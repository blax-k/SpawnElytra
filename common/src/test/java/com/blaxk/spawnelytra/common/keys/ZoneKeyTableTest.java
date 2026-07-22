/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.TestYaml;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneKeyTableTest {

    private static final Zone CIRCLE = Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100));
    private static final KeyContext CTX = KeyContext.lenient();

    private static Zone ok(final Zone z, final String key, final String value) {
        final SetResult<Zone> r = ZoneKeyTable.apply(z, key, value, ZoneKeyTableTest.CTX);
        assertTrue(r.ok(), () -> key + "=" + value + " -> " + r.error());
        return r.value();
    }

    private static String err(final Zone z, final String key, final String value) {
        final SetResult<Zone> r = ZoneKeyTable.apply(z, key, value, ZoneKeyTableTest.CTX);
        assertFalse(r.ok(), () -> key + "=" + value + " should fail");
        return r.error().key();
    }

    @Test
    void tableCoversEveryZoneField() {
        final Set<String> keys = new HashSet<>(ZoneKeyTable.keys());
        for (final String k : List.of("name", "world", "enabled", "priority", "shape", "center", "radius", "corner1", "corner2",
                "points", "min_y", "max_y", "activation_mode", "boost.enabled", "boost.strength", "boost.direction",
                "boost.max_boosts", "boost.boost_cooldown", "boost.sound", "f_key.launch_strength", "boost_display",
                "fireworks.disable_in_spawn_elytra", "hunger_consumption", "hunger_consumption.minimum_food_level",
                "hunger_consumption.activation.hunger_cost", "hunger_consumption.distance.blocks_per_point",
                "hunger_consumption.distance.hunger_cost", "hunger_consumption.time.seconds_per_point",
                "hunger_consumption.time.hunger_cost")) {
            assertTrue(keys.remove(k), k);
        }
        assertTrue(keys.isEmpty(), keys::toString);
        for (final KeySpec s : ZoneKeyTable.specs()) {
            assertTrue(s.labelKey().startsWith("menu_field_"));
            if (s.isNumeric()) {
                assertTrue(s.min() < s.max() && s.step() > 0 && s.largeStep() >= s.step(), s.key());
            }
        }
    }

    @Test
    void everyKeyRoundTripsItsCurrentValueThroughTheCodec() {
        Zone z = ZoneKeyTableTest.CIRCLE;
        z = ok(z, "hunger_consumption", "time");
        z = ok(z, "boost_display", "bossbar");
        z = ok(z, "fireworks.disable_in_spawn_elytra", "true");
        z = ok(z, "min_y", "10");
        z = ok(z, "max_y", "300");
        for (final Zone shaped : List.of(z, ok(z, "shape", "rectangle"), ok(z, "shape", "polygon"))) {
            for (final String key : ZoneKeyTable.keysFor(shaped)) {
                final String v = ZoneKeyTable.currentValue(shaped, key);
                if (key.equals("world") || key.equals("boost.sound") || key.equals("name")) {
                    continue;
                }
                assertEquals(shaped, ok(shaped, key, v), key);
            }
            final MapConfigView root = new MapConfigView();
            ZoneCodec.write(root, shaped, null, false);
            assertEquals(List.of(shaped), ZoneCodec.readAll(TestYaml.parse(TestYaml.dump(root)), HungerSettings.DEFAULTS).zones());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "enabled,toggle", "enabled,off", "priority,-1000", "priority,1000", "boost.strength,0.5", "boost.strength,10",
            "boost.max_boosts,20", "boost.boost_cooldown,0", "boost.boost_cooldown,60", "f_key.launch_strength,0.1",
            "f_key.launch_strength,5", "activation_mode,F_KEY", "activation_mode,next", "boost.direction,upward",
            "radius,1", "center,world_spawn", "center,'10.5, -3'", "min_y,none", "max_y,inherit", "boost_display,inherit",
            "hunger_consumption,inherit", "hunger_consumption,off", "fireworks.disable_in_spawn_elytra,on"})
    void validValues(final String key, final String value) {
        ok(ZoneKeyTableTest.CIRCLE, key, value);
    }

    @ParameterizedTest
    @CsvSource({
            "enabled,maybe,zone_set_invalid_boolean", "priority,1001,zone_set_out_of_range", "priority,1.5,zone_set_invalid_integer",
            "boost.strength,0.4,zone_set_out_of_range", "boost.strength,10.5,zone_set_out_of_range", "boost.strength,abc,zone_set_invalid_number",
            "boost.max_boosts,0,zone_set_out_of_range", "boost.max_boosts,21,zone_set_out_of_range",
            "boost.boost_cooldown,61,zone_set_out_of_range", "f_key.launch_strength,5.1,zone_set_out_of_range",
            "activation_mode,fly,zone_set_invalid_option", "radius,0,zone_set_out_of_range", "radius,NaN,zone_set_invalid_number",
            "center,abc,zone_set_invalid_point", "corner1,'0,0',zone_set_wrong_shape", "points,'0,0;1,0;0,1',zone_set_wrong_shape",
            "nope,1,zone_set_unknown_key", "name,Bad Name,zone_error_invalid_name", "min_y,-5000,zone_set_out_of_range"})
    void invalidValues(final String key, final String value, final String error) {
        assertEquals(error, err(ZoneKeyTableTest.CIRCLE, key, value));
    }

    @Test
    void specificSemantics() {
        Zone z = ok(ZoneKeyTableTest.CIRCLE, "enabled", "toggle");
        assertFalse(z.enabled());
        z = ok(z, "activation_mode", "next");
        assertEquals(ActivationMode.AUTO, z.activationMode());
        z = ok(z, "center", "10,20");
        assertEquals(new Vec2(10, 20), ((CircleShape) z.shape()).center());
        z = ok(z, "shape", "rectangle");
        assertEquals(new RectangleShape(new Vec2(-90, -80), new Vec2(110, 120)), z.shape());
        z = ok(z, "corner1", "0,0");
        assertEquals(ShapeType.RECTANGLE, z.shape().type());
        assertEquals("editor_error_rectangle_flat", err(z, "corner2", "0,5"));
        z = ok(z, "shape", "polygon");
        z = ok(z, "points", "0,0;10,0;10,10;0,10");
        assertEquals(4, ((PolygonShape) z.shape()).points().size());
        assertEquals("editor_error_polygon_self_intersecting", err(z, "points", "0,0;10,10;10,0;0,10"));
        assertEquals("editor_error_polygon_too_few", err(z, "points", "0,0;10,10"));
        z = ok(z, "max_y", "50");
        assertEquals("editor_error_height_order", err(z, "min_y", "51"));
        z = ok(z, "boost_display", "none");
        assertEquals(BoostDisplay.NONE, z.boostDisplayOverride());
        z = ok(z, "boost_display", "inherit");
        assertNull(z.boostDisplayOverride());
        z = ok(z, "fireworks.disable_in_spawn_elytra", "off");
        assertEquals(false, z.fireworksDisabledOverride());
        z = ok(z, "name", "  Arena ");
        assertEquals("arena", z.name());
    }

    @Test
    void hungerOverrideIsSeededFromGlobals() {
        final HungerSettings global = new HungerSettings(true, HungerMode.DISTANCE, 3, 2, 40, 5, 20, 1);
        final KeyContext ctx = new KeyContext() {
            public boolean worldExists(final String w) { return w.equals("world"); }
            public boolean soundExists(final String s) { return s.equals("ENTITY_BAT_TAKEOFF"); }
            public boolean zoneNameTaken(final String n) { return n.equals("taken"); }
            public Vec2 worldSpawn(final String w) { return new Vec2(0, 0); }
            public HungerSettings globalHunger() { return global; }
        };
        final SetResult<Zone> r = ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "hunger_consumption.time.hunger_cost", "4", ctx);
        assertEquals(global.withTimeCost(4), r.value().hungerOverride());
        assertEquals("inherit", ZoneKeyTable.currentValue(ZoneKeyTableTest.CIRCLE, "hunger_consumption.time.hunger_cost"));
        assertEquals(global.withEnabled(false), ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "hunger_consumption", "off", ctx).value().hungerOverride());
        assertEquals("zone_error_world_unknown", ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "world", "nether", ctx).error().key());
        assertEquals("zone_set_invalid_sound", ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "boost.sound", "NOPE", ctx).error().key());
        assertEquals("zone_error_name_taken", ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "name", "taken", ctx).error().key());
        assertTrue(ZoneKeyTable.apply(ZoneKeyTableTest.CIRCLE, "name", "spawn", ctx).ok());
    }

    @Test
    void keysForShape() {
        assertTrue(ZoneKeyTable.keysFor(ZoneKeyTableTest.CIRCLE).contains("radius"));
        assertFalse(ZoneKeyTable.keysFor(ZoneKeyTableTest.CIRCLE).contains("corner1"));
        assertEquals(List.of("true", "false", "toggle"), ZoneKeyTable.spec("enabled").orElseThrow().suggestions());
        assertTrue(ZoneKeyTable.spec("shape").orElseThrow().suggestions().contains("next"));
    }

    @Test
    void globalKeys() {
        final MapConfigView root = TestYaml.parse("language: en\nmessages: {style: classic}");
        assertEquals("de", GlobalKeyTable.apply(root, "language", "DE").value());
        assertEquals("de", root.get("language"));
        assertEquals("small_caps", GlobalKeyTable.apply(root, "messages.style", "next").value());
        assertEquals(false, GlobalKeyTable.apply(root, "game_modes.disable_in_creative", "toggle").value());
        assertEquals(25, GlobalKeyTable.apply(root, "hunger_consumption.distance.blocks_per_point", "25.0").value());
        assertEquals("zone_set_out_of_range", GlobalKeyTable.apply(root, "hunger_consumption.minimum_food_level", "21").error().key());
        assertEquals("zone_set_invalid_option", GlobalKeyTable.apply(root, "menus.mode", "popup").error().key());
        assertEquals("auto", GlobalKeyTable.currentValue(root, "menus.mode"));
        assertEquals("50", GlobalKeyTable.currentValue(TestYaml.parse("a: 1"), "hunger_consumption.distance.blocks_per_point"));
        assertEquals(Map.of("style", "small_caps"), ((MapConfigView) root.section("messages")).toMap());
    }
}
