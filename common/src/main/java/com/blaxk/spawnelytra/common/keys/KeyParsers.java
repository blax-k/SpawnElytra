/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;

import java.util.List;
import java.util.Locale;

/** Shared value parsing for the key tables. Results are {@link SetResult}s carrying {@code zone_set_*} errors. */
public enum KeyParsers {
    ;

    /** Parses a boolean ({@code toggle} flips {@code current}). */
    public static SetResult<Boolean> bool(final KeySpec spec, final String raw, final boolean current) {
        final String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "on", "yes", "1", "enable", "enabled" -> SetResult.ok(true);
            case "false", "off", "no", "0", "disable", "disabled" -> SetResult.ok(false);
            case "toggle" -> SetResult.ok(!current);
            default -> SetResult.fail("zone_set_invalid_boolean", "key", spec.key(), "value", String.valueOf(raw));
        };
    }

    /** Parses a number within the spec's range. INT keys reject decimals. */
    public static SetResult<Double> number(final KeySpec spec, final String raw) {
        final double v;
        try {
            v = Double.parseDouble(raw == null ? "" : raw.trim());
        } catch (final NumberFormatException e) {
            return SetResult.fail("zone_set_invalid_number", "key", spec.key(), "value", String.valueOf(raw));
        }
        if (!Double.isFinite(v)) {
            return SetResult.fail("zone_set_invalid_number", "key", spec.key(), "value", String.valueOf(raw));
        }
        if ((spec.type() == KeyType.INT || spec.type() == KeyType.OPTIONAL_INT) && v != Math.rint(v)) {
            return SetResult.fail("zone_set_invalid_integer", "key", spec.key(), "value", String.valueOf(raw));
        }
        if (!Double.isNaN(spec.min()) && (v < spec.min() || v > spec.max())) {
            return SetResult.fail("zone_set_out_of_range", "key", spec.key(), "min", ConfigNumbers.format(spec.min()),
                    "max", ConfigNumbers.format(spec.max()));
        }
        return SetResult.ok(v);
    }

    /** {@code true} for the "remove this optional value" words. */
    public static boolean isNone(final String raw) {
        final String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("none") || s.equals("unset") || s.equals("null") || s.equals("-") || s.equals("inherit");
    }

    /** Parses an option ({@code next} cycles from {@code current}). Matching is case-insensitive. */
    public static SetResult<String> option(final KeySpec spec, final String raw, final String current) {
        final String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        final List<String> options = spec.options();
        if (s.equals("next")) {
            final int i = options.indexOf(current);
            return SetResult.ok(options.get((i + 1) % options.size()));
        }
        for (final String o : options) {
            if (o.equalsIgnoreCase(s)) {
                return SetResult.ok(o);
            }
        }
        return SetResult.fail("zone_set_invalid_option", "key", spec.key(), "value", String.valueOf(raw),
                "options", String.join(", ", options));
    }
}
