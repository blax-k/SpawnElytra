/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.config;

import java.util.Locale;

/** {@code menus.mode} (spec §7.1): auto-detect dialogs, force dialogs, or force chat menus. */
public enum MenusMode {
    AUTO("auto"),
    DIALOG("dialog"),
    CHAT("chat");

    private final String id;

    MenusMode(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String langKey() {
        return "menu_menus_mode_" + this.id;
    }

    /** Unknown/absent → {@link #AUTO}. */
    public static MenusMode fromIdOrDefault(final String id) {
        if (id != null) {
            final String s = id.trim().toLowerCase(Locale.ROOT);
            for (final MenusMode m : values()) {
                if (m.id.equals(s)) {
                    return m;
                }
            }
        }
        return AUTO;
    }

    /**
     * Whether dialogs should be used. {@code DIALOG} without runtime support falls back to chat.
     *
     * @param dialogsSupported platform detection result (Paper: Dialog API class present; Fabric: MC ≥ 1.21.6)
     */
    public boolean useDialogs(final boolean dialogsSupported) {
        return dialogsSupported && this != CHAT;
    }
}
