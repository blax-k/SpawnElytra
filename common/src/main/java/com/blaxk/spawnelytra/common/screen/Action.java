/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A button/click action. Dialogs send it as a custom click action ({@link #id()} in the {@code spawnelytra:}
 * namespace, {@link #payload()} as the payload — plus the form input values for {@link ActionIds#isFormSubmit form
 * submits}); chat menus use {@link #command()} as {@code run_command} (or {@code suggest_command} when
 * {@link #suggest()} is true). Handlers MUST re-check permissions and that referenced zones still exist, and ignore
 * unknown ids / malformed payloads ({@link ActionIds#isKnown}).
 *
 * @param suggest chat: put the command into the chat box instead of running it (text input such as rename)
 */
public record Action(String id, Map<String, String> payload, boolean suggest) {

    public Action {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public static Action of(final String id, final String... kv) {
        final Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new Action(id, m, false);
    }

    public Action suggesting() {
        return new Action(this.id, this.payload, true);
    }

    public String get(final String key) {
        return this.payload.get(key);
    }

    /** The equivalent chat command ({@code /se ...}), or {@code null} if the action has no chat form (close). */
    public String command() {
        return ActionIds.toCommand(this);
    }
}
