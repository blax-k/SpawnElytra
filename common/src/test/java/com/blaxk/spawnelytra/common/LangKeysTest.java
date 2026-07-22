/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common;

import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.config.MenusMode;
import com.blaxk.spawnelytra.common.editor.EditorTool;
import com.blaxk.spawnelytra.common.editor.ZoneDraft;
import com.blaxk.spawnelytra.common.geom.Geometry;
import com.blaxk.spawnelytra.common.keys.GlobalKeyTable;
import com.blaxk.spawnelytra.common.keys.KeySpec;
import com.blaxk.spawnelytra.common.keys.KeyType;
import com.blaxk.spawnelytra.common.keys.ZoneKeyTable;
import com.blaxk.spawnelytra.common.screen.OptionLabels;
import com.blaxk.spawnelytra.common.screen.Screens;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every lang key the core can produce exists in all five lang files, and the files have identical key sets. */
class LangKeysTest {

    private static final List<String> LANGS = List.of("en", "de", "es", "fr", "pl");
    private static final Pattern TAG_PLACEHOLDER = Pattern.compile("<([a-z_]+)>");

    private static MapConfigView lang(final String code) {
        return TestYaml.file("src/main/resources/lang/" + code + ".yml");
    }

    @Test
    void allLanguagesHaveTheSameKeysAndPlaceholders() {
        final MapConfigView en = LangKeysTest.lang("en");
        for (final String code : LangKeysTest.LANGS) {
            final MapConfigView l = LangKeysTest.lang(code);
            assertEquals(new TreeSet<>(en.keys()), new TreeSet<>(l.keys()), code);
            assertEquals("1.6", String.valueOf(l.get("lang-version")), code);
            for (final String key : en.keys()) {
                if (key.equals("lang-version")) {
                    continue;
                }
                assertEquals(LangKeysTest.placeholders(en.getString(key, "")), LangKeysTest.placeholders(l.getString(key, "")),
                        code + ":" + key);
            }
        }
    }

    private static Set<String> placeholders(final String s) {
        final Set<String> out = new TreeSet<>();
        final Matcher m = LangKeysTest.TAG_PLACEHOLDER.matcher(s);
        while (m.find()) {
            if (!m.group(1).equals("br") && !m.group(1).equals("bold")) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    void everyKeyProducedByTheCoreExists() throws IOException {
        final Set<String> needed = new TreeSet<>();
        // literal keys in the core sources
        final Pattern literal = Pattern.compile("\"((?:zone|editor|menu|tier|stats|toggle|bossbar|info|migration|state)_[a-z0-9_]+)\"");
        final Path src = TestYaml.project().resolve("common/src/main/java");
        try (Stream<Path> files = Files.walk(src)) {
            for (final Path p : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("SpawnElytraCore.java")).toList()) {
                final Matcher m = literal.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    final String k = m.group(1);
                    if (!k.endsWith("_")) {
                        needed.add(k);
                    }
                }
            }
        }
        // computed keys
        for (final ShapeType t : ShapeType.values()) {
            needed.add(t.langKey());
            needed.add("zone_dims_" + t.id());
            for (final EditorTool tool : EditorTool.values()) {
                needed.add(tool.nameKey(t));
                needed.add(tool.loreKey(t));
                needed.add(tool.hintKey(t));
            }
        }
        for (final ActivationMode m : ActivationMode.values()) {
            needed.add(m.langKey());
        }
        for (final BoostDirection d : BoostDirection.values()) {
            needed.add(d.langKey());
        }
        for (final BoostDisplay d : BoostDisplay.values()) {
            needed.add(d.langKey());
        }
        for (final HungerMode m : HungerMode.values()) {
            needed.add(m.langKey());
        }
        for (final MenusMode m : MenusMode.values()) {
            needed.add(m.langKey());
        }
        for (final Geometry.PolygonProblem p : Geometry.PolygonProblem.values()) {
            needed.add(p.langKey());
        }
        for (final ZoneDraft.HeightBound b : ZoneDraft.HeightBound.values()) {
            needed.add(b.langKey());
        }
        for (final Screens.Context c : Screens.Context.values()) {
            needed.add("menu_hover_save_" + c.id());
        }
        final List<KeySpec> specs = new ArrayList<>(ZoneKeyTable.specs());
        specs.addAll(GlobalKeyTable.specs());
        for (final KeySpec s : specs) {
            needed.add(s.labelKey());
            if (s.type() == KeyType.OPTION) {
                for (final String o : s.options()) {
                    final Msg label = OptionLabels.of(s.key(), o);
                    if (!label.isLiteral()) {
                        needed.add(label.key());
                    }
                }
            }
        }
        needed.add("state_on");
        needed.add("state_off");
        needed.add("toggle_hover");
        needed.add("creative_mode_elytra_disabled");

        final Set<String> en = new HashSet<>(LangKeysTest.lang("en").keys());
        final List<String> missing = needed.stream().filter(k -> !en.contains(k)).toList();
        assertTrue(missing.isEmpty(), () -> "missing lang keys: " + missing);
    }
}
