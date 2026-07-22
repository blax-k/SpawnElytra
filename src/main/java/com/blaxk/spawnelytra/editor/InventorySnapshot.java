/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.editor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable copy of a player's inventory (storage, armor, offhand, cursor) taken when entering the zone editor.
 * Serialized through Bukkit's YAML item serialization so it can be persisted and restored after a crash.
 */
public final class InventorySnapshot {
    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final ItemStack cursor;

    private InventorySnapshot(final ItemStack[] storage, final ItemStack[] armor, final ItemStack offhand, final ItemStack cursor) {
        this.storage = storage;
        this.armor = armor;
        this.offhand = offhand;
        this.cursor = cursor;
    }

    /** Must run on the player's thread. */
    public static InventorySnapshot capture(final Player player) {
        final PlayerInventory inv = player.getInventory();
        return new InventorySnapshot(
                cloneAll(inv.getStorageContents()),
                cloneAll(inv.getArmorContents()),
                cloneOne(inv.getItemInOffHand()),
                cloneOne(player.getItemOnCursor()));
    }

    /** Must run on the player's thread. Replaces the whole inventory with this snapshot. */
    public void restore(final Player player) {
        final PlayerInventory inv = player.getInventory();
        player.setItemOnCursor(null);
        inv.clear();
        inv.setStorageContents(cloneAll(this.storage));
        inv.setArmorContents(cloneAll(this.armor));
        inv.setItemInOffHand(cloneOne(this.offhand));
        if (this.cursor != null) {
            // Never leave anything on the cursor (it would be dropped when the inventory closes): put it back into
            // the inventory, overflow drops at the player's feet.
            for (final ItemStack leftover : inv.addItem(cloneOne(this.cursor)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    public String serialize() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("storage", toList(this.storage));
        yaml.set("armor", toList(this.armor));
        yaml.set("offhand", this.offhand);
        yaml.set("cursor", this.cursor);
        return yaml.saveToString();
    }

    public static InventorySnapshot deserialize(final String data) throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(data);
        return new InventorySnapshot(
                fromList(yaml.getList("storage"), 36),
                fromList(yaml.getList("armor"), 4),
                yaml.getItemStack("offhand"),
                yaml.getItemStack("cursor"));
    }

    private static List<ItemStack> toList(final ItemStack[] items) {
        final List<ItemStack> out = new ArrayList<>(items.length);
        for (final ItemStack item : items) {
            out.add(item);
        }
        return out;
    }

    private static ItemStack[] fromList(final List<?> list, final int size) {
        final ItemStack[] out = new ItemStack[list == null ? size : Math.max(size, list.size())];
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof final ItemStack stack) {
                    out[i] = stack;
                }
            }
        }
        return out;
    }

    private static ItemStack[] cloneAll(final ItemStack[] items) {
        final ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = cloneOne(items[i]);
        }
        return out;
    }

    private static ItemStack cloneOne(final ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}
