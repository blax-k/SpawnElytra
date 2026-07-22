/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;

/**
 * Converts Adventure components (built with MiniMessage, exactly like on Paper) into vanilla
 * text components. The JSON layout is chosen from the running game's data version, so click
 * and hover events use the pre- or post-1.21.5 format as the client expects.
 */
public final class Texts {
    private static GsonComponentSerializer serializer = GsonComponentSerializer.gson();
    private static HolderLookup.Provider registries;

    private Texts() {
    }

    public static void init(final HolderLookup.Provider registries) {
        Texts.registries = registries;
        Texts.serializer = GsonComponentSerializer.builder()
                .options(JSONOptions.byDataVersion().at(Compat.dataVersion()))
                .build();
    }

    public static net.minecraft.network.chat.Component toNative(final Component component) {
        final JsonElement json = serializer.serializeToTree(component);
        if (registries == null) {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        }
        return ComponentSerialization.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).getOrThrow();
    }
}
