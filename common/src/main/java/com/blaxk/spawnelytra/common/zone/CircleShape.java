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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Circle on the XZ plane. {@code center == null} means {@code world_spawn} (follows the live world spawn, the old
 * 1.5 "auto" mode). Containment: {@code sqrt(dx*dx + dz*dz) <= radius} (inclusive, like 1.5's
 * {@code distance <= radius}, but horizontal only; see decisions log).
 */
public record CircleShape(Vec2 center, double radius) implements Shape {

    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 100_000.0;

    public static CircleShape worldSpawn(final double radius) {
        return new CircleShape(null, radius);
    }

    public boolean followsWorldSpawn() {
        return this.center == null;
    }

    /** The effective center: fixed center or the given world spawn. */
    public Vec2 resolveCenter(final Vec2 worldSpawn) {
        return this.center != null ? this.center : worldSpawn;
    }

    public CircleShape withRadius(final double r) {
        return new CircleShape(this.center, r);
    }

    public CircleShape withCenter(final Vec2 c) {
        return new CircleShape(c, this.radius);
    }

    @Override
    public ShapeType type() {
        return ShapeType.CIRCLE;
    }

    @Override
    public boolean contains(final double x, final double z, final Vec2 worldSpawn) {
        final Vec2 c = this.resolveCenter(worldSpawn);
        if (c == null) {
            return false;
        }
        final double dx = x - c.x();
        final double dz = z - c.z();
        return Math.sqrt(dx * dx + dz * dz) <= this.radius;
    }

    @Override
    public Bounds2D bounds(final Vec2 worldSpawn) {
        final Vec2 c = this.resolveCenter(worldSpawn);
        return c == null ? null : new Bounds2D(c.x() - this.radius, c.z() - this.radius, c.x() + this.radius, c.z() + this.radius);
    }

    @Override
    public Vec2 center(final Vec2 worldSpawn) {
        return this.resolveCenter(worldSpawn);
    }

    @Override
    public Shape translate(final double dx, final double dz, final Vec2 worldSpawn) {
        final Vec2 c = this.resolveCenter(worldSpawn);
        if (c == null) {
            return this;
        }
        return new CircleShape(c.add(dx, dz), this.radius);
    }

    @Override
    public String validate() {
        if (Double.isNaN(this.radius) || this.radius <= 0) {
            return "editor_error_radius";
        }
        if (this.radius > CircleShape.MAX_RADIUS) {
            return "editor_error_radius_too_large";
        }
        return null;
    }

    /** Number of vertices used to approximate the circle with chords of at most {@code maxChord} (24..4096). */
    public int segmentCount(final double maxChord) {
        final double chord = maxChord <= 0 ? 2 : maxChord;
        final int n = (int) Math.ceil(2 * Math.PI * this.radius / chord);
        return Math.max(24, Math.min(n, 4096));
    }

    @Override
    public List<Vec2> outline(final Vec2 worldSpawn, final double maxChord) {
        final Vec2 c = this.resolveCenter(worldSpawn);
        if (c == null) {
            return Collections.emptyList();
        }
        final int n = this.segmentCount(maxChord);
        final List<Vec2> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final double a = 2 * Math.PI * i / n;
            pts.add(new Vec2(c.x() + this.radius * Math.cos(a), c.z() + this.radius * Math.sin(a)));
        }
        return pts;
    }

    @Override
    public Msg dims() {
        return Msg.of("zone_dims_circle", "radius", ConfigNumbers.format(this.radius));
    }
}
