/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.config.GlobalSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Keys of the Global settings screen (spec §7.2 #3), settable with {@code /se global set <key> <value>}.
 * Keys are the config paths. {@link #apply} writes into the root {@link ConfigView}; the platform then saves the
 * file and applies the change live (language/style reload messages exactly like 1.5's {@code /se set language}).
 */
public enum GlobalKeyTable {
    ;

    private static final Map<String, KeySpec> TABLE = new LinkedHashMap<>();
    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    private static void put(final KeySpec spec, final Object def) {
        GlobalKeyTable.TABLE.put(spec.key(), spec);
        GlobalKeyTable.DEFAULTS.put(spec.key(), def);
    }

    static {
        GlobalKeyTable.put(KeySpec.option("language", GlobalSettings.LANGUAGES), "en");
        GlobalKeyTable.put(KeySpec.option("messages.style", GlobalSettings.STYLES), "classic");
        GlobalKeyTable.put(KeySpec.option("boost_display", List.of("actionbar", "bossbar", "none")), "actionbar");
        GlobalKeyTable.put(KeySpec.option("menus.mode", List.of("auto", "dialog", "chat")), "auto");
        GlobalKeyTable.put(KeySpec.of("game_modes.disable_in_creative", KeyType.BOOLEAN), true);
        GlobalKeyTable.put(KeySpec.of("game_modes.disable_in_adventure", KeyType.BOOLEAN), false);
        GlobalKeyTable.put(KeySpec.of("fireworks.disable_in_spawn_elytra", KeyType.BOOLEAN), false);
        GlobalKeyTable.put(KeySpec.of("bedrock.enabled", KeyType.BOOLEAN), true);
        GlobalKeyTable.put(KeySpec.of("messages.show_press_to_boost", KeyType.BOOLEAN), true);
        GlobalKeyTable.put(KeySpec.of("messages.show_boost_activated", KeyType.BOOLEAN), true);
        GlobalKeyTable.put(KeySpec.of("messages.show_creative_disabled", KeyType.BOOLEAN), false);
        GlobalKeyTable.put(KeySpec.of("hunger_consumption.enabled", KeyType.BOOLEAN), false);
        GlobalKeyTable.put(KeySpec.option("hunger_consumption.mode", List.of("activation", "distance", "time")), "activation");
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.minimum_food_level", KeyType.INT, 0, 20, 1, 5), 0);
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.activation.hunger_cost", KeyType.INT, 0, 20, 1, 5), 1);
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.distance.blocks_per_point", KeyType.DOUBLE, 1, 10000, 5, 50), 50.0);
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.distance.hunger_cost", KeyType.INT, 0, 20, 1, 5), 1);
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.time.seconds_per_point", KeyType.DOUBLE, 1, 3600, 5, 60), 30);
        GlobalKeyTable.put(KeySpec.number("hunger_consumption.time.hunger_cost", KeyType.INT, 0, 20, 1, 5), 1);
    }

    public static List<String> keys() {
        return List.copyOf(GlobalKeyTable.TABLE.keySet());
    }

    public static List<KeySpec> specs() {
        return new ArrayList<>(GlobalKeyTable.TABLE.values());
    }

    public static Optional<KeySpec> spec(final String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(GlobalKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT)));
    }

    /** Current value (command syntax), falling back to the 1.6 default when the key is missing. */
    public static String currentValue(final ConfigView root, final String key) {
        final KeySpec spec = GlobalKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT));
        if (spec == null) {
            return "";
        }
        final Object v = root.get(spec.key());
        final Object val = v == null || v instanceof ConfigView ? GlobalKeyTable.DEFAULTS.get(spec.key()) : v;
        return val instanceof final Number n ? ConfigNumbers.format(n.doubleValue()) : String.valueOf(val);
    }

    /**
     * Validates and writes the value into {@code root}. Returns the written (canonical) value on success.
     * Unknown key → {@code zone_set_unknown_key}.
     */
    public static SetResult<Object> apply(final ConfigView root, final String key, final String rawValue) {
        final KeySpec spec = key == null ? null : GlobalKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT));
        if (spec == null) {
            return SetResult.fail("zone_set_unknown_key", "key", String.valueOf(key));
        }
        final String current = GlobalKeyTable.currentValue(root, spec.key());
        final Object value;
        switch (spec.type()) {
            case BOOLEAN -> {
                final SetResult<Boolean> r = KeyParsers.bool(spec, rawValue, Boolean.parseBoolean(current));
                if (!r.ok()) {
                    return SetResult.fail(r.error());
                }
                value = r.value();
            }
            case OPTION -> {
                final SetResult<String> r = KeyParsers.option(spec, rawValue, current);
                if (!r.ok()) {
                    return SetResult.fail(r.error());
                }
                value = r.value();
            }
            case INT -> {
                final SetResult<Double> r = KeyParsers.number(spec, rawValue);
                if (!r.ok()) {
                    return SetResult.fail(r.error());
                }
                value = r.value().intValue();
            }
            case DOUBLE -> {
                final SetResult<Double> r = KeyParsers.number(spec, rawValue);
                if (!r.ok()) {
                    return SetResult.fail(r.error());
                }
                value = ConfigNumbers.canonical(r.value());
            }
            default -> {
                return SetResult.fail("zone_set_unknown_key", "key", spec.key());
            }
        }
        root.set(spec.key(), value);
        return SetResult.ok(value);
    }
}
