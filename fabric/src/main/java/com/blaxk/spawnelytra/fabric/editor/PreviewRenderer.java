/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

import com.blaxk.spawnelytra.common.editor.Preview;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Glowing zone outline for exactly one player (the Fabric counterpart of the Paper renderer:
 * same blocks, glow colours, thickness and entity cap). Fabric uses packet-only display
 * entities ({@link FakeEntities}): nothing is spawned on the server, so the preview is never
 * saved with the world and never visible to other players.
 */
public final class PreviewRenderer {
    private static BlockState block(final Preview.Style style) {
        //? if >=26.3 {
        /*return switch (style) {
            case DRAFT -> Blocks.CONCRETE.lightBlue().defaultBlockState();
            case DRAFT_OVERLAP, OTHER_OVERLAP -> Blocks.CONCRETE.red().defaultBlockState();
            case OTHER -> Blocks.CONCRETE.gray().defaultBlockState();
            case HEIGHT_TOP -> Blocks.CONCRETE.yellow().defaultBlockState();
            case HEIGHT_BOTTOM -> Blocks.CONCRETE.orange().defaultBlockState();
        };
        *///?} else {
        return switch (style) {
            case DRAFT -> Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
            case DRAFT_OVERLAP, OTHER_OVERLAP -> Blocks.RED_CONCRETE.defaultBlockState();
            case OTHER -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case HEIGHT_TOP -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case HEIGHT_BOTTOM -> Blocks.ORANGE_CONCRETE.defaultBlockState();
        };
        //?}
    }

    private static int glow(final Preview.Style style) {
        return switch (style) {
            case DRAFT -> 0x5db3ff;
            case DRAFT_OVERLAP -> 0xfd5e5e;
            case OTHER_OVERLAP -> 0xb04040;
            case OTHER -> 0x6f6f6f;
            case HEIGHT_TOP -> 0xffd166;
            case HEIGHT_BOTTOM -> 0xfdba5e;
        };
    }

    private static final float THICKNESS = 0.09f;
    private static final float POINT_SIZE = 0.3f;
    private static final int MAX_ENTITIES = 320;

    private final ServerPlayer viewer;
    private final Map<Preview.Segment, Integer> segments = new HashMap<>();
    private int label = -1;
    private Vec3 labelPos;
    private Component labelText;

    public PreviewRenderer(final ServerPlayer viewer) {
        this.viewer = viewer;
    }

    /**
     * Brings the displayed outline in line with {@code wanted} (segments are value objects):
     * unchanged segments are kept, the rest is removed / added.
     */
    public void update(final ServerPlayer viewer, final List<Preview.Segment> wanted, final double range, final Vec3 labelAt, final Component labelComponent) {
        final Set<Preview.Segment> want = new HashSet<>();
        for (final Preview.Segment segment : wanted) {
            if (want.size() >= MAX_ENTITIES) {
                break;
            }
            want.add(segment);
        }

        final Set<Preview.Segment> stale = new HashSet<>(this.segments.keySet());
        stale.removeAll(want);
        final List<Integer> remove = new ArrayList<>();
        for (final Preview.Segment key : stale) {
            remove.add(this.segments.remove(key));
        }
        FakeEntities.remove(viewer, remove);

        for (final Preview.Segment segment : want) {
            if (!this.segments.containsKey(segment)) {
                this.segments.put(segment, spawnSegment(viewer, segment));
            }
        }

        this.updateLabel(viewer, labelAt, labelComponent, range);
    }

    private void updateLabel(final ServerPlayer viewer, final Vec3 at, final Component text, final double range) {
        if (at == null || text == null || Math.hypot(at.x - viewer.getX(), at.z - viewer.getZ()) > range) {
            this.removeLabel(viewer);
            return;
        }
        if (this.label >= 0 && this.labelPos != null && this.labelPos.distanceToSqr(at) < 0.01) {
            if (!text.equals(this.labelText)) {
                FakeEntities.updateText(viewer, this.label, text);
                this.labelText = text;
            }
            return;
        }
        this.removeLabel(viewer);
        this.label = FakeEntities.text(viewer, at.x, at.y, at.z, text);
        this.labelPos = at;
        this.labelText = text;
    }

    private void removeLabel(final ServerPlayer viewer) {
        if (this.label >= 0) {
            FakeEntities.remove(viewer, List.of(this.label));
        }
        this.label = -1;
        this.labelPos = null;
        this.labelText = null;
    }

    private static int spawnSegment(final ServerPlayer viewer, final Preview.Segment piece) {
        final double dx = piece.to().x() - piece.from().x();
        final double dz = piece.to().z() - piece.from().z();
        if (Math.sqrt(dx * dx + dz * dz) < 1.0E-3) {
            return FakeEntities.cube(viewer, piece.from().x(), piece.y() + POINT_SIZE / 2, piece.from().z(), POINT_SIZE,
                    block(piece.style()), glow(piece.style()));
        }
        return FakeEntities.horizontalBar(viewer, piece.from().x(), piece.y(), piece.from().z(), dx, dz, THICKNESS,
                block(piece.style()), glow(piece.style()));
    }

    /** Removes every entity of this preview from the viewer's client. */
    public void clear() {
        FakeEntities.remove(this.viewer, new ArrayList<>(this.segments.values()));
        this.segments.clear();
        this.removeLabel(this.viewer);
    }

    /** Forgets the entities without packets (the client dropped them already, e.g. after a dimension change). */
    public void forget() {
        this.segments.clear();
        this.label = -1;
        this.labelPos = null;
        this.labelText = null;
    }

    public int entityCount() {
        return this.segments.size() + (this.label >= 0 ? 1 : 0);
    }
}
