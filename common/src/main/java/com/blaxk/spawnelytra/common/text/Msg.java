/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A platform-neutral reference to a lang message: the lang key plus named placeholder values.
 * Placeholder values are {@code String} (insert as unparsed text, e.g. {@code Placeholder.unparsed(name, value)}) or a
 * nested {@link Msg} (render it first, then insert as a component, e.g. {@code Placeholder.component}).
 * A {@code null} key denotes a literal: the text is in arg {@code "text"} (insert unparsed, no lang lookup).
 */
public record Msg(String key, Map<String, Object> args) {

    public Msg {
        args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    /** {@code Msg.of("zone_created", "name", "spawn")}: args are alternating name/value pairs. */
    public static Msg of(final String key, final Object... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholder args must be name/value pairs");
        }
        final Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            final Object v = nameValuePairs[i + 1];
            m.put(String.valueOf(nameValuePairs[i]), v instanceof Msg ? v : String.valueOf(v));
        }
        return new Msg(key, m);
    }

    /** A literal text (no lang lookup). */
    public static Msg literal(final String text) {
        return new Msg(null, Map.of("text", text == null ? "" : text));
    }

    public boolean isLiteral() {
        return this.key == null;
    }

    /** The literal text ({@code null} for keyed messages). */
    public String literalText() {
        return this.key == null ? (String) this.args.get("text") : null;
    }

    /** Copy with one more placeholder. */
    public Msg with(final String name, final Object value) {
        final Map<String, Object> m = new LinkedHashMap<>(this.args);
        m.put(name, value instanceof Msg ? value : String.valueOf(value));
        return new Msg(this.key, m);
    }
}
