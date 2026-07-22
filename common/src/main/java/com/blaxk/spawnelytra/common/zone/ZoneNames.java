/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/** Zone (and tier) name rules: {@code ^[a-z0-9_-]{1,32}$}, unique case-insensitively, stored lowercase. */
public enum ZoneNames {
    ;

    public static final int MAX_LENGTH = 32;
    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    /** Lowercases (ROOT) and trims user input. {@code null} → {@code ""}. */
    public static String normalize(final String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code true} if the already normalized name matches the pattern. */
    public static boolean isValid(final String name) {
        return name != null && ZoneNames.PATTERN.matcher(name).matches();
    }

    /**
     * Validates user input (normalizing first). Returns {@code null} if OK, else a lang key:
     * {@code zone_error_invalid_name} or {@code zone_error_name_taken}.
     *
     * @param existing    existing zone names (any case)
     * @param ignoreName  a name that may be "taken" (the zone being renamed), or {@code null}
     */
    public static String check(final String input, final Collection<String> existing, final String ignoreName) {
        final String name = ZoneNames.normalize(input);
        if (!ZoneNames.isValid(name)) {
            return "zone_error_invalid_name";
        }
        for (final String e : existing) {
            if (e.equalsIgnoreCase(name) && (ignoreName == null || !ignoreName.equalsIgnoreCase(name))) {
                return "zone_error_name_taken";
            }
        }
        return null;
    }

    /**
     * Turns an arbitrary world name into a name-safe fragment: lowercase, any char outside {@code [a-z0-9_-]}
     * becomes {@code _}, trimmed to {@code maxLength}. Empty input → {@code "world"}.
     */
    public static String sanitize(final String input, final int maxLength) {
        final String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            final char c = lower.charAt(i);
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' ? c : '_');
        }
        String s = sb.toString();
        if (s.isEmpty()) {
            s = "world";
        }
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    /**
     * Returns {@code base} if free, else {@code base_2}, {@code base_3}, ... (trimming base so the result stays
     * ≤ 32 chars).
     */
    public static String unique(final String base, final Collection<String> taken) {
        if (!ZoneNames.containsIgnoreCase(taken, base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            final String suffix = "_" + i;
            final String trimmed = base.length() + suffix.length() > ZoneNames.MAX_LENGTH
                    ? base.substring(0, ZoneNames.MAX_LENGTH - suffix.length()) : base;
            final String candidate = trimmed + suffix;
            if (!ZoneNames.containsIgnoreCase(taken, candidate)) {
                return candidate;
            }
        }
    }

    private static boolean containsIgnoreCase(final Collection<String> c, final String s) {
        for (final String e : c) {
            if (e.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
