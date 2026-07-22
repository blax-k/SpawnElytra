/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common;

import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.migration.ConfigMigrator;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import com.blaxk.spawnelytra.common.zone.ZoneComments;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the core against the real Bukkit {@code YamlConfiguration} through the reference adapter from CORE-API.md:
 * migration output must equal the in-memory result, survive save/reload, and be idempotent byte-for-byte.
 */
class BukkitAdapterTest {

    /** The reference Paper adapter (same as in CORE-API.md). */
    record BukkitView(ConfigurationSection s) implements ConfigView {
        @Override
        public Set<String> keys() {
            return this.s.getKeys(false);
        }

        @Override
        public Object get(final String p) {
            final Object v = this.s.get(p, null);
            return v instanceof final ConfigurationSection c ? new BukkitView(c) : v;
        }

        @Override
        public void set(final String p, final Object v) {
            this.s.set(p, v);
        }

        @Override
        public ConfigView createSection(final String p) {
            return new BukkitView(this.s.createSection(p));
        }

        @Override
        public List<String> comments(final String p) {
            return this.s.getComments(p);
        }

        @Override
        public void setComments(final String p, final List<String> c) {
            this.s.setComments(p, c);
        }
    }

    private static YamlConfiguration load(final String text) throws InvalidConfigurationException {
        final YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    private static String fixture(final String name) throws IOException {
        try (InputStream in = BukkitAdapterTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "advanced_circular", "advanced_rectangular", "setup_wizard_rectangular", "multiple_worlds", "per_world_hunger"})
    void migrationOnBukkitMatchesInMemoryAndIsIdempotent(final String fixture) throws Exception {
        final String text = BukkitAdapterTest.fixture("fixtures/v15/" + fixture + ".yml");
        final YamlConfiguration bukkit = BukkitAdapterTest.load(text);
        final ConfigMigrator.Result r = ConfigMigrator.migrate(new BukkitView(bukkit));
        assertTrue(r.changed());

        final MapConfigView mem = TestYaml.parse(text);
        ConfigMigrator.migrate(mem);

        final String saved = bukkit.saveToString();
        assertEquals(mem.toMap(), TestYaml.parse(saved).toMap(), "Bukkit result differs from in-memory result");

        final YamlConfiguration reloaded = BukkitAdapterTest.load(saved);
        final ConfigView view = new BukkitView(reloaded);
        final ZoneCodec.ReadResult read = ZoneCodec.readAll(view, GlobalSettings.read(view).hunger());
        assertTrue(read.warnings().isEmpty(), read.warnings()::toString);
        assertEquals(r.createdZones(), read.zones().stream().map(Zone::name).sorted().toList().stream()
                .sorted((a, b) -> r.createdZones().indexOf(a) - r.createdZones().indexOf(b)).toList());
        assertEquals(ZoneComments.ZONES_SECTION.subList(1, ZoneComments.ZONES_SECTION.size()),
                reloaded.getComments("zones").subList(1, reloaded.getComments("zones").size()));

        assertFalse(ConfigMigrator.migrate(view).changed());
        assertEquals(saved, reloaded.saveToString());
    }

    @Test
    void shippedConfigCommentsMatchZoneComments() throws Exception {
        final String text = Files.readString(TestYaml.project().resolve("src/main/resources/config.yml"), StandardCharsets.UTF_8);
        final YamlConfiguration y = BukkitAdapterTest.load(text);
        final ConfigurationSection spawn = y.getConfigurationSection("zones.spawn");
        for (final Map.Entry<String, List<String>> e : ZoneComments.DEFAULTS.entrySet()) {
            if (spawn.contains(e.getKey(), true)) {
                assertEquals(e.getValue(), spawn.getComments(e.getKey()), e.getKey());
            }
        }
        assertEquals(ConfigMigrator.BOOST_DISPLAY_COMMENTS, y.getComments("boost_display"));
        assertEquals(ConfigMigrator.MENUS_MODE_COMMENTS, y.getComments("menus.mode"));
        assertEquals(ConfigMigrator.TIERS_COMMENTS, y.getComments("permission_tiers"));
        assertFalse(ConfigMigrator.migrate(new BukkitView(y)).changed());
        assertEquals(ZoneComments.ZONES_SECTION, y.getComments("zones"));
    }

    @Test
    void polygonAndRenameThroughBukkit() throws Exception {
        final YamlConfiguration y = BukkitAdapterTest.load("zones: {}\n");
        final BukkitView root = new BukkitView(y);
        final Zone poly = Zone.createDefault("poly", "world", new PolygonShape(List.of(new Vec2(0, 0), new Vec2(10.25, 0), new Vec2(0, 10))))
                .withMinY(5);
        ZoneCodec.write(root, poly, null, false);
        y.setComments("zones.poly.min_y", List.of("custom"));
        ZoneCodec.write(root, poly.withName("tri"), "poly", false);
        final YamlConfiguration back = BukkitAdapterTest.load(y.saveToString());
        final ConfigView v = new BukkitView(back);
        assertEquals(List.of(poly.withName("tri")), ZoneCodec.readAll(v, null).zones());
        assertEquals(List.of("custom"), back.getComments("zones.tri.min_y"));
        assertFalse(back.contains("zones.poly"));
    }
}
