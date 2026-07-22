/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.geom.Bounds2D;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;

import java.util.List;

/**
 * Axis-aligned rectangle given by two opposite corners (any order). Containment is 1.5-identical:
 * {@code minX <= x <= maxX && minZ <= z <= maxZ} on the raw coordinates (inclusive on all sides).
 */
public record RectangleShape(Vec2 corner1, Vec2 corner2) implements Shape {

    /** Returns a copy with corner {@code index} (1 or 2) replaced. */
    public RectangleShape withCorner(final int index, final Vec2 p) {
        return index == 1 ? new RectangleShape(p, this.corner2) : new RectangleShape(this.corner1, p);
    }

    public double minX() {
        return Math.min(this.corner1.x(), this.corner2.x());
    }

    public double maxX() {
        return Math.max(this.corner1.x(), this.corner2.x());
    }

    public double minZ() {
        return Math.min(this.corner1.z(), this.corner2.z());
    }

    public double maxZ() {
        return Math.max(this.corner1.z(), this.corner2.z());
    }

    @Override
    public ShapeType type() {
        return ShapeType.RECTANGLE;
    }

    @Override
    public boolean contains(final double x, final double z, final Vec2 worldSpawn) {
        return x >= this.minX() && x <= this.maxX() && z >= this.minZ() && z <= this.maxZ();
    }

    @Override
    public Bounds2D bounds(final Vec2 worldSpawn) {
        return Bounds2D.of(this.corner1, this.corner2);
    }

    @Override
    public Vec2 center(final Vec2 worldSpawn) {
        return this.bounds(worldSpawn).center();
    }

    @Override
    public Shape translate(final double dx, final double dz, final Vec2 worldSpawn) {
        return new RectangleShape(this.corner1.add(dx, dz), this.corner2.add(dx, dz));
    }

    /** Grows (positive) or shrinks (negative) every side outward by {@code by}; corners keep their roles. */
    public RectangleShape grow(final double by) {
        final double sx1 = this.corner1.x() <= this.corner2.x() ? -by : by;
        final double sz1 = this.corner1.z() <= this.corner2.z() ? -by : by;
        return new RectangleShape(this.corner1.add(sx1, sz1), this.corner2.add(-sx1, -sz1));
    }

    @Override
    public String validate() {
        if (this.maxX() - this.minX() <= 0 || this.maxZ() - this.minZ() <= 0) {
            return "editor_error_rectangle_flat";
        }
        return null;
    }

    @Override
    public List<Vec2> outline(final Vec2 worldSpawn, final double maxChord) {
        return List.of(new Vec2(this.minX(), this.minZ()), new Vec2(this.maxX(), this.minZ()),
                new Vec2(this.maxX(), this.maxZ()), new Vec2(this.minX(), this.maxZ()));
    }

    @Override
    public Msg dims() {
        return Msg.of("zone_dims_rectangle", "width", ConfigNumbers.format(this.maxX() - this.minX()),
                "depth", ConfigNumbers.format(this.maxZ() - this.minZ()));
    }
}
