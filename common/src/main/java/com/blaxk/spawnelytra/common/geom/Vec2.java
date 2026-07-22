/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.geom;

/** A point on the horizontal (XZ) plane. */
public record Vec2(double x, double z) {

    public Vec2 add(final double dx, final double dz) {
        return new Vec2(this.x + dx, this.z + dz);
    }

    public Vec2 subtract(final Vec2 o) {
        return new Vec2(this.x - o.x, this.z - o.z);
    }

    public double distance(final Vec2 o) {
        return Math.sqrt(this.distanceSquared(o));
    }

    public double distanceSquared(final Vec2 o) {
        final double dx = this.x - o.x;
        final double dz = this.z - o.z;
        return dx * dx + dz * dz;
    }

    /** Center of the block containing the given block coordinates ({@code bx + 0.5, bz + 0.5}). */
    public static Vec2 blockCenter(final int bx, final int bz) {
        return new Vec2(bx + 0.5, bz + 0.5);
    }
}
