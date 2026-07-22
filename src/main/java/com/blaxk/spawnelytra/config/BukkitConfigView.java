/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.config;

import com.blaxk.spawnelytra.common.config.ConfigView;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** {@link ConfigView} over a Bukkit {@link ConfigurationSection}; never consults defaults (see the contract). */
public final class BukkitConfigView implements ConfigView {
    private final ConfigurationSection section;

    public BukkitConfigView(final ConfigurationSection section) {
        this.section = section;
    }

    public ConfigurationSection handle() {
        return this.section;
    }

    @Override
    public Set<String> keys() {
        return this.section.getKeys(false);
    }

    @Override
    public Object get(final String path) {
        final Object value = this.section.get(path, null);
        if (value instanceof final ConfigurationSection sub) {
            return new BukkitConfigView(sub);
        }
        return value;
    }

    @Override
    public void set(final String path, final Object value) {
        this.section.set(path, value);
    }

    @Override
    public ConfigView createSection(final String path) {
        return new BukkitConfigView(this.section.createSection(path));
    }

    @Override
    public List<String> comments(final String path) {
        try {
            final List<String> comments = this.section.getComments(path);
            final List<String> out = new ArrayList<>(comments.size());
            for (final String c : comments) {
                out.add(c == null ? "" : c);
            }
            return out;
        } catch (final Throwable noCommentApi) {
            return List.of();
        }
    }

    @Override
    public void setComments(final String path, final List<String> comments) {
        if (this.section.get(path, null) == null) {
            return;
        }
        try {
            // Bukkit uses null entries for blank lines; the core uses "" (see comments()).
            final List<String> mapped = new ArrayList<>(comments.size());
            for (final String c : comments) {
                mapped.add(c == null || c.isEmpty() ? null : c);
            }
            this.section.setComments(path, mapped);
        } catch (final Throwable noCommentApi) {
            // pre-1.18.1 API: comments are not preserved
        }
    }
}
