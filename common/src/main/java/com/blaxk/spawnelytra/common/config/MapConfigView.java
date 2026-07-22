/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link ConfigView} backed by insertion-ordered maps. Used by unit tests and usable by platforms for
 * scratch data. Nested {@code Map}s passed to the constructor / {@link #fromMap(Map)} become sections
 * (like a YAML load); maps inside lists stay maps.
 */
public final class MapConfigView implements ConfigView {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, List<String>> comments = new LinkedHashMap<>();

    public MapConfigView() {
    }

    /** Builds a view from a parsed YAML tree (e.g. SnakeYAML {@code load} output). */
    public static MapConfigView fromMap(final Map<?, ?> map) {
        final MapConfigView view = new MapConfigView();
        if (map != null) {
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                final String key = String.valueOf(e.getKey());
                if (e.getValue() instanceof final Map<?, ?> sub) {
                    view.values.put(key, MapConfigView.fromMap(sub));
                } else if (e.getValue() != null) {
                    view.values.put(key, e.getValue());
                }
            }
        }
        return view;
    }

    /** Converts back into plain nested maps (sections become {@code LinkedHashMap}s). */
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> e : this.values.entrySet()) {
            out.put(e.getKey(), e.getValue() instanceof final MapConfigView sub ? sub.toMap() : e.getValue());
        }
        return out;
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.values.keySet()));
    }

    private MapConfigView parentOf(final String path, final boolean create) {
        MapConfigView cur = this;
        final String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            final Object next = cur.values.get(parts[i]);
            if (next instanceof final MapConfigView sub) {
                cur = sub;
            } else if (create) {
                final MapConfigView sub = new MapConfigView();
                cur.values.put(parts[i], sub);
                cur = sub;
            } else {
                return null;
            }
        }
        return cur;
    }

    private static String leaf(final String path) {
        final int i = path.lastIndexOf('.');
        return i < 0 ? path : path.substring(i + 1);
    }

    @Override
    public Object get(final String path) {
        if (path == null || path.isEmpty()) {
            return this;
        }
        final MapConfigView parent = this.parentOf(path, false);
        return parent == null ? null : parent.values.get(MapConfigView.leaf(path));
    }

    @Override
    public void set(final String path, final Object value) {
        final MapConfigView parent = this.parentOf(path, value != null);
        if (parent == null) {
            return;
        }
        final String key = MapConfigView.leaf(path);
        if (value == null) {
            parent.values.remove(key);
            parent.comments.remove(key);
        } else if (value instanceof final Map<?, ?> m) {
            parent.values.put(key, MapConfigView.fromMap(m));
        } else {
            parent.values.put(key, value);
        }
    }

    @Override
    public ConfigView createSection(final String path) {
        final MapConfigView parent = this.parentOf(path, true);
        final MapConfigView sub = new MapConfigView();
        parent.values.put(MapConfigView.leaf(path), sub);
        return sub;
    }

    @Override
    public List<String> comments(final String path) {
        final MapConfigView parent = this.parentOf(path, false);
        if (parent == null) {
            return Collections.emptyList();
        }
        final List<String> c = parent.comments.get(MapConfigView.leaf(path));
        return c == null ? Collections.emptyList() : Collections.unmodifiableList(c);
    }

    @Override
    public void setComments(final String path, final List<String> comments) {
        final MapConfigView parent = this.parentOf(path, false);
        if (parent == null || !parent.values.containsKey(MapConfigView.leaf(path))) {
            return;
        }
        parent.comments.put(MapConfigView.leaf(path), comments == null ? new ArrayList<>() : new ArrayList<>(comments));
    }

    @Override
    public String toString() {
        return "MapConfigView" + this.toMap();
    }
}
