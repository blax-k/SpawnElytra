/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * The player's real inventory while the zone editor replaces the hotbar: main inventory,
 * armor and offhand (inventory slots 0-40 on every supported version) and the cursor stack.
 * Serialized as gzipped NBT (vanilla item codec, so every component survives) in Base64, which
 * is what the player data file stores so a crash mid-edit never loses items.
 */
public final class InventorySnapshot {
    public static final int SLOTS = 41;

    private final ItemStack[] slots;
    private final ItemStack cursor;

    private InventorySnapshot(final ItemStack[] slots, final ItemStack cursor) {
        this.slots = slots;
        this.cursor = cursor;
    }

    public static InventorySnapshot capture(final ServerPlayer player) {
        final Inventory inventory = player.getInventory();
        final ItemStack[] copy = new ItemStack[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            copy[i] = inventory.getItem(i).copy();
        }
        return new InventorySnapshot(copy, player.containerMenu.getCarried().copy());
    }

    /** Empties the inventory (all slots and the cursor). */
    public static void clear(final ServerPlayer player) {
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < SLOTS; i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
        player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /** Puts the snapshot back, replacing whatever is in the inventory now. */
    public void restore(final ServerPlayer player) {
        final Inventory inventory = player.getInventory();
        for (int i = 0; i < SLOTS; i++) {
            inventory.setItem(i, this.slots[i].copy());
        }
        player.containerMenu.setCarried(this.cursor.copy());
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastFullState();
    }

    public String encode(final HolderLookup.Provider registries) throws IOException {
        final DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        final CompoundTag root = new CompoundTag();
        final ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            if (!this.slots[i].isEmpty()) {
                entry.put("item", ItemStack.OPTIONAL_CODEC.encodeStart(ops, this.slots[i]).getOrThrow());
                list.add(entry);
            }
        }
        root.put("slots", list);
        if (!this.cursor.isEmpty()) {
            root.put("cursor", ItemStack.OPTIONAL_CODEC.encodeStart(ops, this.cursor).getOrThrow());
        }
        root.putInt("version", 1);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** File content of {@code playerdata/editor-inventory/<uuid>.yml}. */
    public String serialize(final HolderLookup.Provider registries) throws IOException {
        return "# Spawn Elytra zone editor: inventory stored while editing (restored on the next join)\n"
                + "format: fabric-nbt-v1\n"
                + "data: " + this.encode(registries) + "\n";
    }

    public static InventorySnapshot deserialize(final HolderLookup.Provider registries, final String content) throws IOException {
        for (final String line : content.split("\\R")) {
            final String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                return decode(registries, trimmed.substring(5).trim());
            }
        }
        throw new IOException("no inventory data");
    }

    public static InventorySnapshot decode(final HolderLookup.Provider registries, final String encoded) throws IOException {
        final CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)), NbtAccounter.unlimitedHeap());
        final DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        final ItemStack[] slots = new ItemStack[SLOTS];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        final Tag slotsTag = root.get("slots");
        if (slotsTag instanceof final ListTag list) {
            for (final Tag element : list) {
                if (element instanceof final CompoundTag entry && entry.get("item") != null) {
                    final Tag slotTag = entry.get("slot");
                    final int slot = slotTag instanceof final net.minecraft.nbt.NumericTag numeric ? numberOf(numeric) : -1;
                    if (slot >= 0 && slot < SLOTS) {
                        slots[slot] = ItemStack.OPTIONAL_CODEC.parse(ops, entry.get("item")).result().orElse(ItemStack.EMPTY);
                    }
                }
            }
        }
        final Tag cursorTag = root.get("cursor");
        final ItemStack cursor = cursorTag == null ? ItemStack.EMPTY
                : ItemStack.OPTIONAL_CODEC.parse(ops, cursorTag).result().orElse(ItemStack.EMPTY);
        return new InventorySnapshot(slots, cursor);
    }

    private static int numberOf(final net.minecraft.nbt.NumericTag tag) {
        //? if >=1.21.5 {
        return tag.intValue();
        //?} else {
        /*return tag.getAsInt();
        *///?}
    }
}
