/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.geom.Bounds2D;
import com.blaxk.spawnelytra.common.geom.Geometry;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple polygon (≥ 3 points, not self-intersecting). Containment: even-odd ray casting on XZ; points on an edge or
 * vertex count as inside.
 */
public record PolygonShape(List<Vec2> points) implements Shape {

    public PolygonShape {
        points = List.copyOf(points);
    }

    @Override
    public ShapeType type() {
        return ShapeType.POLYGON;
    }

    @Override
    public boolean contains(final double x, final double z, final Vec2 worldSpawn) {
        return Geometry.polygonContains(this.points, x, z);
    }

    @Override
    public Bounds2D bounds(final Vec2 worldSpawn) {
        return this.points.isEmpty() ? null : Bounds2D.of(this.points);
    }

    @Override
    public Vec2 center(final Vec2 worldSpawn) {
        final Bounds2D b = this.bounds(worldSpawn);
        return b == null ? null : b.center();
    }

    @Override
    public Shape translate(final double dx, final double dz, final Vec2 worldSpawn) {
        final List<Vec2> moved = new ArrayList<>(this.points.size());
        for (final Vec2 p : this.points) {
            moved.add(p.add(dx, dz));
        }
        return new PolygonShape(moved);
    }

    @Override
    public String validate() {
        final Geometry.PolygonProblem problem = Geometry.validatePolygon(this.points, Shape.MAX_POLYGON_POINTS);
        return problem == null ? null : problem.langKey();
    }

    @Override
    public List<Vec2> outline(final Vec2 worldSpawn, final double maxChord) {
        return this.points;
    }

    /** Index {@code i} of the edge (point i → point i+1, wrapping) closest to the position (first on ties). */
    public int nearestEdge(final double x, final double z) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        final int n = this.points.size();
        for (int i = 0; i < n; i++) {
            final Vec2 a = this.points.get(i);
            final Vec2 b = this.points.get((i + 1) % n);
            final double d = Geometry.distanceToSegment(x, z, a.x(), a.z(), b.x(), b.z());
            if (d < bestDist - Geometry.EPS) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Index of the vertex closest to the position (first one on ties). */
    public int nearestPoint(final double x, final double z) {
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        final Vec2 p = new Vec2(x, z);
        for (int i = 0; i < this.points.size(); i++) {
            final double d = this.points.get(i).distanceSquared(p);
            if (d < bestDist - Geometry.EPS) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Inserts a point into the nearest edge (between its two vertices). */
    public PolygonShape withPointInserted(final Vec2 p) {
        final List<Vec2> list = new ArrayList<>(this.points);
        if (list.size() < 2) {
            list.add(p);
        } else {
            list.add(this.nearestEdge(p.x(), p.z()) + 1, p);
        }
        return new PolygonShape(list);
    }

    /** Removes the vertex nearest to the given position. */
    public PolygonShape withNearestPointRemoved(final double x, final double z) {
        final List<Vec2> list = new ArrayList<>(this.points);
        if (!list.isEmpty()) {
            list.remove(this.nearestPoint(x, z));
        }
        return new PolygonShape(list);
    }

    /**
     * Moves every vertex {@code by} blocks away from (positive) or towards (negative) the vertex centroid.
     * Returns {@code null} if a vertex would come closer than 0.5 blocks to the centroid.
     */
    public PolygonShape grow(final double by) {
        final Vec2 c = Geometry.vertexCentroid(this.points);
        final List<Vec2> out = new ArrayList<>(this.points.size());
        for (final Vec2 p : this.points) {
            final double d = p.distance(c);
            if (d <= Geometry.EPS || d + by < 0.5) {
                return null;
            }
            final double f = (d + by) / d;
            out.add(new Vec2(ConfigNumbers.round(c.x() + (p.x() - c.x()) * f, 2),
                    ConfigNumbers.round(c.z() + (p.z() - c.z()) * f, 2)));
        }
        return new PolygonShape(out);
    }

    @Override
    public Msg dims() {
        final Bounds2D b = this.bounds(null);
        return Msg.of("zone_dims_polygon", "points", String.valueOf(this.points.size()),
                "width", b == null ? "0" : ConfigNumbers.format(b.width()),
                "depth", b == null ? "0" : ConfigNumbers.format(b.depth()));
    }
}
