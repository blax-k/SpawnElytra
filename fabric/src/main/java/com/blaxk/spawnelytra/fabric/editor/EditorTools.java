/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

import com.blaxk.spawnelytra.common.editor.EditorTool;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Hotbar tool items of the zone editor. Each tool carries the custom data entry
 * {@code spawnelytra:editor_tool = <id>} (the Fabric counterpart of the Paper PDC key), which
 * is how inventory protection and input handling recognize them.
 */
public final class EditorTools {
    public static final String TAG = "spawnelytra:editor_tool";

    private EditorTools() {
    }

    public static ItemStack create(final EditorTool tool, final ShapeType shape) {
        final Item item = BuiltInRegistries.ITEM.getOptional(Compat.parseId(tool.suggestedItem())).orElse(Items.STICK);
        final ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Texts.toNative(plain(Msg.of(tool.nameKey(shape)))));
        final List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
        final String raw = MessageUtil.rawMessage(tool.loreKey(shape));
        for (final String line : raw.split("<br>|\n")) {
            if (!line.isBlank()) {
                lore.add(Texts.toNative(plain(MessageUtil.deserialize("<#aaa8a8>" + line))));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        final CompoundTag tag = new CompoundTag();
        tag.putString(TAG, tool.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static Component plain(final Msg msg) {
        return plain(MessageUtil.render(msg));
    }

    /** Item names and lore are italic by default; the editor tools are not. */
    private static Component plain(final Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** The editor tool of a stack, or {@code null}. */
    public static EditorTool toolOf(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        final String id = Compat.tagString(data.copyTag(), TAG);
        return id == null ? null : EditorTool.byId(id);
    }

    public static boolean isTool(final ItemStack stack) {
        return toolOf(stack) != null;
    }
}
