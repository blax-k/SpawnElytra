/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.editor;

import com.blaxk.spawnelytra.common.editor.Preview;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Glowing zone outline made of thin block displays plus one text-display label, visible only to one player.
 * <p>
 * Threading: every method must run on the viewer's thread (main thread on Paper, the viewer's region on Folia).
 * Entities are only spawned at locations owned by that thread (anything else is in another region and far away,
 * so it is simply skipped) and removed on the thread owning them. All entities are non-persistent and carry a PDC
 * marker so strays (e.g. after a crash) are removed when their chunk loads.
 */
public final class PreviewRenderer {
    private static Material block(final Preview.Style style) {
        return switch (style) {
            case DRAFT -> Material.LIGHT_BLUE_CONCRETE;
            case DRAFT_OVERLAP, OTHER_OVERLAP -> Material.RED_CONCRETE;
            case OTHER -> Material.GRAY_CONCRETE;
            case HEIGHT_TOP -> Material.YELLOW_CONCRETE;
            case HEIGHT_BOTTOM -> Material.ORANGE_CONCRETE;
        };
    }

    private static Color glow(final Preview.Style style) {
        return switch (style) {
            case DRAFT -> Color.fromRGB(0x5db3ff);
            case DRAFT_OVERLAP -> Color.fromRGB(0xfd5e5e);
            case OTHER_OVERLAP -> Color.fromRGB(0xb04040);
            case OTHER -> Color.fromRGB(0x6f6f6f);
            case HEIGHT_TOP -> Color.fromRGB(0xffd166);
            case HEIGHT_BOTTOM -> Color.fromRGB(0xfdba5e);
        };
    }

    private static final float THICKNESS = 0.09f;
    private static final int MAX_ENTITIES = 320;

    private final Plugin plugin;
    private final Player viewer;
    private final NamespacedKey markerKey;
    private final Map<Preview.Segment, Entity> segments = new HashMap<>();
    private TextDisplay label;
    private String labelText;

    public PreviewRenderer(final Plugin plugin, final Player viewer, final NamespacedKey markerKey) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.markerKey = markerKey;
    }

    /**
     * Brings the displayed outline in line with {@code wanted} (segments are value objects): unchanged segments are
     * kept, the rest is despawned / spawned.
     */
    public void update(final List<Preview.Segment> wanted, final double range, final Location labelAt, final Component labelComponent) {
        final World world = this.viewer.getWorld();
        final Set<Preview.Segment> want = new HashSet<>();
        for (final Preview.Segment segment : wanted) {
            if (want.size() >= MAX_ENTITIES) {
                break;
            }
            want.add(segment);
        }

        final Set<Preview.Segment> stale = new HashSet<>(this.segments.keySet());
        stale.removeAll(want);
        for (final Preview.Segment key : stale) {
            this.despawn(this.segments.remove(key));
        }

        for (final Preview.Segment segment : want) {
            final Entity existing = this.segments.get(segment);
            if (existing != null && existing.isValid()) {
                continue;
            }
            final Entity spawned = this.spawnSegment(world, segment);
            if (spawned != null) {
                this.segments.put(segment, spawned);
            } else {
                this.segments.remove(segment);
            }
        }

        this.updateLabel(world, labelAt, labelComponent, range);
    }

    private void updateLabel(final World world, final Location at, final Component text, final double range) {
        if (at == null || text == null || at.getWorld() != world
                || horizontalDistance(at, this.viewer.getLocation()) > range) {
            despawn(this.label);
            this.label = null;
            this.labelText = null;
            return;
        }
        final String plain = LegacyComponentSerializer.legacySection().serialize(text);
        if (this.label != null && this.label.isValid()) {
            final Location current = this.label.getLocation();
            if (current.distanceSquared(at) < 0.01 && plain.equals(this.labelText)) {
                return;
            }
            if (current.distanceSquared(at) < 0.01) {
                setText(this.label, text, plain);
                this.labelText = plain;
                return;
            }
        }
        despawn(this.label);
        this.label = null;
        this.labelText = null;
        if (!this.canSpawnAt(at)) {
            return;
        }
        this.label = world.spawn(at, TextDisplay.class, display -> {
            this.prepare(display);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setViewRange(4.0f);
            setText(display, text, plain);
        });
        this.viewer.showEntity(this.plugin, this.label);
        this.labelText = plain;
    }

    private static void setText(final TextDisplay display, final Component text, final String legacy) {
        try {
            display.text(text);
        } catch (final Throwable noAdventure) {
            display.setText(legacy);
        }
    }

    private boolean canSpawnAt(final Location location) {
        final World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        return SchedulerUtil.isOwnedByCurrentThread(location);
    }

    private Entity spawnSegment(final World world, final Preview.Segment piece) {
        final double dx = piece.to().x() - piece.from().x();
        final double dz = piece.to().z() - piece.from().z();
        final double length = Math.sqrt(dx * dx + dz * dz);
        final Location origin = new Location(world, piece.from().x(), piece.y(), piece.from().z());
        if (!this.canSpawnAt(origin)) {
            return null;
        }
        final boolean point = length < 1.0E-3;
        // Block model spans [0,1]^3: scale it to (length x t x t), rotate around Y onto the segment direction and
        // shift by half the thickness so the line is centred on the segment.
        final float angle = (float) Math.atan2(-dz, dx);
        final float len = point ? 0.3f : (float) length;
        final float thick = point ? 0.3f : THICKNESS;
        final float cos = (float) Math.cos(angle);
        final float sin = (float) Math.sin(angle);
        // rotateY(angle) applied to (0, -t/2, -t/2): x' = z*sin, z' = z*cos
        final float half = -thick / 2f;
        final Vector3f translation = point
                ? new Vector3f(-0.15f, -0.15f, -0.15f)
                : new Vector3f(half * sin, half, half * cos);
        final Transformation transformation = new Transformation(
                translation,
                new AxisAngle4f(point ? 0f : angle, 0f, 1f, 0f),
                new Vector3f(len, thick, thick),
                new AxisAngle4f(0f, 0f, 0f, 1f));
        final BlockDisplay display = world.spawn(origin, BlockDisplay.class, entity -> {
            this.prepare(entity);
            entity.setBlock(block(piece.style()).createBlockData());
            entity.setTransformation(transformation);
            entity.setGlowing(true);
            entity.setGlowColorOverride(glow(piece.style()));
            entity.setViewRange(4.0f);
            entity.setBrightness(new Display.Brightness(15, 15));
        });
        this.viewer.showEntity(this.plugin, display);
        return display;
    }

    private void prepare(final Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setSilent(true);
        display.getPersistentDataContainer().set(this.markerKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void despawn(final Entity entity) {
        if (entity == null) {
            return;
        }
        SchedulerUtil.runForAnyEntity(this.plugin, entity, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        });
    }

    /** Removes every entity of this preview. Safe from any thread (removal hops to each entity's owner). */
    public void clear() {
        for (final Entity entity : new ArrayList<>(this.segments.values())) {
            despawn(entity);
        }
        this.segments.clear();
        despawn(this.label);
        this.label = null;
        this.labelText = null;
    }

    public int entityCount() {
        return this.segments.size() + (this.label != null ? 1 : 0);
    }

    private static double horizontalDistance(final Location a, final Location b) {
        final double dx = a.getX() - b.getX();
        final double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
