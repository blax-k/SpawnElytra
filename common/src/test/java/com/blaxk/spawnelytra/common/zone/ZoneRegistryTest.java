/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneRegistryTest {

    private static Zone circle(final String name, final String world, final double x, final double z, final double r, final int prio) {
        return Zone.createDefault(name, world, new CircleShape(new Vec2(x, z), r)).withPriority(prio);
    }

    @Test
    void highestPriorityWins() {
        final ZoneRegistry reg = new ZoneRegistry(List.of(
                circle("big", "world", 0, 0, 100, 0),
                circle("arena", "world", 10, 0, 20, 5),
                circle("other", "world_nether", 0, 0, 100, 99)));
        assertEquals("arena", reg.resolve("world", 10, 64, 0, null).orElseThrow().name());
        assertEquals("big", reg.resolve("world", -50, 64, 0, null).orElseThrow().name());
        assertTrue(reg.resolve("world", 500, 64, 0, null).isEmpty());
        assertEquals("other", reg.resolve("world_nether", 0, 64, 0, null).orElseThrow().name());
        assertEquals(List.of("arena", "big"), reg.candidates("world", 10, 64, 0, null).stream().map(Zone::name).toList());
    }

    @Test
    void tiesBrokenByNameAscending() {
        final ZoneRegistry reg = new ZoneRegistry(List.of(
                circle("zulu", "world", 0, 0, 10, 1),
                circle("alpha", "world", 0, 0, 10, 1),
                circle("mike", "world", 0, 0, 10, 1)));
        assertEquals("alpha", reg.resolve("world", 0, 0, 0, null).orElseThrow().name());
    }

    @Test
    void disabledZonesAndHeightLimitsAreRespected() {
        final ZoneRegistry reg = new ZoneRegistry(List.of(
                circle("top", "world", 0, 0, 10, 10).withEnabled(false),
                circle("low", "world", 0, 0, 10, 5).withMinY(0).withMaxY(100),
                circle("base", "world", 0, 0, 10, 0)));
        assertEquals("low", reg.resolve("world", 0, 100, 0, null).orElseThrow().name());
        assertEquals("base", reg.resolve("world", 0, 100.01, 0, null).orElseThrow().name());
        assertEquals("base", reg.resolve("world", 0, -0.5, 0, null).orElseThrow().name());
        assertTrue(reg.hasEnabledZones("world"));
        assertFalse(reg.hasEnabledZones("world_the_end"));
    }

    @Test
    void worldSpawnLookupAndAliases() {
        final Map<String, String> alias = Map.of("minecraft:overworld", "world");
        final ZoneRegistry reg = new ZoneRegistry(List.of(
                Zone.createDefault("spawn", "world", CircleShape.worldSpawn(10))), w -> alias.getOrDefault(w, w));
        final ZoneRegistry.SpawnLookup spawns = w -> new Vec2(100, 100);
        assertTrue(reg.resolve("minecraft:overworld", 105, 64, 100, spawns).isPresent());
        assertTrue(reg.resolve("world", 105, 64, 100, spawns).isPresent());
        assertTrue(reg.resolve("world", 105, 64, 100, null).isEmpty());
        assertTrue(reg.resolve("world", 0, 64, 0, spawns).isEmpty());
    }

    @Test
    void lookupIsCaseInsensitiveAndCopiesAreImmutable() {
        final ZoneRegistry reg = new ZoneRegistry(List.of(circle("spawn", "world", 0, 0, 10, 0)));
        assertTrue(reg.get("SPAWN").isPresent());
        final ZoneRegistry withB = reg.with(circle("b", "world", 0, 0, 10, 3));
        assertEquals(1, reg.size());
        assertEquals(2, withB.size());
        assertEquals(List.of("b", "spawn"), withB.names());
        final ZoneRegistry renamed = withB.replace("b", withB.get("b").orElseThrow().withName("c"));
        assertEquals(List.of("c", "spawn"), renamed.names());
        assertEquals(1, renamed.without("c").size());
        assertTrue(ZoneRegistry.empty().isEmpty());
    }

    @Test
    void zoneNames() {
        assertTrue(ZoneNames.isValid("spawn"));
        assertTrue(ZoneNames.isValid("a-b_c-9"));
        assertFalse(ZoneNames.isValid("Spawn"));
        assertFalse(ZoneNames.isValid(""));
        assertFalse(ZoneNames.isValid("a".repeat(33)));
        assertTrue(ZoneNames.isValid("a".repeat(32)));
        assertFalse(ZoneNames.isValid("spawn zone"));
        assertEquals(null, ZoneNames.check(" Arena ", List.of("spawn"), null));
        assertEquals("zone_error_name_taken", ZoneNames.check("SPAWN", List.of("spawn"), null));
        assertEquals(null, ZoneNames.check("spawn", List.of("spawn"), "spawn"));
        assertEquals("zone_error_invalid_name", ZoneNames.check("sp@wn", List.of(), null));
        assertEquals("my_world_", ZoneNames.sanitize("My World!", 26));
        assertEquals("minecraft_the_nether", ZoneNames.sanitize("minecraft:the_nether", 26));
        assertEquals("x_2", ZoneNames.unique("x", List.of("x")));
        assertEquals("x_3", ZoneNames.unique("x", List.of("x", "X_2")));
        assertEquals(32, ZoneNames.unique("a".repeat(32), List.of("a".repeat(32))).length());
    }
}
