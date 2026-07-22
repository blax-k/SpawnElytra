/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeOpsTest {

    @Test
    void createAroundPlayer() {
        final Vec2 c = Vec2.blockCenter(10, 20);
        final CircleShape circle = (CircleShape) ShapeOps.createAround(ShapeType.CIRCLE, c);
        assertEquals(30, circle.radius());
        assertEquals(c, circle.center());
        final RectangleShape rect = (RectangleShape) ShapeOps.createAround(ShapeType.RECTANGLE, c);
        assertEquals(30, rect.maxX() - rect.minX());
        assertEquals(30, rect.maxZ() - rect.minZ());
        assertTrue(rect.contains(c.x(), c.z(), null));
        final PolygonShape poly = (PolygonShape) ShapeOps.createAround(ShapeType.POLYGON, c);
        assertEquals(4, poly.points().size());
        assertNull(poly.validate());
        assertEquals(rect.bounds(null), poly.bounds(null));
    }

    @Test
    void convertViaBoundingBox() {
        final CircleShape c = new CircleShape(new Vec2(0, 0), 10);
        final RectangleShape r = (RectangleShape) ShapeOps.convert(c, ShapeType.RECTANGLE, null);
        assertEquals(new RectangleShape(new Vec2(-10, -10), new Vec2(10, 10)), r);

        final CircleShape back = (CircleShape) ShapeOps.convert(new RectangleShape(new Vec2(0, 0), new Vec2(20, 10)), ShapeType.CIRCLE, null);
        assertEquals(new Vec2(10, 5), back.center());
        assertEquals(10, back.radius());

        final PolygonShape fromRect = (PolygonShape) ShapeOps.convert(r, ShapeType.POLYGON, null);
        assertEquals(4, fromRect.points().size());
        assertNull(fromRect.validate());

        final PolygonShape fromCircle = (PolygonShape) ShapeOps.convert(c, ShapeType.POLYGON, null);
        assertEquals(ShapeOps.CIRCLE_TO_POLYGON_POINTS, fromCircle.points().size());
        assertNull(fromCircle.validate());
        for (final Vec2 p : fromCircle.points()) {
            assertEquals(10, p.distance(new Vec2(0, 0)), 0.01);
        }

        final RectangleShape fromPoly = (RectangleShape) ShapeOps.convert(
                new PolygonShape(List.of(new Vec2(0, 0), new Vec2(8, 2), new Vec2(3, 9))), ShapeType.RECTANGLE, null);
        assertEquals(new RectangleShape(new Vec2(0, 0), new Vec2(8, 9)), fromPoly);

        assertSame(c, ShapeOps.convert(c, ShapeType.CIRCLE, null));
    }

    @Test
    void convertWorldSpawnCircleNeedsSpawn() {
        final CircleShape c = CircleShape.worldSpawn(5);
        assertNull(ShapeOps.convert(c, ShapeType.RECTANGLE, null));
        assertEquals(new RectangleShape(new Vec2(95, -5), new Vec2(105, 5)), ShapeOps.convert(c, ShapeType.RECTANGLE, new Vec2(100, 0)));
    }

    @Test
    void cycleOrder() {
        assertEquals(ShapeType.RECTANGLE, ShapeType.CIRCLE.next());
        assertEquals(ShapeType.POLYGON, ShapeType.RECTANGLE.next());
        assertEquals(ShapeType.CIRCLE, ShapeType.POLYGON.next());
    }

    @Test
    void intersections() {
        final CircleShape c1 = new CircleShape(new Vec2(0, 0), 10);
        final CircleShape c2 = new CircleShape(new Vec2(20, 0), 10);
        final CircleShape c3 = new CircleShape(new Vec2(21, 0), 10);
        assertTrue(ShapeOps.intersects(c1, c2, null)); // touching counts
        assertFalse(ShapeOps.intersects(c1, c3, null));

        final RectangleShape r = new RectangleShape(new Vec2(9, -1), new Vec2(30, 1));
        assertTrue(ShapeOps.intersects(c1, r, null));
        assertTrue(ShapeOps.intersects(r, c1, null));
        final RectangleShape corner = new RectangleShape(new Vec2(8, 8), new Vec2(20, 20)); // corner (8,8) is 11.3 away
        assertFalse(ShapeOps.intersects(c1, corner, null));

        final RectangleShape inner = new RectangleShape(new Vec2(-1, -1), new Vec2(1, 1));
        assertTrue(ShapeOps.intersects(c1, inner, null)); // fully inside
        final RectangleShape outer = new RectangleShape(new Vec2(-50, -50), new Vec2(50, 50));
        assertTrue(ShapeOps.intersects(c1, outer, null)); // circle fully inside

        final PolygonShape tri = new PolygonShape(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(0, 10)));
        final PolygonShape far = new PolygonShape(List.of(new Vec2(6, 6), new Vec2(10, 6), new Vec2(10, 10)));
        assertFalse(ShapeOps.intersects(tri, far, null));
        final PolygonShape cross = new PolygonShape(List.of(new Vec2(4, 4), new Vec2(10, 4), new Vec2(10, 10)));
        assertTrue(ShapeOps.intersects(tri, cross, null));
        assertTrue(ShapeOps.intersects(outer, tri, null)); // contained
    }

    @Test
    void zoneOverlapRespectsWorldAndHeight() {
        final Zone a = Zone.createDefault("a", "world", new CircleShape(new Vec2(0, 0), 10));
        final Zone b = Zone.createDefault("b", "world", new CircleShape(new Vec2(5, 0), 10));
        assertTrue(ZoneOverlaps.overlaps(a, b, null, null));
        assertFalse(ZoneOverlaps.overlaps(a, b.withWorld("world_nether"), null, null));
        assertFalse(ZoneOverlaps.overlaps(a.withMaxY(50), b.withMinY(51), null, null));
        assertTrue(ZoneOverlaps.overlaps(a.withMaxY(50), b.withMinY(50), null, null));
        final ZoneRegistry reg = new ZoneRegistry(List.of(a, b, Zone.createDefault("c", "world", new CircleShape(new Vec2(100, 0), 5))));
        assertEquals(List.of("b"), ZoneOverlaps.overlapping(a, reg, null).stream().map(Zone::name).toList());
    }

    @Test
    void outlines() {
        final CircleShape c = new CircleShape(new Vec2(0, 0), 100);
        final List<Vec2> o = c.outline(null, 4);
        assertTrue(o.size() >= 2 * Math.PI * 100 / 4);
        for (final Vec2 p : o) {
            assertEquals(100, p.distance(new Vec2(0, 0)), 1e-9);
        }
        assertEquals(24, new CircleShape(new Vec2(0, 0), 1).outline(null, 4).size());
        assertInstanceOf(List.class, new RectangleShape(new Vec2(0, 0), new Vec2(1, 1)).outline(null, 4));
    }

    @Test
    void growShrink() {
        final RectangleShape r = new RectangleShape(new Vec2(10, 0), new Vec2(0, 10));
        final RectangleShape g = r.grow(1);
        assertEquals(new RectangleShape(new Vec2(11, -1), new Vec2(-1, 11)), g);
        assertEquals(r, g.grow(-1));
        final PolygonShape sq = new PolygonShape(List.of(new Vec2(-1, -1), new Vec2(1, -1), new Vec2(1, 1), new Vec2(-1, 1)));
        final PolygonShape bigger = sq.grow(1);
        assertEquals(Math.sqrt(2) + 1, bigger.points().get(0).distance(new Vec2(0, 0)), 0.01);
        assertNull(sq.grow(-1)); // would come within 0.5 of the centroid
    }

    @Test
    void polygonEditing() {
        final PolygonShape sq = new PolygonShape(List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(0, 10)));
        final PolygonShape added = sq.withPointInserted(new Vec2(5, -3)); // nearest edge 0 -> 1
        assertEquals(List.of(new Vec2(0, 0), new Vec2(5, -3), new Vec2(10, 0), new Vec2(10, 10), new Vec2(0, 10)), added.points());
        assertNull(added.validate());
        final PolygonShape removed = added.withNearestPointRemoved(4, -2);
        assertEquals(sq, removed);
        assertEquals(2, sq.nearestEdge(5, 11));
        assertEquals(3, sq.nearestPoint(-1, 11));
    }
}
