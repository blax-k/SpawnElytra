/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Locale;

/** How spawn elytra gliding is started inside a zone (same ids as 1.5). */
public enum ActivationMode {
    DOUBLE_JUMP("double_jump"),
    AUTO("auto"),
    SNEAK_JUMP("sneak_jump"),
    F_KEY("f_key");

    private final String id;

    ActivationMode(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** Lang key of the display name ({@code zone_mode_double_jump}, ...). */
    public String langKey() {
        return "zone_mode_" + this.id;
    }

    /** Parses the config id (case-insensitive); unknown → {@code null}. */
    public static ActivationMode fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String s = id.trim().toLowerCase(Locale.ROOT);
        for (final ActivationMode m : values()) {
            if (m.id.equals(s)) {
                return m;
            }
        }
        return null;
    }

    /** Like {@link #fromId(String)} but falls back to {@link #DOUBLE_JUMP} (1.5 default). */
    public static ActivationMode fromIdOrDefault(final String id) {
        final ActivationMode m = ActivationMode.fromId(id);
        return m == null ? DOUBLE_JUMP : m;
    }
}
