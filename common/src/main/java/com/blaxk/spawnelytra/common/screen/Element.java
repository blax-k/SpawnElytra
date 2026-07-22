/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.text.Msg;

import java.util.List;

/**
 * Body elements of a {@link Screen}. Render top to bottom.
 * <ul>
 *     <li>{@link Text}: a line of text (dialog: plain message body; chat: a line)</li>
 *     <li>{@link Heading}: a section heading (dialog: plain message, bold; chat: header line)</li>
 *     <li>{@link Row}: a summary text with its own buttons (overview zone rows). Dialog: render the text as body and
 *     the buttons as action buttons labelled with the row (or as a nested per-zone dialog); chat: text then
 *     {@code [Button] [Button]} on one line.</li>
 *     <li>{@link Field}: an editable value (dialog: input; chat: controls, see {@link Field})</li>
 * </ul>
 */
public sealed interface Element permits Element.Text, Element.Heading, Element.Row, Field {

    record Text(Msg text) implements Element {
    }

    record Heading(Msg text) implements Element {
    }

    /** @param id stable id (e.g. the zone name) */
    record Row(String id, Msg text, List<Button> buttons) implements Element {
        public Row {
            buttons = List.copyOf(buttons);
        }
    }
}
