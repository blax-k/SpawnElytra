/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Canonical number representation for values written to YAML, so Paper and Fabric emit byte-identical files:
 * integral values are written as {@code Integer} ({@code 4}, not {@code 4.0}), everything else as the exact
 * {@code Double} (never rounded, so migrated 1.5 coordinates keep identical containment). Step arithmetic noise is
 * avoided where it arises (menus format values with {@link #format(double)} before they are parsed again).
 */
public enum ConfigNumbers {
    ;

    /** Integral → {@code Integer} (when it fits), otherwise the unchanged {@code Double}. */
    public static Number canonical(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        if (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    /** Rounds half-up to the given number of decimals. */
    public static double round(final double value, final int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** Human/command formatting rounded to 4 decimals: {@code 4}, {@code 4.5}, {@code 0.25} (no trailing zeros/exponent). */
    public static String format(final double value) {
        final Number n = ConfigNumbers.canonical(ConfigNumbers.round(value, 4));
        if (n instanceof Integer) {
            return n.toString();
        }
        return BigDecimal.valueOf(n.doubleValue()).stripTrailingZeros().toPlainString();
    }
}
