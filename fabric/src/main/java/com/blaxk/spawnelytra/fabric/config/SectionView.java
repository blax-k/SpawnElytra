/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.config;

import com.blaxk.spawnelytra.common.config.ConfigView;

import java.util.List;
import java.util.Set;

/**
 * {@link ConfigView} over the Bukkit-compatible {@link ConfigSection}, so the shared core reads
 * and writes the Fabric config exactly like the Paper adapter does over Bukkit's
 * {@code ConfigurationSection} (same default fallback, key order and comment handling).
 */
public final class SectionView implements ConfigView {
    private final ConfigSection section;

    public SectionView(final ConfigSection section) {
        this.section = section;
    }

    public ConfigSection unwrap() {
        return this.section;
    }

    @Override
    public Set<String> keys() {
        return this.section.getKeys(false);
    }

    @Override
    public Object get(final String path) {
        final Object value = this.section.get(path, null);
        return value instanceof final ConfigSection sub ? new SectionView(sub) : value;
    }

    @Override
    public void set(final String path, final Object value) {
        this.section.set(path, value instanceof final SectionView view ? view.section : value);
    }

    @Override
    public ConfigView createSection(final String path) {
        return new SectionView(this.section.createSection(path));
    }

    @Override
    public List<String> comments(final String path) {
        final List<String> comments = this.section.getComments(path);
        return comments == null ? List.of() : comments;
    }

    @Override
    public void setComments(final String path, final List<String> comments) {
        this.section.setComments(path, comments);
    }
}
