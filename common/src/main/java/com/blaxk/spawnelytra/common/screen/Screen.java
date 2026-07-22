/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.text.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-neutral description of a menu screen (spec §7.2). Identical content for dialogs and chat:
 * render {@link #title()}, then {@link #body()} in order, then {@link #buttons()} (filtered by
 * {@link Button.Mode#visible(boolean)}). In dialogs, {@link #exit()} is the dialog's exit/escape action.
 *
 * @param id      {@link ScreenId}
 * @param title   dialog title / chat header
 * @param body    rows, texts and fields
 * @param buttons footer buttons
 * @param exit    exit action (dialog escape / chat "close" is simply not rendered), may be {@code null}
 */
public record Screen(ScreenId id, Msg title, List<Element> body, List<Button> buttons, Button exit) {

    public Screen {
        body = List.copyOf(body);
        buttons = List.copyOf(buttons);
    }

    /** All fields of the body (dialog inputs, in order). */
    public List<Field> fields() {
        final List<Field> out = new ArrayList<>();
        for (final Element e : this.body) {
            if (e instanceof final Field f && !(f instanceof Field.ReadOnly)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Footer buttons visible in the given mode. */
    public List<Button> buttons(final boolean dialog) {
        final List<Button> out = new ArrayList<>();
        for (final Button b : this.buttons) {
            if (b.mode().visible(dialog)) {
                out.add(b);
            }
        }
        return out;
    }

    /** Which screen this is. */
    public enum ScreenId {
        OVERVIEW, ZONE_SETTINGS, GLOBAL_SETTINGS, NEW_ZONE, DELETE_CONFIRM, STATS
    }
}
