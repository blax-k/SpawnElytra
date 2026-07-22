/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small re-implementation of Bukkit's {@code MemorySection} so the Fabric port can read
 * and write the exact same YAML files with the exact same lookup semantics (dotted paths,
 * typed getters with explicit defaults, defaults fallback for sections, per-key comments).
 */
public class ConfigSection {
    static final char SEPARATOR = '.';

    /** One key of a section: the value plus the comments attached to it. */
    static final class Entry {
        Object value;
        List<String> comments = Collections.emptyList();
        List<String> inlineComments = Collections.emptyList();

        Entry(final Object value) {
            this.value = value;
        }
    }

    final Map<String, Entry> map = new LinkedHashMap<>();
    private final ConfigSection parent;
    private final String path;

    ConfigSection() {
        this.parent = null;
        this.path = "";
    }

    ConfigSection(final ConfigSection parent, final String key) {
        this.parent = parent;
        this.path = parent.path.isEmpty() ? key : parent.path + SEPARATOR + key;
    }

    public String getCurrentPath() {
        return this.path;
    }

    protected YamlConfiguration root() {
        ConfigSection s = this;
        while (s.parent != null) {
            s = s.parent;
        }
        return s instanceof final YamlConfiguration y ? y : null;
    }

    /** Keys of this section (non-deep), in file order. */
    public Set<String> getKeys(final boolean deep) {
        final Set<String> keys = new LinkedHashSet<>();
        for (final Map.Entry<String, Entry> e : this.map.entrySet()) {
            keys.add(e.getKey());
            if (deep && e.getValue().value instanceof final ConfigSection sub) {
                for (final String k : sub.getKeys(true)) {
                    keys.add(e.getKey() + SEPARATOR + k);
                }
            }
        }
        return keys;
    }

    public boolean contains(final String path) {
        return this.contains(path, false);
    }

    public boolean contains(final String path, final boolean ignoreDefault) {
        return (ignoreDefault ? this.get(path, null) : this.get(path)) != null;
    }

    public boolean isSet(final String path) {
        return this.get(path, null) != null;
    }

    protected Object getDefault(final String path) {
        final YamlConfiguration root = this.root();
        if (root == null || root.getDefaults() == null) {
            return null;
        }
        final String full = this.path.isEmpty() ? path : this.path + SEPARATOR + path;
        return root.getDefaults().get(full, null);
    }

    public Object get(final String path) {
        return this.get(path, this.getDefault(path));
    }

    public Object get(final String path, final Object def) {
        if (path == null) {
            return def;
        }
        if (path.isEmpty()) {
            return this;
        }
        ConfigSection section = this;
        int i1 = -1;
        int i2;
        while ((i1 = path.indexOf(SEPARATOR, i2 = i1 + 1)) != -1) {
            final Entry e = section.map.get(path.substring(i2, i1));
            if (e == null || !(e.value instanceof final ConfigSection sub)) {
                return def;
            }
            section = sub;
        }
        final Entry e = section.map.get(path.substring(i2));
        return e == null ? def : e.value;
    }

    /** Sets a value; {@code null} removes the key. Existing comments are kept. */
    public void set(final String path, final Object value) {
        ConfigSection section = this;
        int i1 = -1;
        int i2;
        while ((i1 = path.indexOf(SEPARATOR, i2 = i1 + 1)) != -1) {
            final String node = path.substring(i2, i1);
            final Entry e = section.map.get(node);
            if (e != null && e.value instanceof final ConfigSection sub) {
                section = sub;
            } else if (value == null) {
                return;
            } else {
                section = section.createSection(node);
            }
        }
        final String key = path.substring(i2);
        if (value == null) {
            section.map.remove(key);
            return;
        }
        final Object stored = value instanceof final Map<?, ?> m ? section.sectionFromMap(key, m) : value;
        final Entry existing = section.map.get(key);
        if (existing == null) {
            section.map.put(key, new Entry(stored));
        } else {
            existing.value = stored;
        }
    }

