/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.geom;

import java.util.List;

/** Low-level 2D geometry on the XZ plane. All methods are pure and allocation-light. */
public enum Geometry {
    ;

    /** Tolerance used for "on the edge" and collinearity tests. */
    public static final double EPS = 1.0e-9;

    /** Distance from point p to the segment a-b. */
    public static double distanceToSegment(final double px, final double pz,
                                           final double ax, final double az, final double bx, final double bz) {
        final double dx = bx - ax;
        final double dz = bz - az;
        final double len2 = dx * dx + dz * dz;
        double t = len2 <= 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        final double cx = ax + t * dx;
        final double cz = az + t * dz;
        final double ex = px - cx;
        final double ez = pz - cz;
        return Math.sqrt(ex * ex + ez * ez);
    }

    public static double distanceToSegment(final Vec2 p, final Vec2 a, final Vec2 b) {
        return Geometry.distanceToSegment(p.x(), p.z(), a.x(), a.z(), b.x(), b.z());
    }

    /** {@code true} if the point lies on the polygon boundary (within {@link #EPS}). */
    public static boolean onPolygonEdge(final List<Vec2> polygon, final double x, final double z) {
        final int n = polygon.size();
        for (int i = 0; i < n; i++) {
            final Vec2 a = polygon.get(i);
            final Vec2 b = polygon.get((i + 1) % n);
            if (Geometry.distanceToSegment(x, z, a.x(), a.z(), b.x(), b.z()) <= Geometry.EPS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Even-odd ray casting containment; points exactly on an edge or vertex count as inside.
     * Polygons with fewer than 3 points contain nothing.
     */
    public static boolean polygonContains(final List<Vec2> polygon, final double x, final double z) {
        final int n = polygon.size();
        if (n < 3) {
            return false;
        }
        if (Geometry.onPolygonEdge(polygon, x, z)) {
            return true;
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            final double xi = polygon.get(i).x(), zi = polygon.get(i).z();
            final double xj = polygon.get(j).x(), zj = polygon.get(j).z();
            if ((zi > z) != (zj > z)) {
                final double xCross = (xj - xi) * (z - zi) / (zj - zi) + xi;
                if (x < xCross) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /** Orientation of the triple (p, q, r): {@code >0} counter-clockwise, {@code <0} clockwise, {@code 0} collinear. */
    public static double cross(final Vec2 p, final Vec2 q, final Vec2 r) {
        return (q.x() - p.x()) * (r.z() - p.z()) - (q.z() - p.z()) * (r.x() - p.x());
    }

    private static int sign(final double v) {
        return v > Geometry.EPS ? 1 : (v < -Geometry.EPS ? -1 : 0);
    }

    private static boolean onSegment(final Vec2 p, final Vec2 q, final Vec2 r) {
        return q.x() <= Math.max(p.x(), r.x()) + Geometry.EPS && q.x() >= Math.min(p.x(), r.x()) - Geometry.EPS
                && q.z() <= Math.max(p.z(), r.z()) + Geometry.EPS && q.z() >= Math.min(p.z(), r.z()) - Geometry.EPS;
    }

    /** {@code true} if the closed segments p1-q1 and p2-q2 share at least one point. */
    public static boolean segmentsIntersect(final Vec2 p1, final Vec2 q1, final Vec2 p2, final Vec2 q2) {
        final int o1 = Geometry.sign(Geometry.cross(p1, q1, p2));
        final int o2 = Geometry.sign(Geometry.cross(p1, q1, q2));
        final int o3 = Geometry.sign(Geometry.cross(p2, q2, p1));
        final int o4 = Geometry.sign(Geometry.cross(p2, q2, q1));
        if (o1 != o2 && o3 != o4) {
            return true;
        }
        if (o1 == 0 && Geometry.onSegment(p1, p2, q1)) return true;
        if (o2 == 0 && Geometry.onSegment(p1, q2, q1)) return true;
        if (o3 == 0 && Geometry.onSegment(p2, p1, q2)) return true;
        return o4 == 0 && Geometry.onSegment(p2, q1, q2);
    }

    /** Signed area (shoelace); positive for counter-clockwise order in a right-handed XZ frame. */
    public static double signedArea(final List<Vec2> polygon) {
        double a = 0;
        final int n = polygon.size();
        for (int i = 0; i < n; i++) {
            final Vec2 p = polygon.get(i);
            final Vec2 q = polygon.get((i + 1) % n);
            a += p.x() * q.z() - q.x() * p.z();
        }
        return a / 2.0;
    }

    /** Arithmetic mean of the vertices. */
    public static Vec2 vertexCentroid(final List<Vec2> polygon) {
        double sx = 0, sz = 0;
        for (final Vec2 p : polygon) {
            sx += p.x();
            sz += p.z();
        }
        return new Vec2(sx / polygon.size(), sz / polygon.size());
    }

    /**
     * Validates a simple polygon. Returns {@code null} if valid, otherwise a {@link PolygonProblem}.
     * Rules: ≥ 3 points, ≤ {@code maxPoints}, no duplicate points, non-zero area, no two non-adjacent edges touch,
     * adjacent edges only share their common vertex (no fold-back).
     */
    public static PolygonProblem validatePolygon(final List<Vec2> pts, final int maxPoints) {
        final int n = pts.size();
        if (n < 3) {
            return PolygonProblem.TOO_FEW_POINTS;
        }
        if (n > maxPoints) {
            return PolygonProblem.TOO_MANY_POINTS;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (pts.get(i).distanceSquared(pts.get(j)) <= Geometry.EPS * Geometry.EPS) {
                    return PolygonProblem.DUPLICATE_POINT;
                }
            }
        }
        if (Math.abs(Geometry.signedArea(pts)) <= Geometry.EPS) {
            // all points on one line → zero area; otherwise (bow tie) the edges cross
            boolean collinear = true;
            for (int i = 2; i < n && collinear; i++) {
                collinear = Geometry.sign(Geometry.cross(pts.get(0), pts.get(1), pts.get(i))) == 0;
            }
            return collinear ? PolygonProblem.ZERO_AREA : PolygonProblem.SELF_INTERSECTING;
        }
        for (int i = 0; i < n; i++) {
            final Vec2 a1 = pts.get(i);
            final Vec2 a2 = pts.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                final Vec2 b1 = pts.get(j);
                final Vec2 b2 = pts.get((j + 1) % n);
                final boolean adjacent = j == i + 1 || (i == 0 && j == n - 1);
                if (adjacent) {
                    // Adjacent edges share exactly one vertex; they must not fold back onto each other.
                    final Vec2 shared = j == i + 1 ? a2 : a1;
                    final Vec2 otherA = j == i + 1 ? a1 : a2;
                    final Vec2 otherB = j == i + 1 ? b2 : b1;
                    if (Geometry.sign(Geometry.cross(otherA, shared, otherB)) == 0) {
                        final double dot = (otherA.x() - shared.x()) * (otherB.x() - shared.x())
                                + (otherA.z() - shared.z()) * (otherB.z() - shared.z());
                        if (dot > 0) {
                            return PolygonProblem.SELF_INTERSECTING;
                        }
                    }
                    continue;
                }
                if (Geometry.segmentsIntersect(a1, a2, b1, b2)) {
                    return PolygonProblem.SELF_INTERSECTING;
                }
            }
        }
        return null;
    }

    /** Why a polygon is invalid. {@link #langKey()} is the matching {@code editor_*} error message. */
    public enum PolygonProblem {
        TOO_FEW_POINTS("editor_error_polygon_too_few"),
        TOO_MANY_POINTS("editor_error_polygon_too_many"),
        DUPLICATE_POINT("editor_error_polygon_duplicate"),
        ZERO_AREA("editor_error_polygon_zero_area"),
        SELF_INTERSECTING("editor_error_polygon_self_intersecting");

        private final String langKey;

        PolygonProblem(final String langKey) {
            this.langKey = langKey;
        }

        public String langKey() {
            return this.langKey;
        }
    }
}
