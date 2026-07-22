/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import org.bukkit.Sound;

public enum SoundUtil {
    ;

    /**
     * Resolves a sound by its constant name (e.g. {@code ENTITY_BAT_TAKEOFF}).
     * {@link Sound} is an enum up to 1.21.2 and an interface with static constants afterwards,
     * so {@code Sound.valueOf} is not binary compatible across versions; a field lookup works on both.
     *
     * @throws IllegalArgumentException if no sound with that name exists
     */
    public static Sound byName(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Empty sound name");
        }
        try {
            final Field field = Sound.class.getField(name.trim().toUpperCase(Locale.ROOT));
            if (Modifier.isStatic(field.getModifiers()) && Sound.class.isAssignableFrom(field.getType())) {
                final Object value = field.get(null);
                if (value != null) {
                    return (Sound) value;
                }
            }
        } catch (final NoSuchFieldException | IllegalAccessException ignored) {
        }
        final String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.indexOf(':') >= 0 || trimmed.indexOf('.') >= 0) {
            final org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(trimmed);
            if (key != null) {
                try {
                    final Sound sound = org.bukkit.Registry.SOUNDS.get(key);
                    if (sound != null) {
                        return sound;
                    }
                } catch (final Throwable registryUnavailable) {
                }
            }
        }
        throw new IllegalArgumentException("Unknown sound: " + name);
    }
}
