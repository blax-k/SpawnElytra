/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Minimal, platform-neutral view of one YAML section (Bukkit {@code ConfigurationSection} on Paper,
 * {@code ConfigSection} on Fabric). Paths are dotted ({@code boost.strength}).
 *
 * <h2>Adapter contract (both platforms MUST follow it, the migration relies on it)</h2>
 * <ul>
 *     <li><b>Never consult defaults.</b> Bukkit: use {@code get(path, null)}, {@code isSet(path)} and
 *     {@code getKeys(false)}; never {@code contains(path)} without {@code ignoreDefault=true}.</li>
 *     <li>{@link #get(String)} returns scalars ({@code String, Integer, Long, Double, Boolean}) and lists
 *     ({@code List<?>}, elements may be {@code Map<?, ?>}) unchanged; a nested section is returned wrapped
 *     as a {@link ConfigView}.</li>
 *     <li>{@link #set(String, Object)} with {@code null} removes the key. Core code never passes a
 *     {@code Map} to {@code set} (use {@link #createSection(String)}); it does pass {@code List<Map<String,Object>>}
 *     for polygon points.</li>
 *     <li>Comments are the platform's comment lines without the leading {@code "# "}.</li>
 * </ul>
 * Typed getters are implemented here with Bukkit's semantics (a value of the wrong type yields the default),
 * so both platforms read the same values.
 */
public interface ConfigView {

    /** Direct child keys (non-deep), in file order. */
    Set<String> keys();

    /** Raw value at the path or {@code null}; sections are returned as {@link ConfigView}. */
    Object get(String path);

    /** Sets (or with {@code null} removes) a value. Intermediate sections are created. */
    void set(String path, Object value);

    /** Creates (or replaces) a section at the path and returns it. */
    ConfigView createSection(String path);

    /** Comment lines above the key (may be empty, never null). */
    List<String> comments(String path);

    /** Replaces the comment lines above the key (no-op if the key does not exist). */
    void setComments(String path, List<String> comments);

    // ---- derived helpers -------------------------------------------------------------------------------------

    /** {@code true} if a value (or section) is set at the path. */
    default boolean contains(final String path) {
        return this.get(path) != null;
    }

    /** The section at the path, or {@code null} if absent / not a section. */
    default ConfigView section(final String path) {
        return this.get(path) instanceof final ConfigView v ? v : null;
    }

    default boolean isSection(final String path) {
        return this.get(path) instanceof ConfigView;
    }

    /** Bukkit semantics: any non-section value is stringified. */
    default String getString(final String path, final String def) {
        final Object v = this.get(path);
        if (v == null || v instanceof ConfigView) {
            return def;
        }
        return v.toString();
    }

    default int getInt(final String path, final int def) {
        return this.get(path) instanceof final Number n ? n.intValue() : def;
    }

    default long getLong(final String path, final long def) {
        return this.get(path) instanceof final Number n ? n.longValue() : def;
    }

    default double getDouble(final String path, final double def) {
        return this.get(path) instanceof final Number n ? n.doubleValue() : def;
    }

    default boolean getBoolean(final String path, final boolean def) {
        return this.get(path) instanceof final Boolean b ? b : def;
    }

    /** {@code Integer} value or {@code null} when absent / not a number. */
    default Integer getIntOrNull(final String path) {
        return this.get(path) instanceof final Number n ? n.intValue() : null;
    }

    /** {@code Double} value or {@code null} when absent / not a number. */
    default Double getDoubleOrNull(final String path) {
        return this.get(path) instanceof final Number n ? n.doubleValue() : null;
    }

    /** List value or an empty list. */
    default List<?> getList(final String path) {
        return this.get(path) instanceof final List<?> l ? l : Collections.emptyList();
    }

    /**
     * Deep copy of {@code from}'s children into this view under {@code path} (values only, comments of the
     * copied keys are copied too). Lists are copied shallowly.
     */
    default void copyFrom(final ConfigView from, final String path) {
        for (final String key : from.keys()) {
            final Object v = from.get(key);
            final String target = path == null || path.isEmpty() ? key : path + "." + key;
            if (v instanceof final ConfigView sub) {
                this.createSection(target);
                this.copyFrom(sub, target);
            } else if (v instanceof final List<?> list) {
                this.set(target, new ArrayList<>(list));
            } else {
                this.set(target, v);
            }
            final List<String> c = from.comments(key);
            if (!c.isEmpty()) {
                this.setComments(target, c);
            }
        }
    }
}
