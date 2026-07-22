/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.visual;

import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.config.ConfigSection;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Scheduler;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /se visualize [zone] [seconds]}: particle outline of one zone or all zones in the
 * player's world, sent only to the viewer (port of the Paper Visualizer).
 */
public final class Visualizer {
    private static final double MAX_DISTANCE = 128.0;

    private final Main plugin;
    private final Map<UUID, Scheduler.TaskHandle> tasks = new ConcurrentHashMap<>();

    public Visualizer(final Main plugin) {
        this.plugin = plugin;
    }

    /** {@code zones} must not be empty. */
    public void start(final ServerPlayer player, final List<Zone> zones, final int seconds) {
        this.stop(player, false);
        final ConfigSection cfg = this.plugin.getConfig().getConfigurationSection("visualization");
        final int verticalRange = cfg != null ? cfg.getInt("vertical_range", 20) : 20;
        final int pillarRange = cfg != null ? cfg.getInt("pillar_vertical_range", 25) : 25;
        final int frequency = Math.max(1, cfg != null ? cfg.getInt("update_frequency", 10) : 10);
        final float size = (float) Math.max(0.1, cfg != null ? cfg.getDouble("particle_size", 2.0) : 2.0);
        final boolean enhanced = cfg == null || cfg.getBoolean("enhanced_particles", true);

        MessageUtil.send(player, "visualize_start", Placeholder.unparsed("seconds", String.valueOf(seconds)));
        final UUID uuid = player.getUUID();
        final int maxTicks = seconds * 20;
        final int[] elapsed = {0};
        final Scheduler.TaskHandle task = this.plugin.getScheduler().runTimer(1L, 1L, () -> {
            final ServerPlayer online = this.plugin.getPlayer(uuid);
            if (elapsed[0] >= maxTicks || online == null) {
                final Scheduler.TaskHandle handle = this.tasks.remove(uuid);
                if (handle != null) {
                    handle.cancel();
                }
                if (online != null) {
                    MessageUtil.send(online, "visualize_end");
                }
                return;
            }
            if (elapsed[0] % frequency == 0) {
                for (final Zone zone : zones) {
                    this.draw(online, zone, verticalRange, pillarRange, size, enhanced);
                }
            }
            elapsed[0]++;
        });
        this.tasks.put(uuid, task);
    }

    public void stop(final ServerPlayer player, final boolean message) {
        final Scheduler.TaskHandle task = this.tasks.remove(player.getUUID());
        if (task != null) {
            task.cancel();
            if (message) {
                MessageUtil.send(player, "visualize_stop");
            }
        }
    }

    public void onQuit(final ServerPlayer player) {
        this.stop(player, false);
    }

    private static void particle(final ServerPlayer player, final ParticleOptions options, final double x, final double y, final double z,
                                 final int count, final double dx, final double dy, final double dz, final double speed) {
        Compat.particle(player, options, x, y, z, count, dx, dy, dz, speed);
    }

    private void draw(final ServerPlayer player, final Zone zone, final int verticalRange, final int pillarRange,
                      final float size, final boolean enhanced) {
        final ZoneService zones = this.plugin.getZoneService();
        if (!zones.worldKey(zone.world()).equals(ZoneService.worldOf(Compat.level(player)))) {
            return;
        }
        final Vec2 spawn = zones.worldSpawn(zone.world());
        final List<Vec2> outline = zone.shape().outline(spawn, 2.0);
        if (outline.size() < 2) {
            return;
        }
        final double eyeX = player.getX();
        final double eyeZ = player.getZ();
        final double playerY = player.getY();
        final ParticleOptions gold = Compat.dust(0xFFD700, size);
        final ParticleOptions bright = enhanced ? Compat.dust(0xFFFF64, size * 0.9f) : gold;

        // Height bounds clamp the drawn band (a zone with limits is only shown inside them).
        final double minY = zone.minHeight() != null ? Math.max(zone.minHeight(), playerY - verticalRange) : playerY - verticalRange;
        final double maxY = zone.maxHeight() != null ? Math.min(zone.maxHeight() + 1, playerY + verticalRange) : playerY + verticalRange;

        final int n = outline.size();
        for (double y = minY; y <= maxY; y += 3) {
            final ParticleOptions dust = Math.abs(y - playerY) <= 6 ? bright : gold;
            for (int i = 0; i < n; i++) {
                final Vec2 a = outline.get(i);
                final Vec2 b = outline.get((i + 1) % n);
                final double len = a.distance(b);
                final int steps = Math.max(1, (int) Math.ceil(len / 1.5));
                for (int k = 0; k < steps; k++) {
                    final double x = a.x() + (b.x() - a.x()) * k / steps;
                    final double z = a.z() + (b.z() - a.z()) * k / steps;
                    if (Math.abs(x - eyeX) > MAX_DISTANCE || Math.abs(z - eyeZ) > MAX_DISTANCE) {
                        continue;
                    }
                    particle(player, dust, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }

        // Vertex pillars (rectangle/polygon corners, 4 cardinal points of a circle).
        final int step = zone.shape() instanceof CircleShape ? Math.max(1, n / 4) : 1;
        for (int i = 0; i < n; i += step) {
            final Vec2 v = outline.get(i);
            if (Math.abs(v.x() - eyeX) > MAX_DISTANCE || Math.abs(v.z() - eyeZ) > MAX_DISTANCE) {
                continue;
            }
            for (double y = playerY - pillarRange; y <= playerY + pillarRange; y += 0.5) {
                particle(player, ParticleTypes.END_ROD, v.x(), y, v.z(), 1, 0, 0, 0, 0);
            }
            if (enhanced) {
                particle(player, ParticleTypes.FIREWORK, v.x(), playerY, v.z(), 2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        final Vec2 c = zone.shape().center(spawn);
        if (c != null && Math.abs(c.x() - eyeX) <= MAX_DISTANCE && Math.abs(c.z() - eyeZ) <= MAX_DISTANCE) {
            particle(player, ParticleTypes.END_ROD, c.x(), playerY, c.z(), 5, 0.2, 0.2, 0.2, 0);
            final int centerRange = Math.max(10, verticalRange * 3 / 4);
            for (double y = playerY - centerRange; y <= playerY + centerRange; y += 1.0) {
                particle(player, enhanced ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, c.x(), y, c.z(), 1, 0, 0, 0, 0);
            }
        }
    }
}
