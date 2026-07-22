/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.text.Msg;

/**
 * A clickable button.
 *
 * @param label   button text ({@code menu_button_*})
 * @param tooltip hover text ({@code menu_hover_*}), may be {@code null}
 * @param action  what happens on click
 * @param mode    where the button is shown (some buttons only make sense in dialogs, e.g. form submits)
 */
public record Button(Msg label, Msg tooltip, Action action, Mode mode) {

    public enum Mode {
        BOTH, DIALOG_ONLY, CHAT_ONLY;

        public boolean visible(final boolean dialog) {
            return this == BOTH || (dialog ? this == DIALOG_ONLY : this == CHAT_ONLY);
        }
    }

    public static Button of(final String labelKey, final String tooltipKey, final Action action) {
        return new Button(Msg.of(labelKey), tooltipKey == null ? null : Msg.of(tooltipKey), action, Mode.BOTH);
    }

    public Button dialogOnly() {
        return new Button(this.label, this.tooltip, this.action, Mode.DIALOG_ONLY);
    }

    public Button chatOnly() {
        return new Button(this.label, this.tooltip, this.action, Mode.CHAT_ONLY);
    }
}
