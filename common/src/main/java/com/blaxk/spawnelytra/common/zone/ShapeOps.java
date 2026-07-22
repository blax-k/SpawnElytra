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

import java.util.ArrayList;
import java.util.List;

/** Shape conversion (editor slot 5), creation templates (zone create) and shape-vs-shape overlap tests. */
public enum ShapeOps {
    ;

    /** Default size used by {@code /se zone create}: circle radius 30, 30×30 rectangle / square polygon. */
    public static final double CREATE_SIZE = 30.0;

    /** Number of vertices when converting a circle into a polygon. */
    public static final int CIRCLE_TO_POLYGON_POINTS = 8;

    /**
     * Initial draft shape for {@code /se zone create}, centered on the given position (pass the player's block
     * center, {@link Vec2#blockCenter(int, int)}): circle r=30, 30×30 rectangle, or a 4-point 30×30 square.
     */
    public static Shape createAround(final ShapeType type, final Vec2 center) {
        final double h = ShapeOps.CREATE_SIZE / 2.0;
        return switch (type) {
            case CIRCLE -> new CircleShape(center, ShapeOps.CREATE_SIZE);
            case RECTANGLE -> new RectangleShape(center.add(-h, -h), center.add(h, h));
            case POLYGON -> new PolygonShape(List.of(center.add(-h, -h), center.add(h, -h), center.add(h, h), center.add(-h, h)));
        };
    }

    /**
     * Converts between shapes using the bounding box (spec §6.2 slot 5):
     * <ul>
     *     <li>→ rectangle: the bounding box;</li>
     *     <li>→ circle: center of the bounding box, radius = half of the larger box side (min 1);</li>
     *     <li>→ polygon: rectangle → its 4 corners; circle → {@value #CIRCLE_TO_POLYGON_POINTS} points on the circle.</li>
     * </ul>
     * Coordinates are rounded to 2 decimals. Returns the input when the type is unchanged, {@code null} if the
     * shape cannot be resolved (world-spawn circle without a known spawn).
     */
    public static Shape convert(final Shape from, final ShapeType to, final Vec2 worldSpawn) {
        if (from.type() == to) {
            return from;
        }
        final Bounds2D b = from.bounds(worldSpawn);
        if (b == null) {
            return null;
        }
        return switch (to) {
            case RECTANGLE -> new RectangleShape(new Vec2(r2(b.minX()), r2(b.minZ())), new Vec2(r2(b.maxX()), r2(b.maxZ())));
            case CIRCLE -> new CircleShape(new Vec2(r2(b.center().x()), r2(b.center().z())),
                    Math.max(CircleShape.MIN_RADIUS, r2(Math.max(b.width(), b.depth()) / 2.0)));
            case POLYGON -> {
                if (from instanceof final CircleShape c) {
                    final Vec2 center = c.resolveCenter(worldSpawn);
                    final List<Vec2> pts = new ArrayList<>(ShapeOps.CIRCLE_TO_POLYGON_POINTS);
                    for (int i = 0; i < ShapeOps.CIRCLE_TO_POLYGON_POINTS; i++) {
                        final double a = 2 * Math.PI * i / ShapeOps.CIRCLE_TO_POLYGON_POINTS;
                        pts.add(new Vec2(r2(center.x() + c.radius() * Math.cos(a)), r2(center.z() + c.radius() * Math.sin(a))));
                    }
                    yield new PolygonShape(pts);
                }
                yield new PolygonShape(List.of(new Vec2(b.minX(), b.minZ()), new Vec2(b.maxX(), b.minZ()),
                        new Vec2(b.maxX(), b.maxZ()), new Vec2(b.minX(), b.maxZ())));
            }
        };
    }

    private static double r2(final double v) {
        return ConfigNumbers.round(v, 2);
    }

    /** Polygon vertices of a rectangle/polygon ({@code null} for circles). */
    private static List<Vec2> vertices(final Shape s, final Vec2 spawn) {
        return switch (s) {
            case final RectangleShape r -> r.outline(spawn, 0);
            case final PolygonShape p -> p.points();
            case final CircleShape c -> null;
        };
    }

    /**
     * {@code true} if the two shapes share at least one point (touching edges count). Height is not considered
     * (see {@link ZoneOverlaps}).
     */
    public static boolean intersects(final Shape a, final Shape b, final Vec2 worldSpawn) {
        final Bounds2D ba = a.bounds(worldSpawn);
        final Bounds2D bb = b.bounds(worldSpawn);
        if (ba == null || bb == null || !ba.intersects(bb)) {
            return false;
        }
        if (a instanceof final CircleShape ca && b instanceof final CircleShape cb) {
            return ca.resolveCenter(worldSpawn).distance(cb.resolveCenter(worldSpawn)) <= ca.radius() + cb.radius() + Geometry.EPS;
        }
        if (a instanceof final CircleShape ca) {
            return ShapeOps.circlePolygon(ca, ShapeOps.vertices(b, worldSpawn), worldSpawn);
        }
        if (b instanceof final CircleShape cb) {
            return ShapeOps.circlePolygon(cb, ShapeOps.vertices(a, worldSpawn), worldSpawn);
        }
        return ShapeOps.polygonPolygon(ShapeOps.vertices(a, worldSpawn), ShapeOps.vertices(b, worldSpawn));
    }

    private static boolean circlePolygon(final CircleShape c, final List<Vec2> poly, final Vec2 spawn) {
        final Vec2 center = c.resolveCenter(spawn);
        if (poly.size() < 3) {
            return false;
        }
        if (Geometry.polygonContains(poly, center.x(), center.z())) {
            return true;
        }
        final int n = poly.size();
        for (int i = 0; i < n; i++) {
            if (Geometry.distanceToSegment(center, poly.get(i), poly.get((i + 1) % n)) <= c.radius() + Geometry.EPS) {
                return true;
            }
        }
        return false;
    }

    private static boolean polygonPolygon(final List<Vec2> a, final List<Vec2> b) {
        if (a.size() < 3 || b.size() < 3) {
            return false;
        }
        final int n = a.size();
        final int m = b.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (Geometry.segmentsIntersect(a.get(i), a.get((i + 1) % n), b.get(j), b.get((j + 1) % m))) {
                    return true;
                }
            }
        }
        return Geometry.polygonContains(b, a.get(0).x(), a.get(0).z()) || Geometry.polygonContains(a, b.get(0).x(), b.get(0).z());
    }
}
