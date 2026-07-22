/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.text.Msg;

/**
 * Outcome of applying a key: the new value ({@code Zone} for zone keys, the canonical config value for global keys)
 * or an error message ({@code zone_set_*} lang keys).
 */
public record SetResult<T>(T value, Msg error) {

    public static <T> SetResult<T> ok(final T value) {
        return new SetResult<>(value, null);
    }

    public static <T> SetResult<T> fail(final Msg error) {
        return new SetResult<>(null, error);
    }

    public static <T> SetResult<T> fail(final String key, final Object... args) {
        return new SetResult<>(null, Msg.of(key, args));
    }

    public boolean ok() {
        return this.error == null;
    }
}
