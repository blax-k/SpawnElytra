/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Locale;

/** Where boost prompts are shown (spec §4). Global default + optional per-zone override. */
public enum BoostDisplay {
    ACTIONBAR("actionbar"),
    BOSSBAR("bossbar"),
    NONE("none");

    private final String id;

    BoostDisplay(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "menu_boost_display_" + this.id;
    }

    public static BoostDisplay fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String s = id.trim().toLowerCase(Locale.ROOT);
        for (final BoostDisplay d : values()) {
            if (d.id.equals(s)) {
                return d;
            }
        }
        return null;
    }

    /** Unknown/absent → {@link #ACTIONBAR} (1.5 behaviour). */
    public static BoostDisplay fromIdOrDefault(final String id) {
        final BoostDisplay d = BoostDisplay.fromId(id);
        return d == null ? ACTIONBAR : d;
    }
}
