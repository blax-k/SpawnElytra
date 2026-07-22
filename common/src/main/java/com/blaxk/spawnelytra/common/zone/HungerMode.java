/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Locale;

/** Hunger consumption mode (1.5 ids). Unknown values fall back to {@link #ACTIVATION} like 1.5. */
public enum HungerMode {
    ACTIVATION("activation"),
    DISTANCE("distance"),
    TIME("time");

    private final String id;

    HungerMode(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "menu_hunger_mode_" + this.id;
    }

    public static HungerMode fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String s = id.trim().toLowerCase(Locale.ROOT);
        for (final HungerMode m : values()) {
            if (m.id.equals(s)) {
                return m;
            }
        }
        return null;
    }

    public static HungerMode fromIdOrDefault(final String id) {
        final HungerMode m = HungerMode.fromId(id);
        return m == null ? ACTIVATION : m;
    }
}
