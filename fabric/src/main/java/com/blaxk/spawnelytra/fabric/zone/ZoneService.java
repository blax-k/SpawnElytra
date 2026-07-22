/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.zone;

import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.keys.KeyContext;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.tier.EffectiveSettings;
import com.blaxk.spawnelytra.common.tier.PermissionTier;
import com.blaxk.spawnelytra.common.tier.TierResolver;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import com.blaxk.spawnelytra.common.zone.ZoneRegistry;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.config.SectionView;
import com.blaxk.spawnelytra.fabric.integration.Perms;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.SoundResolver;
import com.blaxk.spawnelytra.fabric.util.WorldNames;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Live zone registry + global settings (the Fabric counterpart of the Paper ZoneService).
 * Both are immutable snapshots swapped atomically. Fabric has no world names, so zone worlds
 * are canonicalized to dimension ids (both {@code world_nether} and {@code minecraft:the_nether}
 * address the same zones, as in 1.5). Config writes are serialized and the file is written on a
 * background thread.
 */
public final class ZoneService {
    private final Main plugin;
    private volatile ZoneRegistry registry = ZoneRegistry.empty();
    private volatile GlobalSettings globals;
    private final Map<String, String> keyCache = new ConcurrentHashMap<>();
    private final ExecutorService configWriter = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "SpawnElytra-Config-IO");
        t.setDaemon(true);
        return t;
    });

    public final ZoneRegistry.SpawnLookup spawns = world -> {
        final ServerLevel level = this.level(world);
        if (level == null) {
            return null;
        }
        final BlockPos spawn = Compat.spawnPos(level);
        return new Vec2(spawn.getX(), spawn.getZ());
    };

    public ZoneService(final Main plugin) {
        this.plugin = plugin;
    }

    /** Canonical world key: the dimension id when the world resolves, else the lower-case name. */
    public String worldKey(final String world) {
        if (world == null) {
            return "";
        }
        return this.keyCache.computeIfAbsent(world, w -> {
            final ServerLevel level = WorldNames.resolve(this.plugin.getServer(), w);
            return level == null ? w.toLowerCase(Locale.ROOT) : Compat.keyId(level.dimension()).toString();
        });
    }

    /** The world key of a level (its dimension id). */
    public static String worldOf(final ServerLevel level) {
        return Compat.keyId(level.dimension()).toString();
    }

    public ServerLevel level(final String world) {
        return WorldNames.resolve(this.plugin.getServer(), world);
    }

    public void load() {
        this.keyCache.clear();
        final ConfigView root = new SectionView(this.plugin.getConfig());
        final GlobalSettings g = GlobalSettings.read(root);
        for (final Msg warning : TierResolver.read(root).warnings()) {
            this.plugin.getLogger().warn(MessageUtil.plain(warning));
        }
        final ZoneCodec.ReadResult result = ZoneCodec.readAll(root, g.hunger());
        for (final Msg warning : result.warnings()) {
            this.plugin.getLogger().warn(MessageUtil.plain(warning));
        }
        for (final Zone zone : result.zones()) {
            if (this.level(zone.world()) == null) {
                this.plugin.getLogger().warn("Zone '{}': world '{}' is not loaded, the zone is inactive until it is. Available worlds: {}",
                        zone.name(), zone.world(), WorldNames.available(this.plugin.getServer()));
            }
        }
        this.globals = g;
        this.registry = new ZoneRegistry(result.zones(), this::worldKey);
        if (result.zones().isEmpty()) {
            this.plugin.getLogger().warn("No zones configured for Spawn Elytra. Create one with /se zone create <name>.");
        }
    }

    public ZoneRegistry registry() {
        return this.registry;
    }

    public GlobalSettings globals() {
        return this.globals;
    }

    public Optional<Zone> zone(final String name) {
        return this.registry.get(name);
    }

    /** Winning enabled zone at the player's position (overlap resolution per spec 1.2). */
    public Zone zoneAt(final ServerPlayer player) {
        return this.registry.resolve(worldOf(Compat.level(player)), player.getX(), player.getY(), player.getZ(), this.spawns)
                .orElse(null);
    }

    public Vec2 worldSpawn(final String world) {
        return this.spawns.spawn(world);
    }

    public PermissionTier tierOf(final ServerPlayer player) {
        return TierResolver.resolve(this.globals.tiers(), node -> Perms.has(player, node)).orElse(null);
    }

    public EffectiveSettings effective(final Zone zone, final ServerPlayer player) {
        return EffectiveSettings.resolve(zone, this.globals, this.tierOf(player));
    }

    /** Writes a zone into the config (replacing {@code previousName}), applies it live and saves asynchronously. */
    public void saveZone(final Zone zone, final String previousName) {
        this.mutateConfig(root -> ZoneCodec.write(root, zone, previousName, true));
        ZoneRegistry next = this.registry;
        if (previousName != null) {
            next = next.without(previousName);
        }
        this.registry = next.with(zone);
    }

    public boolean deleteZone(final String name) {
        final boolean[] removed = {false};
        this.mutateConfig(root -> removed[0] = ZoneCodec.remove(root, name));
        this.registry = this.registry.without(name);
        return removed[0];
    }

    public void reloadGlobals() {
        this.globals = GlobalSettings.read(new SectionView(this.plugin.getConfig()));
    }

    public interface ConfigMutation {
        void apply(ConfigView root);
    }

    /** Runs a config mutation and schedules an async save of the resulting file. */
    public void mutateConfig(final ConfigMutation mutation) {
        final String content;
        synchronized (this.plugin) {
            mutation.apply(new SectionView(this.plugin.getConfig()));
            content = this.plugin.getConfig().saveToString();
        }
        final File file = new File(this.plugin.getDataFolder(), "config.yml");
        final Runnable write = () -> {
            try {
                final File tmp = new File(file.getPath() + ".tmp");
                Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (final IOException e) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException e) {
                this.plugin.getLogger().error("Could not save config.yml: {}", e.getMessage());
            }
        };
        try {
            this.configWriter.execute(write);
        } catch (final java.util.concurrent.RejectedExecutionException e) {
            write.run();
        }
    }

    public void shutdown() {
        this.configWriter.shutdown();
        try {
            this.configWriter.awaitTermination(10, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Validation context for key tables and save checks. */
    public KeyContext keyContext() {
        return new KeyContext() {
            @Override
            public boolean worldExists(final String world) {
                return world != null && ZoneService.this.level(world) != null;
            }

            @Override
            public boolean soundExists(final String sound) {
                return sound != null && !sound.isBlank() && SoundResolver.resolve(sound) != null;
            }

            @Override
            public boolean zoneNameTaken(final String name) {
                return ZoneService.this.registry.get(name).isPresent();
            }

            @Override
            public Vec2 worldSpawn(final String world) {
                return ZoneService.this.worldSpawn(world);
            }

            @Override
            public HungerSettings globalHunger() {
                return ZoneService.this.globals.hunger();
            }
        };
    }
}