    private ConfigSection sectionFromMap(final String key, final Map<?, ?> values) {
        final ConfigSection sub = new ConfigSection(this, key);
        for (final Map.Entry<?, ?> e : values.entrySet()) {
            sub.set(String.valueOf(e.getKey()), e.getValue());
        }
        return sub;
    }

    /** Creates (or replaces) a section at the given path, mirroring Bukkit's createSection. */
    public ConfigSection createSection(final String path) {
        ConfigSection section = this;
        int i1 = -1;
        int i2;
        while ((i1 = path.indexOf(SEPARATOR, i2 = i1 + 1)) != -1) {
            final String node = path.substring(i2, i1);
            final Entry e = section.map.get(node);
            section = (e != null && e.value instanceof final ConfigSection sub) ? sub : section.createSection(node);
        }
        final String key = path.substring(i2);
        final ConfigSection result = new ConfigSection(section, key);
        section.map.put(key, new Entry(result));
        return result;
    }

    public ConfigSection getConfigurationSection(final String path) {
        Object val = this.get(path, null);
        if (val != null) {
            return val instanceof final ConfigSection s ? s : null;
        }
        val = this.get(path, this.getDefault(path));
        return val instanceof ConfigSection ? this.createSection(path) : null;
    }

    public boolean isConfigurationSection(final String path) {
        return this.get(path) instanceof ConfigSection;
    }

    public String getString(final String path) {
        final Object def = this.getDefault(path);
        return this.getString(path, def != null ? def.toString() : null);
    }

    public String getString(final String path, final String def) {
        final Object val = this.get(path, def);
        return val != null ? val.toString() : def;
    }

    public int getInt(final String path) {
        final Object def = this.getDefault(path);
        return this.getInt(path, def instanceof final Number n ? n.intValue() : 0);
    }

    public int getInt(final String path, final int def) {
        final Object val = this.get(path, def);
        return val instanceof final Number n ? toInt(n) : def;
    }

    public long getLong(final String path, final long def) {
        final Object val = this.get(path, def);
        return val instanceof final Number n ? n.longValue() : def;
    }

    public double getDouble(final String path) {
        final Object def = this.getDefault(path);
        return this.getDouble(path, def instanceof final Number n ? n.doubleValue() : 0);
    }

    public double getDouble(final String path, final double def) {
        final Object val = this.get(path, def);
        return val instanceof final Number n ? n.doubleValue() : def;
    }

    public boolean getBoolean(final String path) {
        final Object def = this.getDefault(path);
        return this.getBoolean(path, def instanceof final Boolean b ? b : false);
    }

    public boolean getBoolean(final String path, final boolean def) {
        final Object val = this.get(path, def);
        return val instanceof final Boolean b ? b : def;
    }

    private static int toInt(final Number n) {
        // Same as Bukkit's NumberConversions.toInt for numbers.
        return n.intValue();
    }

    // ---- comments -------------------------------------------------------------------------

    private Entry entry(final String path) {
        ConfigSection section = this;
        int i1 = -1;
        int i2;
        while ((i1 = path.indexOf(SEPARATOR, i2 = i1 + 1)) != -1) {
            final Entry e = section.map.get(path.substring(i2, i1));
            if (e == null || !(e.value instanceof final ConfigSection sub)) {
                return null;
            }
            section = sub;
        }
        return section.map.get(path.substring(i2));
    }

    public List<String> getComments(final String path) {
        final Entry e = this.entry(path);
        return e == null ? Collections.emptyList() : Collections.unmodifiableList(e.comments);
    }

    public List<String> getInlineComments(final String path) {
        final Entry e = this.entry(path);
        return e == null ? Collections.emptyList() : Collections.unmodifiableList(e.inlineComments);
    }

    public void setComments(final String path, final List<String> comments) {
        final Entry e = this.entry(path);
        if (e != null) {
            e.comments = comments == null ? Collections.emptyList() : new ArrayList<>(comments);
        }
    }

    public void setInlineComments(final String path, final List<String> comments) {
        final Entry e = this.entry(path);
        if (e != null) {
            e.inlineComments = comments == null ? Collections.emptyList() : new ArrayList<>(comments);
        }
    }

    @Override
    public String toString() {
        return "ConfigSection[path='" + this.path + "']";
    }
}
