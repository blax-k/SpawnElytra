/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves sound names from the config. Bukkit enum names (e.g. {@code ENTITY_BAT_TAKEOFF},
 * case-insensitive like {@code Sound.valueOf(name.toUpperCase())}) are matched against the
 * sound registry using Bukkit's naming rule (key path upper-cased, '.' replaced by '_');
 * namespaced ids such as {@code minecraft:entity.bat.takeoff} are accepted as well.
 */
public final class SoundResolver {
    private SoundResolver() {
    }

    public static Holder<SoundEvent> resolve(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final String trimmed = name.trim();
        if (trimmed.indexOf(':') >= 0 || trimmed.indexOf('.') >= 0) {
            final Identifier id = Compat.parseId(trimmed.toLowerCase(Locale.ROOT));
            if (id != null) {
                final Holder<SoundEvent> holder = byId(id);
                if (holder != null) {
                    return holder;
                }
            }
        }
        final String wanted = trimmed.toUpperCase(Locale.ROOT);
        for (final Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!"minecraft".equals(id.getNamespace())) {
                continue;
            }
            if (id.getPath().toUpperCase(Locale.ROOT).replace('.', '_').equals(wanted)) {
                return byId(id);
            }
        }
        return null;
    }

    public static Holder<SoundEvent> resolveOrDefault(final String name, final String fallback) {
        final Holder<SoundEvent> holder = resolve(name);
        return holder != null ? holder : resolve(fallback);
    }

    private static Holder<SoundEvent> byId(final Identifier id) {
        //? if >=1.21.2 {
        final Optional<? extends Holder<SoundEvent>> holder = BuiltInRegistries.SOUND_EVENT.get(id);
        //?} else {
        /*final Optional<? extends Holder<SoundEvent>> holder = BuiltInRegistries.SOUND_EVENT.getHolder(id);
        *///?}
        return holder.map(h -> (Holder<SoundEvent>) h).orElse(null);
    }
}
