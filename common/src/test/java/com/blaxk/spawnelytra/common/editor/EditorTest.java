/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.keys.KeyContext;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.ShapeOps;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorTest {

    private static ZoneDraft draft(final ShapeType type) {
        return new ZoneDraft(Zone.createDefault("arena", "world", ShapeOps.createAround(type, Vec2.blockCenter(0, 0))), null, new Vec2(0, 0));
    }

    @Test
    void historyUndoRedoAndCapacity() {
        final EditHistory<Integer> h = new EditHistory<>(0, 50);
        for (int i = 1; i <= 60; i++) {
            assertTrue(h.push(i));
        }
        assertFalse(h.push(60));
        assertEquals(50, h.undoSize());
        for (int i = 0; i < 50; i++) {
            assertEquals(59 - i, h.undo());
        }
        assertNull(h.undo());
        assertEquals(10, h.current());
        assertEquals(11, h.redo());
        h.push(99);
        assertFalse(h.canRedo());
        assertEquals(11, h.undo());
        assertTrue(EditHistory.DEFAULT_CAPACITY >= 50);
    }

    @Test
    void circleTools() {
        final ZoneDraft d = EditorTest.draft(ShapeType.CIRCLE);
        assertTrue(d.setCircleCenter(Vec2.blockCenter(10, 10)).success());
        assertEquals(new Vec2(10.5, 10.5), ((CircleShape) d.zone().shape()).center());
        assertTrue(d.scrollResize(1).success());
        assertEquals(31, ((CircleShape) d.zone().shape()).radius());
        d.toggleRadiusStep();
        assertEquals(5, d.radiusStep());
        d.scrollResize(-1);
        assertEquals(26, ((CircleShape) d.zone().shape()).radius());
        for (int i = 0; i < 5; i++) {
            d.scrollResize(-1);
        }
        assertEquals(1, ((CircleShape) d.zone().shape()).radius());
        final EditResult r = d.scrollResize(-1);
        assertFalse(r.success());
        assertEquals("editor_error_radius", r.error().key());
        assertEquals("editor_error_wrong_shape", d.setRectangleCorner(1, new Vec2(0, 0)).error().key());
        // undo walks back each successful change
        assertTrue(d.undo().success());
        assertEquals(6, ((CircleShape) d.zone().shape()).radius());
        assertTrue(d.redo().success());
        assertEquals(1, ((CircleShape) d.zone().shape()).radius());
        assertTrue(d.isDirty());
        assertTrue(d.isNew());
    }

    @Test
    void rectangleAndPolygonTools() {
        final ZoneDraft r = EditorTest.draft(ShapeType.RECTANGLE);
        assertTrue(r.setRectangleCorner(1, new Vec2(-5, -5)).success());
        assertTrue(r.setRectangleCorner(2, new Vec2(5, 5)).success());
        assertFalse(r.setRectangleCorner(2, new Vec2(-5, 7)).success()); // flat
        r.scrollResize(1);
        assertEquals(new RectangleShape(new Vec2(-6, -6), new Vec2(6, 6)), r.zone().shape());

        final ZoneDraft p = EditorTest.draft(ShapeType.POLYGON);
        assertTrue(p.addPolygonPoint(new Vec2(0.5, -20)).success());
        assertEquals(5, ((PolygonShape) p.zone().shape()).points().size());
        final EditResult crossing = p.addPolygonPoint(new Vec2(0.5, 40)); // would cross with the far edge? stays simple -> ok
        assertTrue(crossing.success() || "editor_error_polygon_self_intersecting".equals(crossing.error().key()));
        while (((PolygonShape) p.zone().shape()).points().size() > 3) {
            assertTrue(p.removePolygonPoint(((PolygonShape) p.zone().shape()).points().get(0)).success());
        }
        assertEquals("editor_error_polygon_too_few", p.removePolygonPoint(new Vec2(0, 0)).error().key());
    }

    @Test
    void selfIntersectionRejected() {
        final ZoneDraft p = new ZoneDraft(Zone.createDefault("p", "world", new PolygonShape(List.of(
                new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(5, 2), new Vec2(0, 10)))), "p", null);
        // removing (10,0) leaves (0,0)-(10,10) crossing (5,2)-(0,10)
        final EditResult r = p.removePolygonPoint(new Vec2(10.5, -1));
        assertFalse(r.success());
        assertEquals("editor_error_polygon_self_intersecting", r.error().key());
        assertFalse(p.isDirty());
    }

    @Test
    void heightTool() {
        final ZoneDraft d = EditorTest.draft(ShapeType.CIRCLE);
        assertEquals(ZoneDraft.HeightBound.TOP, d.heightBound());
        d.scrollHeight(1, 70);
        assertEquals(70.0, d.zone().maxHeight());
        d.scrollHeight(1, 70);
        assertEquals(71.0, d.zone().maxHeight());
        d.toggleHeightBound();
        d.scrollHeight(-1, 60);
        assertEquals(60.0, d.zone().minHeight());
        for (int i = 0; i < 11; i++) {
            d.scrollHeight(1, 0);
        }
        assertEquals(71.0, d.zone().minHeight());
        assertEquals("editor_error_height_order", d.scrollHeight(1, 0).error().key());
        assertTrue(d.clearHeight().success());
        assertNull(d.zone().minHeight());
        assertNull(d.zone().maxHeight());
    }

    @Test
    void moveTool() {
        final ZoneDraft d = new ZoneDraft(Zone.createDefault("s", "world", CircleShape.worldSpawn(10)), "s", new Vec2(100, 100));
        assertTrue(d.pickUp(new Vec2(100, 100)).success());
        assertTrue(d.isMoving());
        assertEquals("editor_error_moving", d.scrollResize(1).error().key());
        assertTrue(d.moveTo(new Vec2(110.4, 95.6)));
        assertEquals(new CircleShape(new Vec2(110, 96), 10), d.zone().shape());
        assertFalse(d.moveTo(new Vec2(110.3, 95.7)));
        assertEquals(0, d.history().undoSize()); // moving is preview only
        assertTrue(d.pickUp(new Vec2(0, 0)).success()); // second right-click = place
        assertFalse(d.isMoving());
        assertEquals(1, d.history().undoSize());
        d.undo();
        assertEquals(CircleShape.worldSpawn(10), d.zone().shape());
        d.pickUp(new Vec2(0, 0));
        d.moveTo(new Vec2(5, 5));
        d.cancelMove();
        assertEquals(CircleShape.worldSpawn(10), d.zone().shape());
    }

    @Test
    void shapeCycleAndKeys() {
        final ZoneDraft d = EditorTest.draft(ShapeType.CIRCLE);
        d.cycleShape();
        assertEquals(ShapeType.RECTANGLE, d.zone().shape().type());
        d.cycleShape();
        assertEquals(ShapeType.POLYGON, d.zone().shape().type());
        d.cycleShape();
        assertEquals(ShapeType.CIRCLE, d.zone().shape().type());
        assertEquals(30, ((CircleShape) d.zone().shape()).radius());
        assertTrue(d.applyKey("boost.strength", "6.5", KeyContext.lenient()).success());
        assertEquals(6.5, d.zone().boost().strength());
        assertEquals("zone_set_out_of_range", d.applyKey("boost.strength", "60", KeyContext.lenient()).error().key());
        d.undo();
        assertEquals(4, d.zone().boost().strength());
        assertEquals(3, d.history().undoSize());
    }

    @Test
    void saveCheck() {
        final Zone other = Zone.createDefault("big", "world", new CircleShape(new Vec2(0, 0), 100)).withPriority(5);
        final Zone saved = Zone.createDefault("arena", "world", new CircleShape(new Vec2(500, 500), 10));
        final ZoneRegistry reg = new ZoneRegistry(List.of(other, saved));

        final ZoneDraft fresh = EditorTest.draft(ShapeType.CIRCLE); // new zone named "arena" -> taken
        assertEquals("zone_error_name_taken", fresh.checkSave(reg, KeyContext.lenient()).errors().get(0).key());

        final ZoneDraft edit = new ZoneDraft(saved, "arena", null);
        edit.setCircleCenter(new Vec2(50, 0));
        final ZoneDraft.SaveCheck c = edit.checkSave(reg, KeyContext.lenient());
        assertTrue(c.ok(), c.errors()::toString);
        assertEquals(1, c.warnings().size());
        assertEquals("editor_save_overlap", c.warnings().get(0).key());
        assertEquals("5", c.warnings().get(0).args().get("other_priority"));

        final KeyContext noWorld = new KeyContext() {
            public boolean worldExists(final String w) { return false; }
            public boolean soundExists(final String s) { return true; }
            public boolean zoneNameTaken(final String n) { return false; }
            public Vec2 worldSpawn(final String w) { return null; }
            public com.blaxk.spawnelytra.common.zone.HungerSettings globalHunger() { return com.blaxk.spawnelytra.common.zone.HungerSettings.DEFAULTS; }
        };
        assertEquals("zone_error_world_unknown", edit.checkSave(reg, noWorld).errors().get(0).key());
    }

    @Test
    void toolsAndHud() {
        assertEquals(9, EditorTool.values().length);
        assertSame(EditorTool.SAVE, EditorTool.bySlot(8));
        assertSame(EditorTool.MOVE, EditorTool.byId("move"));
        assertEquals("editor_tool_shape_polygon_name", EditorTool.SHAPE.nameKey(ShapeType.POLYGON));
        assertEquals("editor_hint_resize_circle", EditorTool.RESIZE.hintKey(ShapeType.CIRCLE));
        assertEquals("editor_tool_save_lore", EditorTool.SAVE.loreKey(ShapeType.CIRCLE));
        final ZoneDraft d = EditorTest.draft(ShapeType.CIRCLE);
        assertEquals("editor_bossbar", d.bossbarTitle(EditorTool.HEIGHT).key());
        assertEquals("editor_hint_height", d.actionbarHint(EditorTool.HEIGHT).key());
        assertEquals("editor_hint_none", d.actionbarHint(null).key());
    }

    @Test
    void previewSegmentsAndOverlap() {
        final Zone draft = Zone.createDefault("arena", "world", new RectangleShape(new Vec2(0, 0), new Vec2(20, 20))).withMaxY(100);
        final Zone other = Zone.createDefault("big", "world", new RectangleShape(new Vec2(10, -10), new Vec2(40, 40)));
        final Preview p = Preview.build(draft, List.of(other), null, 5, 64.7, 5, Preview.Options.DEFAULT);
        assertFalse(p.segments().isEmpty());
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.DRAFT));
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.DRAFT_OVERLAP));
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.OTHER));
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.OTHER_OVERLAP));
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.HEIGHT_TOP));
        assertTrue(p.segments().stream().noneMatch(s -> s.style() == Preview.Style.HEIGHT_BOTTOM));
        assertTrue(p.segments().stream().filter(s -> s.style() == Preview.Style.DRAFT).allMatch(s -> s.y() == 64));
        // the draft edge x=20 from z=0..20 lies inside "big" -> red; merged into one segment of length 20
        assertTrue(p.segments().stream().anyMatch(s -> s.style() == Preview.Style.DRAFT_OVERLAP && Math.abs(s.length() - 20) < 1e-9));
        assertEquals("editor_label", p.label().text().key());
        assertEquals(new Vec2(10, 10), p.label().position());

        // range filter + cap
        final Zone huge = Zone.createDefault("huge", "world", new CircleShape(new Vec2(0, 0), 5000));
        final Preview far = Preview.build(huge, List.of(), null, 5000, 64, 0, new Preview.Options(64, 4, 10));
        assertTrue(far.segments().size() <= 10);
        assertTrue(far.segments().stream().allMatch(s -> s.from().distance(new Vec2(5000, 0)) < 70));
        assertNull(far.label());
    }
}
