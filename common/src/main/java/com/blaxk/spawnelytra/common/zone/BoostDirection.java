/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Locale;

/** Boost direction (same ids as 1.5). Unknown values fall back to {@link #FORWARD} like 1.5. */
public enum BoostDirection {
    FORWARD("forward"),
    UPWARD("upward");

    private final String id;

    BoostDirection(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "zone_direction_" + this.id;
    }

    public static BoostDirection fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String s = id.trim().toLowerCase(Locale.ROOT);
        for (final BoostDirection d : values()) {
            if (d.id.equals(s)) {
                return d;
            }
        }
        return null;
    }

    public static BoostDirection fromIdOrDefault(final String id) {
        final BoostDirection d = BoostDirection.fromId(id);
        return d == null ? FORWARD : d;
    }
}
