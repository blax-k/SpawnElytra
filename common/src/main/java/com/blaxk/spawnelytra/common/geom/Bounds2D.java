/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.geom;

import java.util.Collection;

/** Axis-aligned bounding box on the XZ plane (inclusive). */
public record Bounds2D(double minX, double minZ, double maxX, double maxZ) {

    public static Bounds2D of(final Vec2 a, final Vec2 b) {
        return new Bounds2D(Math.min(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), Math.max(a.z(), b.z()));
    }

    public static Bounds2D of(final Collection<Vec2> points) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (final Vec2 p : points) {
            minX = Math.min(minX, p.x());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxZ = Math.max(maxZ, p.z());
        }
        return new Bounds2D(minX, minZ, maxX, maxZ);
    }

    public double width() {
        return this.maxX - this.minX;
    }

    public double depth() {
        return this.maxZ - this.minZ;
    }

    public Vec2 center() {
        return new Vec2((this.minX + this.maxX) / 2.0, (this.minZ + this.maxZ) / 2.0);
    }

    public boolean contains(final double x, final double z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public boolean intersects(final Bounds2D o) {
        return this.minX <= o.maxX && this.maxX >= o.minX && this.minZ <= o.maxZ && this.maxZ >= o.minZ;
    }

    public Bounds2D expand(final double by) {
        return new Bounds2D(this.minX - by, this.minZ - by, this.maxX + by, this.maxZ + by);
    }
}
