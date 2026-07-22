/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.zone;

import com.blaxk.spawnelytra.Main;
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
import com.blaxk.spawnelytra.config.BukkitConfigView;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.SoundUtil;
import com.blaxk.spawnelytra.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Live zone registry + global settings. Both are immutable snapshots swapped atomically (volatile), so every
 * region thread can read them without locking. Config writes go through {@link #mutateConfig} (serialized on the
 * plugin monitor) and the file is written on a background thread.
 */
public final class ZoneService {
    private final Main plugin;
    private volatile ZoneRegistry registry = ZoneRegistry.empty();
    private volatile GlobalSettings globals;
    private final ExecutorService configWriter = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "SpawnElytra-Config-IO");
        t.setDaemon(true);
        return t;
    });

    public final ZoneRegistry.SpawnLookup spawns = world -> {
        final World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        final Location spawn = w.getSpawnLocation();
        return new Vec2(spawn.getX(), spawn.getZ());
    };

    public ZoneService(final Main plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads globals and zones from the plugin config (caller holds the plugin monitor or is in onEnable). */
    public void load() {
        final ConfigView root = new BukkitConfigView(this.plugin.getConfig());
        final GlobalSettings g = GlobalSettings.read(root);
        for (final Msg warning : TierResolver.read(root).warnings()) {
            this.plugin.getLogger().warning(Texts.plain(warning));
        }
        final ZoneCodec.ReadResult result = ZoneCodec.readAll(root, g.hunger());
        for (final Msg warning : result.warnings()) {
            this.plugin.getLogger().warning(Texts.plain(warning));
        }
        for (final Zone zone : result.zones()) {
            if (Bukkit.getWorld(zone.world()) == null) {
                this.plugin.getLogger().warning("Zone '" + zone.name() + "': world '" + zone.world() + "' is not loaded, the zone is inactive until it is.");
            }
        }
        this.globals = g;
        this.registry = new ZoneRegistry(result.zones());
        if (result.zones().isEmpty()) {
            this.plugin.getLogger().warning("No zones configured for Spawn Elytra. Create one with /se zone create <name>.");
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

    /** Winning enabled zone at the location (overlap resolution per spec §1.2). */
    public Zone zoneAt(final Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return this.registry.resolve(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), this.spawns)
                .orElse(null);
    }

    public Vec2 worldSpawn(final String world) {
        return this.spawns.spawn(world);
    }

    public PermissionTier tierOf(final Player player) {
        return TierResolver.resolve(this.globals.tiers(), player::hasPermission).orElse(null);
    }

    public EffectiveSettings effective(final Zone zone, final Player player) {
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

    /** Applies a global settings change (already written into the config by the caller's mutation). */
    public void reloadGlobals() {
        synchronized (this.plugin) {
            this.globals = GlobalSettings.read(new BukkitConfigView(this.plugin.getConfig()));
        }
    }

    public interface ConfigMutation {
        void apply(ConfigView root);
    }

    /** Runs a config mutation under the plugin monitor and schedules an async save of the resulting file. */
    public void mutateConfig(final ConfigMutation mutation) {
        final String content;
        synchronized (this.plugin) {
            mutation.apply(new BukkitConfigView(this.plugin.getConfig()));
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
                this.plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
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
                return world != null && Bukkit.getWorld(world) != null;
            }

            @Override
            public boolean soundExists(final String sound) {
                return ZoneService.soundExists(sound);
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

    /** Bukkit enum names ({@code ENTITY_BAT_TAKEOFF}) and namespaced keys ({@code minecraft:entity.bat.takeoff}). */
    public static boolean soundExists(final String sound) {
        if (sound == null || sound.isBlank()) {
            return false;
        }
        try {
            SoundUtil.byName(sound);
            return true;
        } catch (final IllegalArgumentException unknown) {
            return false;
        }
    }

    public static boolean isOwned(final Player player) {
        return SchedulerUtil.isOwnedByCurrentThread(player);
    }
}
