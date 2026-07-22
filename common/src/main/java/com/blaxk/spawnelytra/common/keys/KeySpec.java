/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import java.util.List;

/**
 * Description of one settable key ({@code /se zone set} or {@code /se global set}): type, range, steps and options.
 * Used for validation, tab completion and to build menu fields.
 *
 * @param key       dotted key ({@code boost.strength})
 * @param type      value type
 * @param min       inclusive minimum (numeric types; {@code NaN} otherwise)
 * @param max       inclusive maximum (numeric types; {@code NaN} otherwise)
 * @param step      small step for menus ({@code [-] [+]}, dialog slider step)
 * @param largeStep large step for chat menus ({@code [--] [++]})
 * @param options   allowed values for {@link KeyType#OPTION} (and suggestions for others), else empty
 * @param labelKey  lang key of the field label ({@code menu_field_*})
 */
public record KeySpec(String key, KeyType type, double min, double max, double step, double largeStep,
                      List<String> options, String labelKey) {

    public KeySpec {
        options = List.copyOf(options);
    }

    public static KeySpec of(final String key, final KeyType type) {
        return new KeySpec(key, type, Double.NaN, Double.NaN, Double.NaN, Double.NaN, List.of(), KeySpec.label(key));
    }

    public static KeySpec number(final String key, final KeyType type, final double min, final double max,
                                 final double step, final double largeStep) {
        return new KeySpec(key, type, min, max, step, largeStep, List.of(), KeySpec.label(key));
    }

    public static KeySpec option(final String key, final List<String> options) {
        return new KeySpec(key, KeyType.OPTION, Double.NaN, Double.NaN, Double.NaN, Double.NaN, options, KeySpec.label(key));
    }

    /** {@code boost.strength → menu_field_boost_strength}. */
    public static String label(final String key) {
        return "menu_field_" + key.replace('.', '_');
    }

    public boolean isNumeric() {
        return this.type == KeyType.INT || this.type == KeyType.DOUBLE || this.type == KeyType.OPTIONAL_INT
                || this.type == KeyType.OPTIONAL_DOUBLE;
    }

    /** Value suggestions for tab completion. */
    public List<String> suggestions() {
        return switch (this.type) {
            case BOOLEAN -> List.of("true", "false", "toggle");
            case OPTION -> {
                final java.util.ArrayList<String> l = new java.util.ArrayList<>(this.options);
                l.add("next");
                yield List.copyOf(l);
            }
            case OPTIONAL_INT, OPTIONAL_DOUBLE -> List.of("none");
            case CENTER -> List.of("world_spawn");
            default -> this.options;
        };
    }
}
