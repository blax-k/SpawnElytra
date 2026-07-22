/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.TestYaml;
import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.config.MenusMode;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.migration.ConfigMigrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneCodecTest {

    private static Zone roundTrip(final Zone zone) {
        final MapConfigView root = new MapConfigView();
        ZoneCodec.write(root, zone, null, false);
        // and through real YAML text
        final MapConfigView reparsed = TestYaml.parse(TestYaml.dump(root));
        final ZoneCodec.ReadResult r = ZoneCodec.readAll(reparsed, HungerSettings.DEFAULTS);
        assertTrue(r.warnings().isEmpty(), () -> r.warnings().toString());
        assertEquals(1, r.zones().size());
        return r.zones().get(0);
    }

    @Test
    void roundTripAllShapesAndOptionalFields() {
        final Zone circle = Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100));
        assertEquals(circle, ZoneCodecTest.roundTrip(circle));

        final Zone fixed = Zone.createDefault("arena", "world_nether", new CircleShape(new Vec2(12.5, -3), 42.25))
                .withPriority(7).withEnabled(false).withMinHeight(-10.25).withMaxY(200)
                .withActivationMode(ActivationMode.F_KEY).withLaunchStrength(2.3)
                .withBoost(new BoostSettings(false, 6.5, BoostDirection.UPWARD, 3, 1.5, "ITEM_ELYTRA_FLYING"))
                .withBoostDisplayOverride(BoostDisplay.BOSSBAR).withFireworksDisabledOverride(true)
                .withHungerOverride(new HungerSettings(true, HungerMode.TIME, 4, 2, 30, 1, 12.5, 3));
        assertEquals(fixed, ZoneCodecTest.roundTrip(fixed));

        final Zone rect = Zone.createDefault("rect", "world", new RectangleShape(new Vec2(-10.5, 20), new Vec2(50, -30)));
        assertEquals(rect, ZoneCodecTest.roundTrip(rect));

        final Zone poly = Zone.createDefault("poly", "world", new PolygonShape(List.of(
                new Vec2(0, 0), new Vec2(10.5, 0), new Vec2(10, 10), new Vec2(0, 10))));
        assertEquals(poly, ZoneCodecTest.roundTrip(poly));
    }

    @Test
    void writesCanonicalNumbersAndShapeKeys() {
        final MapConfigView root = new MapConfigView();
        ZoneCodec.write(root, Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100)), null, false);
        final ConfigView z = root.section("zones").section("spawn");
        assertEquals(List.of("world", "enabled", "priority", "shape", "center", "radius", "activation_mode", "boost", "f_key"),
                List.copyOf(z.keys()));
        assertEquals("world_spawn", z.get("center"));
        assertEquals(100, z.get("radius"));
        assertEquals(4, z.get("boost.strength"));
        assertEquals(1.5, z.get("f_key.launch_strength"));

        // shape change removes stale keys, keeps canonical order
        ZoneCodec.write(root, Zone.createDefault("spawn", "world", new RectangleShape(new Vec2(0, 0), new Vec2(5, 5))), null, false);
        assertEquals(List.of("world", "enabled", "priority", "shape", "corner1", "corner2", "activation_mode", "boost", "f_key"),
                List.copyOf(root.section("zones").section("spawn").keys()));
        assertInstanceOf(Integer.class, root.get("zones.spawn.corner2.x"));
    }

    @Test
    void preservesCommentsAndUnknownKeysAndRenames() {
        final MapConfigView root = new MapConfigView();
        final Zone z = Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100));
        ZoneCodec.write(root, z, null, true);
        assertEquals(ZoneComments.DEFAULTS.get("radius"), root.section("zones").section("spawn").comments("radius"));
        root.section("zones").section("spawn").setComments("radius", List.of("my own comment"));
        root.set("zones.spawn.custom_note", "keep me");
        root.section("zones").setComments("spawn", List.of("zone comment"));

        ZoneCodec.write(root, z.withShape(CircleShape.worldSpawn(50)), null, false);
        final ConfigView s = root.section("zones").section("spawn");
        assertEquals(List.of("my own comment"), s.comments("radius"));
        assertEquals("keep me", s.get("custom_note"));
        assertEquals(50, s.get("radius"));
        assertEquals(List.of("zone comment"), root.section("zones").comments("spawn"));

        ZoneCodec.write(root, z.withName("hub"), "spawn", false);
        assertNull(root.section("zones").section("spawn"));
        assertEquals(List.of("my own comment"), root.section("zones").section("hub").comments("radius"));
        assertTrue(ZoneCodec.remove(root, "HUB"));
        assertFalse(ZoneCodec.remove(root, "hub"));
    }

    @Test
    void readSkipsInvalidZonesWithWarnings() {
        final MapConfigView root = TestYaml.parse("""
                zones:
                  good: {world: world, shape: circle, center: world_spawn, radius: 10}
                  Bad Name: {world: world, radius: 10}
                  noworld: {shape: circle, radius: 10}
                  flat: {world: world, shape: rectangle, corner1: {x: 0, z: 0}, corner2: {x: 0, z: 10}}
                  bowtie: {world: world, shape: polygon, points: [{x: 0, z: 0}, {x: 10, z: 10}, {x: 10, z: 0}, {x: 0, z: 10}]}
                  GOOD: {world: world, radius: 5}
                  notasection: 5
                  inferred: {world: world, points: [{x: 0, z: 0}, {x: 10, z: 0}, {x: 0, z: 10}]}
                  stringpts: {world: world, shape: polygon, points: ["0,0", "10,0", "0,10"]}
                """);
        final ZoneCodec.ReadResult r = ZoneCodec.readAll(root, HungerSettings.DEFAULTS);
        assertEquals(List.of("good", "inferred", "stringpts"), r.zones().stream().map(Zone::name).toList());
        assertEquals(6, r.warnings().size());
        assertEquals("zone_load_invalid", r.warnings().get(2).key());
        assertEquals(ShapeType.POLYGON, r.zones().get(1).shape().type());
    }

    @Test
    void readDefaultsMatch15() {
        final Zone z = ZoneCodec.read("x", TestYaml.parse("world: world"), HungerSettings.DEFAULTS);
        assertTrue(z.enabled());
        assertEquals(0, z.priority());
        assertEquals(new CircleShape(null, 100), z.shape());
        assertEquals(ActivationMode.DOUBLE_JUMP, z.activationMode());
        assertEquals(new BoostSettings(true, 2, BoostDirection.FORWARD, 1, 0, "ENTITY_BAT_TAKEOFF"), z.boost());
        assertEquals(1.5, z.launchStrength());
        assertNull(z.hungerOverride());
        assertNull(z.minHeight());
        // hunger override: missing keys fall back to globals
        final HungerSettings global = new HungerSettings(true, HungerMode.DISTANCE, 3, 2, 40, 5, 20, 1);
        final Zone h = ZoneCodec.read("x", TestYaml.parse("world: w\nhunger_consumption: {mode: time}"), global);
        assertEquals(global.withMode(HungerMode.TIME), h.hungerOverride());
    }

    @Test
    void shippedConfigIsValid16() {
        final MapConfigView root = TestYaml.file("src/main/resources/config.yml");
        final GlobalSettings g = GlobalSettings.read(root);
        assertEquals(BoostDisplay.ACTIONBAR, g.boostDisplay());
        assertEquals(MenusMode.AUTO, g.menusMode());
        assertTrue(g.tiers().isEmpty());
        final ZoneCodec.ReadResult r = ZoneCodec.readAll(root, g.hunger());
        assertTrue(r.warnings().isEmpty());
        assertEquals(List.of(Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100))), r.zones());
        assertFalse(ConfigMigrator.isLegacy(root));
        final ConfigMigrator.Result m = ConfigMigrator.migrate(root);
        assertFalse(m.changed(), () -> m.log().toString());
        assertTrue(root.section("permission_tiers").keys().isEmpty());
        assertEquals(Map.of(), ((MapConfigView) root.section("permission_tiers")).toMap());
    }
}
