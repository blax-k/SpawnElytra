/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.stats;

import java.util.Locale;

/** Formatting of stats for placeholders and {@code /se stats} (locale-independent, {@code .} decimal separator). */
public enum StatsFormat {
    ;

    /** {@code 1234.56 → "1234.6"} (always one decimal). */
    public static String blocks(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Whole seconds, floored: {@code 754.9 → "754"}. */
    public static String seconds(final double value) {
        return Long.toString((long) Math.floor(Math.max(0, value)));
    }

    /** {@code "1h 2m 3s"}, {@code "2m 3s"}, {@code "3s"}. */
    public static String duration(final double seconds) {
        long s = (long) Math.floor(Math.max(0, seconds));
        final long h = s / 3600;
        s %= 3600;
        final long m = s / 60;
        s %= 60;
        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    /**
     * Placeholder value for {@code flights | distance | boosts | glide_time | longest_flight}
     * ({@code glide_time} in whole seconds); {@code null} for unknown names.
     */
    public static String placeholder(final PlayerStats stats, final String name) {
        return switch (name) {
            case "flights" -> Long.toString(stats.flights());
            case "distance" -> StatsFormat.blocks(stats.distance());
            case "boosts" -> Long.toString(stats.boosts());
            case "glide_time" -> StatsFormat.seconds(stats.glideTimeSeconds());
            case "longest_flight" -> StatsFormat.blocks(stats.longestFlight());
            default -> null;
        };
    }
}
