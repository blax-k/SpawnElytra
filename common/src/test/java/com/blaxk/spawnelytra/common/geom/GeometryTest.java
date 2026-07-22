/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.geom;

import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryTest {

    /** 1.5 SpawnElytra#isInSpawnArea circle branch: {@code spawnLocation.distance(player) <= radius} (same Y). */
    private static boolean legacyCircle(final double cx, final double cz, final double r, final double x, final double z) {
        final double dx = x - cx;
        final double dy = 0;
        final double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= r;
    }

    /** 1.5 rectangle branch (X/Z part): {@code x >= minX && x <= maxX && z >= minZ && z <= maxZ}. */
    private static boolean legacyRect(final double x1, final double z1, final double x2, final double z2, final double x, final double z) {
        final double minX = Math.min(x1, x2);
        final double maxX = Math.max(x1, x2);
        final double minZ = Math.min(z1, z2);
        final double maxZ = Math.max(z1, z2);
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Test
    void circleMatchesLegacyMathOnRandomPoints() {
        final Random rnd = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            final double cx = rnd.nextInt(2000) - 1000 + rnd.nextDouble();
            final double cz = rnd.nextInt(2000) - 1000;
            final double r = 1 + rnd.nextInt(200);
            final double x = cx + (rnd.nextDouble() * 2 - 1) * r * 1.3;
            final double z = cz + (rnd.nextDouble() * 2 - 1) * r * 1.3;
            final CircleShape c = new CircleShape(new Vec2(cx, cz), r);
            assertEquals(legacyCircle(cx, cz, r, x, z), c.contains(x, z, null), () -> "mismatch at " + x + "," + z);
        }
    }

    @Test
    void circleEdgesInclusive() {
        final CircleShape c = new CircleShape(new Vec2(0, 0), 100);
        assertTrue(c.contains(100, 0, null));
        assertTrue(c.contains(0, -100, null));
        assertTrue(c.contains(60, 80, null)); // exactly on the circle (3-4-5)
        assertFalse(c.contains(100.0001, 0, null));
        assertFalse(c.contains(71, 71, null));
        assertTrue(c.contains(70.7, 70.7, null));
    }

    @Test
    void worldSpawnCircleFollowsSpawnAndNeedsIt() {
        final CircleShape c = CircleShape.worldSpawn(10);
        assertFalse(c.contains(0, 0, null));
        assertTrue(c.contains(105, 200, new Vec2(100, 200)));
        assertFalse(c.contains(0, 0, new Vec2(100, 200)));
        assertNull(c.bounds(null));
    }

    @Test
    void rectangleMatchesLegacyMathOnRandomPoints() {
        final Random rnd = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            final double x1 = rnd.nextInt(400) - 200 + (rnd.nextBoolean() ? 0.5 : 0);
            final double z1 = rnd.nextInt(400) - 200;
            final double x2 = rnd.nextInt(400) - 200;
            final double z2 = rnd.nextInt(400) - 200 + (rnd.nextBoolean() ? 0.25 : 0);
            final double x = rnd.nextInt(420) - 210 + (rnd.nextInt(4) == 0 ? 0 : rnd.nextDouble());
            final double z = rnd.nextInt(420) - 210 + (rnd.nextInt(4) == 0 ? 0 : rnd.nextDouble());
            final RectangleShape r = new RectangleShape(new Vec2(x1, z1), new Vec2(x2, z2));
            assertEquals(legacyRect(x1, z1, x2, z2, x, z), r.contains(x, z, null));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,true", "10,10,true", "0,10,true", "10,0,true", "5,5,true",
            "10.0001,5,false", "-0.0001,5,false", "5,10.5,false", "10,10.0000001,false"})
    void rectangleBoundsInclusive(final double x, final double z, final boolean inside) {
        final RectangleShape r = new RectangleShape(new Vec2(10, 0), new Vec2(0, 10)); // corner order irrelevant
        assertEquals(inside, r.contains(x, z, null));
    }

    private static final List<Vec2> SQUARE = List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(0, 10));
    /** L shape (concave). */
    private static final List<Vec2> L_SHAPE = List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 4), new Vec2(4, 4),
            new Vec2(4, 10), new Vec2(0, 10));

    @ParameterizedTest
    @CsvSource({
            "5,5,true", "0,0,true", "10,10,true", "5,0,true", "0,5,true", "10,7.5,true",
            "-0.001,5,false", "10.001,5,false", "5,-1,false", "20,20,false"})
    void polygonSquareEdgesAndVertices(final double x, final double z, final boolean inside) {
        assertEquals(inside, Geometry.polygonContains(GeometryTest.SQUARE, x, z));
    }

    @ParameterizedTest
    @CsvSource({
            "2,2,true", "8,2,true", "2,8,true", "4,7,true", "7,4,true", "4,4,true",
            "7,7,false", "5,5,false", "9.9,9.9,false", "4.0001,4.0001,false",
            // ray through a vertex at z=4 / z=0 must not double count
            "-1,4,false", "-1,0,false", "2,4,true"})
    void polygonConcaveEvenOdd(final double x, final double z, final boolean inside) {
        assertEquals(inside, Geometry.polygonContains(GeometryTest.L_SHAPE, x, z));
        assertEquals(inside, new PolygonShape(GeometryTest.L_SHAPE).contains(x, z, null));
    }

    @Test
    void polygonMatchesRectangleForAxisAlignedSquares() {
        final Random rnd = new Random(3);
        final RectangleShape rect = new RectangleShape(new Vec2(0, 0), new Vec2(10, 10));
        for (int i = 0; i < 5000; i++) {
            final double x = rnd.nextInt(14) - 2 + (rnd.nextBoolean() ? 0 : rnd.nextDouble());
            final double z = rnd.nextInt(14) - 2 + (rnd.nextBoolean() ? 0 : rnd.nextDouble());
            assertEquals(rect.contains(x, z, null), Geometry.polygonContains(GeometryTest.SQUARE, x, z), x + "," + z);
        }
    }

    @Test
    void polygonValidation() {
        assertNull(Geometry.validatePolygon(GeometryTest.SQUARE, 64));
        assertNull(Geometry.validatePolygon(GeometryTest.L_SHAPE, 64));
        assertEquals(Geometry.PolygonProblem.TOO_FEW_POINTS, Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(1, 0)), 64));
        assertEquals(Geometry.PolygonProblem.TOO_MANY_POINTS, Geometry.validatePolygon(GeometryTest.SQUARE, 3));
        assertEquals(Geometry.PolygonProblem.DUPLICATE_POINT,
                Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(10, 0)), 64));
        assertEquals(Geometry.PolygonProblem.ZERO_AREA,
                Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(5, 0), new Vec2(10, 0)), 64));
        // bow tie
        assertEquals(Geometry.PolygonProblem.SELF_INTERSECTING,
                Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(10, 10), new Vec2(10, 0), new Vec2(0, 10)), 64));
        // vertex touching a non-adjacent edge
        assertEquals(Geometry.PolygonProblem.SELF_INTERSECTING,
                Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(5, 0), new Vec2(0, 10)), 64));
        // fold back: spike going back along the previous edge
        assertEquals(Geometry.PolygonProblem.SELF_INTERSECTING,
                Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(5, 0), new Vec2(5, 10)), 64));
        // collinear but continuing point is fine
        assertNull(Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(5, 0), new Vec2(10, 0), new Vec2(10, 10)), 64));
        // triangle
        assertNull(Geometry.validatePolygon(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(0, 10)), 64));
    }

    @Test
    void segmentIntersection() {
        assertTrue(Geometry.segmentsIntersect(new Vec2(0, 0), new Vec2(10, 10), new Vec2(0, 10), new Vec2(10, 0)));
        assertTrue(Geometry.segmentsIntersect(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 0), new Vec2(10, 10)));
        assertTrue(Geometry.segmentsIntersect(new Vec2(0, 0), new Vec2(10, 0), new Vec2(5, 0), new Vec2(15, 0)));
        assertFalse(Geometry.segmentsIntersect(new Vec2(0, 0), new Vec2(10, 0), new Vec2(11, 0), new Vec2(15, 0)));
        assertFalse(Geometry.segmentsIntersect(new Vec2(0, 0), new Vec2(10, 0), new Vec2(0, 1), new Vec2(10, 1)));
    }

    @Test
    void distanceToSegment() {
        assertEquals(5, Geometry.distanceToSegment(5, 5, 0, 0, 10, 0), 1e-12);
        assertEquals(5, Geometry.distanceToSegment(-3, 4, 0, 0, 10, 0), 1e-12);
        assertEquals(0, Geometry.distanceToSegment(3, 0, 0, 0, 10, 0), 1e-12);
    }

    @Test
    void boundsHelpers() {
        final Bounds2D b = Bounds2D.of(GeometryTest.L_SHAPE);
        assertEquals(new Bounds2D(0, 0, 10, 10), b);
        assertEquals(new Vec2(5, 5), b.center());
        assertTrue(b.intersects(new Bounds2D(10, 10, 20, 20)));
        assertFalse(b.intersects(new Bounds2D(10.1, 0, 20, 20)));
        assertEquals(new Vec2(3.5, -1.5), Vec2.blockCenter(3, -2));
    }
}
