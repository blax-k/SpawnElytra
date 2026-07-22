/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.TestYaml;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.keys.GlobalKeyTable;
import com.blaxk.spawnelytra.common.keys.KeyContext;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.keys.ZoneKeyTable;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.Zone;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreensTest {

    private static final Zone SPAWN = Zone.createDefault("spawn", "world", CircleShape.worldSpawn(100));
    private static final Zone ARENA = Zone.createDefault("arena", "world_nether", CircleShape.worldSpawn(20)).withEnabled(false);

    @Test
    void overviewRowsButtonsAndCommands() {
        final Screen s = Screens.overview(List.of(ScreensTest.ARENA, ScreensTest.SPAWN));
        assertEquals(Screen.ScreenId.OVERVIEW, s.id());
        assertEquals(2, s.body().size());
        final Element.Row arena = (Element.Row) s.body().get(0);
        assertEquals("arena", arena.id());
        assertEquals(List.of("/se zone settings arena", "/se zone edit arena", "/se zone tp arena", "/se zone enable arena",
                "/se zone delete arena"), arena.buttons().stream().map(b -> b.action().command()).toList());
        final Element.Row spawn = (Element.Row) s.body().get(1);
        assertEquals("/se zone disable spawn", spawn.buttons().get(3).action().command());
        assertEquals("menu_overview_row", spawn.text().key());

        final List<Button> chat = s.buttons(false);
        assertEquals(List.of("/se zone create ", "/se global", "/se settings"), chat.stream().map(b -> b.action().command()).toList());
        assertTrue(chat.get(0).action().suggest());
        final List<Button> dialog = s.buttons(true);
        assertEquals(List.of(ActionIds.ZONE_CREATE_FORM, ActionIds.GLOBAL_SETTINGS, ActionIds.LANGUAGE_STYLE, ActionIds.CLOSE),
                dialog.stream().map(b -> b.action().id()).toList());
        assertNull(dialog.get(3).action().command());
        assertEquals("menu_overview_empty", ((Element.Text) Screens.overview(List.of()).body().get(0)).text().key());
    }

    @Test
    void zoneSettingsFieldsAreZoneKeys() {
        final Screen s = Screens.zoneSettings(ScreensTest.SPAWN, Screens.Context.OVERVIEW, KeyContext.lenient());
        assertEquals(Screens.ZONE_SETTINGS_KEYS.size(), s.body().size());
        for (final Element e : s.body()) {
            final Field f = (Field) e;
            assertTrue(ZoneKeyTable.spec(f.id()).isPresent(), f.id());
        }
        assertInstanceOf(Field.ReadOnly.class, s.body().get(1));
        assertEquals(Screens.ZONE_SETTINGS_KEYS.size() - 1, s.fields().size());
        final Field.Number strength = (Field.Number) s.fields().stream().filter(f -> f.id().equals("boost.strength")).findFirst().orElseThrow();
        assertEquals(4, strength.current());
        assertEquals(0.5, strength.min());
        assertEquals(10, strength.max());
        assertEquals(0.5, strength.step());
        assertFalse(strength.integer());
        assertEquals(List.of("/se zone set spawn boost.strength 2", "/se zone set spawn boost.strength 3.5",
                        "/se zone set spawn boost.strength 4.5", "/se zone set spawn boost.strength 6"),
                strength.chatControls().stream().map(b -> b.action().command()).toList());
        final Field.Number maxBoosts = (Field.Number) s.fields().stream().filter(f -> f.id().equals("boost.max_boosts")).findFirst().orElseThrow();
        assertEquals(List.of("/se zone set spawn boost.max_boosts 2", "/se zone set spawn boost.max_boosts 6"),
                maxBoosts.chatControls().stream().map(b -> b.action().command()).toList()); // lower end clamped away
        final Field.Choice mode = (Field.Choice) s.fields().stream().filter(f -> f.id().equals("activation_mode")).findFirst().orElseThrow();
        assertEquals("double_jump", mode.selected());
        assertEquals("zone_mode_double_jump", mode.selectedLabel().key());
        assertEquals("/se zone set spawn activation_mode next", mode.chatControls().get(0).action().command());
        final Field.Bool enabled = (Field.Bool) s.fields().stream().filter(f -> f.id().equals("enabled")).findFirst().orElseThrow();
        assertEquals("/se zone set spawn enabled toggle", enabled.chatControls().get(0).action().command());
        final Field.TextInput name = (Field.TextInput) s.fields().get(0);
        assertTrue(name.chatControls().get(0).action().suggest());
        assertEquals("/se zone set spawn name ", name.chatControls().get(0).action().command());
        final Field.Choice hunger = (Field.Choice) s.fields().stream().filter(f -> f.id().equals("hunger_consumption")).findFirst().orElseThrow();
        assertEquals("inherit", hunger.selected());
        assertEquals("menu_option_inherit", hunger.selectedLabel().key());

        // dialog save button carries zone + context; editor context has no back button
        final Button save = s.buttons(true).get(0);
        assertEquals(ActionIds.ZONE_SETTINGS_SAVE, save.action().id());
        assertEquals(Map.of("zone", "spawn", "context", "overview"), save.action().payload());
        assertTrue(ActionIds.isFormSubmit(save.action().id()));
        final Screen editor = Screens.zoneSettings(ScreensTest.SPAWN, Screens.Context.EDITOR, KeyContext.lenient());
        assertEquals(List.of(ActionIds.ZONE_SETTINGS_SAVE, ActionIds.CLOSE), editor.buttons(true).stream().map(b -> b.action().id()).toList());
        assertTrue(editor.buttons(false).isEmpty());
    }

    @Test
    void applyZoneFormChangesOnlyWhatDiffers() {
        final Screen s = Screens.zoneSettings(ScreensTest.SPAWN, Screens.Context.OVERVIEW, KeyContext.lenient());
        final Map<String, String> inputs = new HashMap<>();
        for (final Field f : s.fields()) {
            inputs.put(f.id(), f.value());
        }
        // a dialog returns floats for number inputs
        inputs.put("boost.strength", "4.0");
        inputs.put("priority", "0.0");
        assertEquals(ScreensTest.SPAWN, Screens.applyZoneForm(ScreensTest.SPAWN, inputs, KeyContext.lenient()).value());

        inputs.put("name", "hub");
        inputs.put("activation_mode", "f_key");
        inputs.put("boost.max_boosts", "3.0");
        inputs.put("hunger_consumption", "distance");
        inputs.put("hunger_consumption.distance.hunger_cost", "4");
        final SetResult<Zone> r = Screens.applyZoneForm(ScreensTest.SPAWN, inputs, KeyContext.lenient());
        assertTrue(r.ok(), () -> String.valueOf(r.error()));
        assertEquals("hub", r.value().name());
        assertEquals(ActivationMode.F_KEY, r.value().activationMode());
        assertEquals(3, r.value().boost().maxBoosts());
        assertEquals(HungerMode.DISTANCE, r.value().hungerOverride().mode());
        assertEquals(4, r.value().hungerOverride().distanceCost());

        inputs.put("boost.strength", "99");
        assertEquals("zone_set_out_of_range", Screens.applyZoneForm(ScreensTest.SPAWN, inputs, KeyContext.lenient()).error().key());
    }

    @Test
    void globalSettingsScreenAndForm() {
        final MapConfigView root = TestYaml.file("src/main/resources/config.yml");
        final Screen s = Screens.globalSettings(root);
        assertEquals(GlobalKeyTable.keys().size(), s.fields().size());
        assertTrue(s.fields().stream().allMatch(f -> f.target().scope() == Field.Scope.GLOBAL));
        final Field lang = s.fields().get(0);
        assertEquals("/se global set language next", lang.chatControls().get(0).action().command());
        final Map<String, String> inputs = new HashMap<>();
        s.fields().forEach(f -> inputs.put(f.id(), f.value()));
        inputs.put("language", "fr");
        inputs.put("hunger_consumption.time.seconds_per_point", "45.0");
        inputs.put("menus.mode", "nonsense");
        final List<Msg> errors = Screens.applyGlobalForm(root, inputs);
        assertEquals(1, errors.size());
        assertEquals("fr", root.get("language"));
        assertEquals(45, root.get("hunger_consumption.time.seconds_per_point"));
        assertEquals("auto", root.get("menus.mode"));
    }

    @Test
    void otherScreens() {
        final Screen nz = Screens.newZone();
        assertEquals(List.of("name", "shape"), nz.fields().stream().map(Field::id).toList());
        assertTrue(nz.fields().get(0).chatControls().isEmpty());
        assertNull(nz.fields().get(0).setAction("x"));
        assertEquals(ActionIds.ZONE_CREATE, nz.buttons(true).get(0).action().id());
        assertEquals("/se zone create arena polygon",
                new Action(ActionIds.ZONE_CREATE, Map.of("name", "arena", "shape", "polygon"), false).command());

        final Screen del = Screens.deleteConfirm(ScreensTest.ARENA);
        assertEquals("/se zone delete arena confirm", del.buttons(false).get(0).action().command());
        assertEquals("/se", del.exit().action().command());

        final Screen st = Screens.stats("Steve", new PlayerStats(3, 12.34, 2, 75, 9));
        assertEquals(5, st.body().size());
        assertEquals("1m 15s", ((Element.Text) st.body().get(3)).text().args().get("value"));
        assertEquals("Steve", st.title().args().get("player"));
        assertNotNull(st.exit());
    }

    @Test
    void actionIds() {
        assertTrue(ActionIds.isKnown(ActionIds.ZONE_SET));
        assertFalse(ActionIds.isKnown("spawnelytra:hack"));
        assertFalse(ActionIds.isKnown(null));
        assertTrue(ActionIds.requiresAdmin(Action.of(ActionIds.ZONE_DELETE_CONFIRM, "zone", "x")));
        assertFalse(ActionIds.requiresAdmin(Action.of(ActionIds.CLOSE)));
        assertEquals("/se global set boost_display bossbar",
                Action.of(ActionIds.GLOBAL_SET, "key", "boost_display", "value", "bossbar").command());
        assertEquals("/se stats Alex", Action.of(ActionIds.STATS, "player", "Alex").command());
        for (final String field : List.of("OVERVIEW", "ZONE_SETTINGS", "ZONE_EDIT")) {
            assertNotNull(field);
        }
    }
}
