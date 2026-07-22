/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import com.blaxk.spawnelytra.common.text.Msg;

/**
 * Outcome of an editor action. On success play the click sound, refresh the preview and show {@link #feedback()}
 * (actionbar, may be {@code null}); on failure show {@link #error()} (actionbar, error sound) — the draft is unchanged.
 */
public record EditResult(boolean success, Msg feedback, Msg error) {

    public static EditResult ok() {
        return new EditResult(true, null, null);
    }

    public static EditResult ok(final Msg feedback) {
        return new EditResult(true, feedback, null);
    }

    public static EditResult fail(final Msg error) {
        return new EditResult(false, null, error);
    }

    public static EditResult fail(final String key, final Object... args) {
        return new EditResult(false, null, Msg.of(key, args));
    }
}
