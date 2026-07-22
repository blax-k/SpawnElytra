/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.migration;

import com.blaxk.spawnelytra.common.TestYaml;
import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostSettings;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import com.blaxk.spawnelytra.common.zone.ZoneComments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static List<Zone> migrateAndRead(final MapConfigView root) {
        assertTrue(ConfigMigrator.isLegacy(root));
        final ConfigMigrator.Result r = ConfigMigrator.migrate(root);
        assertTrue(r.changed());
        assertTrue(r.migratedWorlds());
        assertNull(root.get("worlds"));
        // reparse through YAML text to make sure the result is plain serializable data
        final MapConfigView reparsed = TestYaml.parse(TestYaml.dump(root));
        final ZoneCodec.ReadResult read = ZoneCodec.readAll(reparsed, GlobalSettings.read(reparsed).hunger());
        assertTrue(read.warnings().isEmpty(), () -> read.warnings().toString());
        // idempotent
        final Map<String, Object> before = root.toMap();
        final ConfigMigrator.Result again = ConfigMigrator.migrate(root);
        assertFalse(again.changed());
        assertEquals(before, root.toMap());
        return read.zones();
    }

    @Test
    void defaultConfig() {
        final MapConfigView root = TestYaml.resource("fixtures/v15/default.yml");
        final List<Zone> zones = ConfigMigratorTest.migrateAndRead(root);
        assertEquals(List.of(Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100))), zones);
        // globals untouched + new ones added
        assertEquals("en", root.get("language"));
        assertEquals(false, root.get("hunger_consumption.enabled"));
        assertEquals("actionbar", root.get("boost_display"));
        assertEquals("auto", root.get("menus.mode"));
        assertNotNull(root.section("permission_tiers"));
        // zones are last, with shipped comments
        final List<String> keys = List.copyOf(root.keys());
        assertEquals("zones", keys.get(keys.size() - 1));
        assertEquals(ZoneComments.ZONES_SECTION, root.comments("zones"));
        assertEquals(ZoneComments.DEFAULTS.get("activation_mode"), root.section("zones").section("spawn").comments("activation_mode"));
        assertEquals(ConfigMigrator.TIERS_COMMENTS, root.comments("permission_tiers"));
    }

    @Test
    void advancedCircular() {
        final List<Zone> zones = ConfigMigratorTest.migrateAndRead(TestYaml.resource("fixtures/v15/advanced_circular.yml"));
        assertEquals(1, zones.size());
        final Zone z = zones.get(0);
        assertEquals(new CircleShape(new Vec2(120.5, -45), 75), z.shape());
        assertEquals(ActivationMode.F_KEY, z.activationMode());
        assertEquals(6, z.boost().strength());
        assertEquals(2.5, z.launchStrength());
        assertNull(z.minHeight()); // circles: no height limit (1.6 XZ cylinder)
        assertNull(z.maxHeight());
    }

    @Test
    void advancedRectangular() {
        final List<Zone> zones = ConfigMigratorTest.migrateAndRead(TestYaml.resource("fixtures/v15/advanced_rectangular.yml"));
        final Zone z = zones.get(0);
        assertEquals(new RectangleShape(new Vec2(-10.5, 20), new Vec2(50, -30)), z.shape());
        assertEquals(64.0, z.minHeight()); // 1.5 box: min(y, y2) .. max(y, y2)
        assertEquals(80.0, z.maxHeight());
        assertEquals(ActivationMode.SNEAK_JUMP, z.activationMode());
        assertEquals(new BoostSettings(true, 3, BoostDirection.UPWARD, 3, 2.5, "ENTITY_FIREWORK_ROCKET_BLAST"), z.boost());
    }

    @Test
    void multipleWorldsNamingOrderAndShapes() {
        final MapConfigView root = TestYaml.resource("fixtures/v15/multiple_worlds.yml");
        final ConfigMigrator.Result preview = ConfigMigrator.migrate(TestYaml.resource("fixtures/v15/multiple_worlds.yml"));
        assertEquals(List.of("spawn", "spawn_world_nether", "spawn_my_world_", "spawn_world_the_end"), preview.createdZones());
        final List<Zone> zones = ConfigMigratorTest.migrateAndRead(root);
        final Map<String, Zone> byName = new java.util.HashMap<>();
        zones.forEach(z -> byName.put(z.name(), z));
        assertEquals("world", byName.get("spawn").world());
        assertEquals(CircleShape.worldSpawn(150), byName.get("spawn").shape());
        assertEquals(new RectangleShape(new Vec2(0, 0), new Vec2(40, 40)), byName.get("spawn_world_nether").shape());
        assertEquals(0.0, byName.get("spawn_world_nether").minHeight()); // y=30, y2=0
        assertEquals(30.0, byName.get("spawn_world_nether").maxHeight());
        assertNull(byName.get("spawn").minHeight());
        assertNull(byName.get("spawn_world_the_end").maxHeight());
        assertEquals(ActivationMode.AUTO, byName.get("spawn_world_nether").activationMode());
        assertEquals("My World!", byName.get("spawn_my_world_").world());
        assertFalse(byName.get("spawn_my_world_").enabled());
        assertEquals(new CircleShape(new Vec2(5, 5), 20), byName.get("spawn_my_world_").shape());
        // advanced + rectangular but second corner all zero -> circle around x/z (1.5 behaviour)
        assertEquals(new CircleShape(new Vec2(10, 10), 33), byName.get("spawn_world_the_end").shape());
        // zone order in the file follows migration order
        assertEquals(List.of("spawn", "spawn_world_nether", "spawn_my_world_", "spawn_world_the_end"),
                List.copyOf(root.section("zones").keys()));
    }

    @Test
    void perWorldHungerOverridesAreCopied() {
        final MapConfigView root = TestYaml.resource("fixtures/v15/per_world_hunger.yml");
        final List<Zone> zones = ConfigMigratorTest.migrateAndRead(root);
        final Zone spawn = zones.stream().filter(z -> z.name().equals("spawn")).findFirst().orElseThrow();
        final Zone creative = zones.stream().filter(z -> z.name().equals("spawn_creative_world")).findFirst().orElseThrow();
        assertEquals(new HungerSettings(true, HungerMode.DISTANCE, 6, 1, 25.0, 2, 30, 1), spawn.hungerOverride());
        assertFalse(creative.hungerOverride().enabled());
        assertEquals(25.0, root.get("zones.spawn.hunger_consumption.distance.blocks_per_point"));
        assertEquals(ZoneComments.DEFAULTS.get("hunger_consumption"), root.section("zones").section("spawn").comments("hunger_consumption"));
    }

    @Test
    void addsMissingGlobalsToAlready16Config() {
        final MapConfigView root = TestYaml.parse("""
                language: de
                zones:
                  spawn: {world: world, radius: 10}
                """);
        final ConfigMigrator.Result r = ConfigMigrator.migrate(root);
        assertTrue(r.changed());
        assertFalse(r.migratedWorlds());
        assertEquals(3, r.messages().size());
        assertEquals(List.of("language", "zones", "boost_display", "menus", "permission_tiers"), List.copyOf(root.keys()));
        assertFalse(ConfigMigrator.migrate(root).changed());
    }

    @Test
    void bothSectionsLeavesWorldsAlone() {
        final MapConfigView root = TestYaml.parse("""
                boost_display: actionbar
                menus: {mode: auto}
                permission_tiers: {}
                worlds:
                  world: {radius: 10}
                zones:
                  spawn: {world: world, radius: 10}
                """);
        final ConfigMigrator.Result r = ConfigMigrator.migrate(root);
        assertFalse(r.changed());
        assertEquals("migration_worlds_ignored", r.messages().get(0).key());
        assertNotNull(root.section("worlds"));
    }

    @Test
    void worldOrder() {
        assertEquals(List.of("world", "a", "b"), ConfigMigrator.worldOrder(List.of("a", "world", "b")));
        assertEquals(List.of("minecraft:overworld", "a"), ConfigMigrator.worldOrder(List.of("a", "minecraft:overworld")));
        assertEquals(List.of("b", "a"), ConfigMigrator.worldOrder(List.of("b", "a")));
    }

    @Test
    void hungerCommentUpdated() {
        final MapConfigView root = TestYaml.resource("fixtures/v15/default.yml");
        root.setComments("hunger_consumption", List.of("Hunger consumption settings (global defaults, can be overridden per-world)"));
        ConfigMigrator.migrate(root);
        assertEquals(List.of("Hunger consumption settings (global defaults, can be overridden per-zone)"), root.comments("hunger_consumption"));
    }

    /** 1.5 SpawnElytra#isInSpawnArea rectangle branch: a 3D box on raw doubles, inclusive. */
    private static boolean legacyBox(final ConfigView w, final double x, final double y, final double z) {
        final double x1 = w.getDouble("spawn_area.x", 0), y1 = w.getDouble("spawn_area.y", 0), z1 = w.getDouble("spawn_area.z", 0);
        final double x2 = w.getDouble("spawn_area.x2", 0), y2 = w.getDouble("spawn_area.y2", 0), z2 = w.getDouble("spawn_area.z2", 0);
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    private static double[] probes(final double a, final double b, final Random rnd) {
        final double lo = Math.min(a, b), hi = Math.max(a, b);
        final double[] out = new double[14];
        final double[] fixed = {lo, hi, Math.nextDown(lo), Math.nextUp(lo), Math.nextDown(hi), Math.nextUp(hi),
                Math.floor(lo), Math.ceil(hi), (lo + hi) / 2, lo - 1};
        System.arraycopy(fixed, 0, out, 0, fixed.length);
        for (int i = fixed.length; i < out.length; i++) {
            out[i] = lo - 3 + rnd.nextDouble() * (hi - lo + 6);
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(strings = {"advanced_rectangular", "setup_wizard_rectangular", "multiple_worlds"})
    void migratedRectanglesMatchThe15BoxCheckExactly(final String fixture) {
        final MapConfigView original = TestYaml.resource("fixtures/v15/" + fixture + ".yml");
        final MapConfigView root = TestYaml.resource("fixtures/v15/" + fixture + ".yml");
        final ConfigMigrator.Result r = ConfigMigrator.migrate(root);
        final MapConfigView reparsed = TestYaml.parse(TestYaml.dump(root));
        final List<Zone> zones = ZoneCodec.readAll(reparsed, HungerSettings.DEFAULTS).zones();
        final ConfigView worlds = original.section("worlds");
        final Random rnd = new Random(fixture.hashCode());
        int checked = 0;
        for (final String world : ConfigMigrator.worldOrder(worlds.keys())) {
            final ConfigView w = worlds.section(world);
            final String zoneName = r.createdZones().get(ConfigMigrator.worldOrder(worlds.keys()).indexOf(world));
            final Zone zone = zones.stream().filter(z -> z.name().equals(zoneName)).findFirst().orElseThrow();
            if (!(zone.shape() instanceof RectangleShape)) {
                continue;
            }
            for (final double x : ConfigMigratorTest.probes(w.getDouble("spawn_area.x", 0), w.getDouble("spawn_area.x2", 0), rnd)) {
                for (final double y : ConfigMigratorTest.probes(w.getDouble("spawn_area.y", 0), w.getDouble("spawn_area.y2", 0), rnd)) {
                    for (final double z : ConfigMigratorTest.probes(w.getDouble("spawn_area.z", 0), w.getDouble("spawn_area.z2", 0), rnd)) {
                        assertEquals(ConfigMigratorTest.legacyBox(w, x, y, z), zone.contains(x, y, z, null),
                                () -> zoneName + " @ " + x + "," + y + "," + z);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked >= 14 * 14 * 14, "no rectangle checked");
    }

    @Test
    void setupWizardDoublesSurviveExactly() {
        final MapConfigView root = TestYaml.resource("fixtures/v15/setup_wizard_rectangular.yml");
        ConfigMigrator.migrate(root);
        final Zone z = ZoneCodec.readAll(TestYaml.parse(TestYaml.dump(root)), HungerSettings.DEFAULTS).zones().get(0);
        assertEquals(new RectangleShape(new Vec2(-123.69999998807907, 45.30000001192093), new Vec2(-40, 120.8125)), z.shape());
        assertEquals(63.0, z.minHeight());
        assertEquals(71.5, z.maxHeight());
        assertEquals(71.5, root.get("zones.spawn.max_y"));
        assertEquals(63, root.get("zones.spawn.min_y"));
    }

    @Test
    void rectangleWithoutYKeepsUnlimitedHeight() {
        final MapConfigView root = TestYaml.parse("""
                worlds:
                  world:
                    spawn_area: {mode: advanced, area_type: rectangular, x: 0, z: 0, x2: 10, z2: 10}
                """);
        ConfigMigrator.migrate(root);
        assertNull(root.get("zones.spawn.min_y"));
        assertNull(root.get("zones.spawn.max_y"));
    }
}
