/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.visual;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /se visualize [zone] [seconds]}: particle outline of one zone or all zones in the player's world.
 * Particles are sent only to the viewer, on the viewer's own (entity) scheduler.
 */
public final class Visualizer implements Listener {
    private static final double MAX_DISTANCE = 128.0;

    private final Main plugin;
    private final Map<UUID, SchedulerUtil.TaskHandle> tasks = new ConcurrentHashMap<>();

    public Visualizer(final Main plugin) {
        this.plugin = plugin;
    }

    /** Must run on the player's thread. {@code zones} must not be empty. */
    public void start(final Player player, final List<Zone> zones, final int seconds) {
        this.stop(player, false);
        final ConfigurationSection cfg = this.plugin.getConfig().getConfigurationSection("visualization");
        final int verticalRange = cfg != null ? cfg.getInt("vertical_range", 20) : 20;
        final int pillarRange = cfg != null ? cfg.getInt("pillar_vertical_range", 25) : 25;
        final int frequency = Math.max(1, cfg != null ? cfg.getInt("update_frequency", 10) : 10);
        final float size = (float) Math.max(0.1, cfg != null ? cfg.getDouble("particle_size", 2.0) : 2.0);
        final boolean enhanced = cfg == null || cfg.getBoolean("enhanced_particles", true);

        MessageUtil.send(player, "visualize_start", Placeholder.unparsed("seconds", String.valueOf(seconds)));
        final int maxTicks = seconds * 20;
        final int[] elapsed = {0};
        final SchedulerUtil.TaskHandle task = SchedulerUtil.runAtEntityTimer(this.plugin, player, 1L, 1L, () -> {
            if (elapsed[0] >= maxTicks || !player.isOnline()) {
                final SchedulerUtil.TaskHandle handle = this.tasks.remove(player.getUniqueId());
                if (handle != null) {
                    handle.cancel();
                }
                if (player.isOnline()) {
                    MessageUtil.send(player, "visualize_end");
                }
                return;
            }
            if (elapsed[0] % frequency == 0) {
                for (final Zone zone : zones) {
                    this.draw(player, zone, verticalRange, pillarRange, size, enhanced);
                }
            }
            elapsed[0]++;
        });
        this.tasks.put(player.getUniqueId(), task);
    }

    public void stop(final Player player, final boolean message) {
        final SchedulerUtil.TaskHandle task = this.tasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (message) {
                MessageUtil.send(player, "visualize_stop");
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.stop(event.getPlayer(), false);
    }

    private void draw(final Player player, final Zone zone, final int verticalRange, final int pillarRange,
                      final float size, final boolean enhanced) {
        final World world = player.getWorld();
        if (!world.getName().equals(zone.world())) {
            return;
        }
        final Vec2 spawn = this.plugin.getZoneService().worldSpawn(zone.world());
        final List<Vec2> outline = zone.shape().outline(spawn, 2.0);
        if (outline.size() < 2) {
            return;
        }
        final Location eye = player.getLocation();
        final double playerY = eye.getY();
        final Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), size);
        final Particle.DustOptions bright = enhanced ? new Particle.DustOptions(Color.fromRGB(255, 255, 100), size * 0.9f) : gold;

        // Height bounds clamp the drawn band (a zone with limits is only shown inside them).
        final double minY = zone.minHeight() != null ? Math.max(zone.minHeight(), playerY - verticalRange) : playerY - verticalRange;
        final double maxY = zone.maxHeight() != null ? Math.min(zone.maxHeight(), playerY + verticalRange) : playerY + verticalRange;

        final int n = outline.size();
        for (double y = minY; y <= maxY; y += 3) {
            final Particle.DustOptions dust = Math.abs(y - playerY) <= 6 ? bright : gold;
            for (int i = 0; i < n; i++) {
                final Vec2 a = outline.get(i);
                final Vec2 b = outline.get((i + 1) % n);
                final double len = a.distance(b);
                final int steps = Math.max(1, (int) Math.ceil(len / 1.5));
                for (int k = 0; k < steps; k++) {
                    final double x = a.x() + (b.x() - a.x()) * k / steps;
                    final double z = a.z() + (b.z() - a.z()) * k / steps;
                    if (Math.abs(x - eye.getX()) > MAX_DISTANCE || Math.abs(z - eye.getZ()) > MAX_DISTANCE) {
                        continue;
                    }
                    player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, 0, dust);
                }
            }
        }

        // Vertex pillars (rectangle/polygon corners, 4 cardinal points of a circle).
        final int step = zone.shape() instanceof com.blaxk.spawnelytra.common.zone.CircleShape ? Math.max(1, n / 4) : 1;
        for (int i = 0; i < n; i += step) {
            final Vec2 v = outline.get(i);
            if (Math.abs(v.x() - eye.getX()) > MAX_DISTANCE || Math.abs(v.z() - eye.getZ()) > MAX_DISTANCE) {
                continue;
            }
            for (double y = playerY - pillarRange; y <= playerY + pillarRange; y += 0.5) {
                player.spawnParticle(Particle.END_ROD, new Location(world, v.x(), y, v.z()), 1, 0, 0, 0, 0);
            }
            if (enhanced) {
                player.spawnParticle(Particle.FIREWORK, new Location(world, v.x(), playerY, v.z()), 2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        final Vec2 c = zone.shape().center(spawn);
        if (c != null && Math.abs(c.x() - eye.getX()) <= MAX_DISTANCE && Math.abs(c.z() - eye.getZ()) <= MAX_DISTANCE) {
            final Location center = new Location(world, c.x(), playerY, c.z());
            player.spawnParticle(Particle.END_ROD, center, 5, 0.2, 0.2, 0.2, 0);
            final int centerRange = Math.max(10, verticalRange * 3 / 4);
            for (double y = playerY - centerRange; y <= playerY + centerRange; y += 1.0) {
                player.spawnParticle(enhanced ? Particle.SOUL_FIRE_FLAME : Particle.FLAME,
                        new Location(world, c.x(), y, c.z()), 1, 0, 0, 0, 0);
            }
        }
    }
}
