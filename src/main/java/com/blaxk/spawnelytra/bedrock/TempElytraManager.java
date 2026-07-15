/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.bedrock;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.integration.BedrockSupport;
import com.blaxk.spawnelytra.listener.SpawnElytra;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TempElytraManager implements Listener {
    private final Main plugin;
    private final NamespacedKey tempElytraKey;
    private final NamespacedKey storedChestplateKey;

    private final Map<UUID, ItemStack> disguisedChestplates = new HashMap<>();

    public TempElytraManager(final Main plugin) {
        this.plugin = plugin;
        this.tempElytraKey = new NamespacedKey(plugin, "temp_elytra");
        this.storedChestplateKey = new NamespacedKey(plugin, "stored_chestplate");
    }

    public boolean isTempElytra(final ItemStack item) {
        if (item == null || Material.ELYTRA != item.getType() || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.tempElytraKey, PersistentDataType.BYTE);
    }

    public boolean hasTempElytraEquipped(final Player player) {
        return this.isTempElytra(player.getInventory().getChestplate());
    }

    public void ensureEquipped(final Player player) {
        this.removeRogueTempElytras(player);

        final ItemStack chestplate = player.getInventory().getChestplate();
        final PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (this.isTempElytra(chestplate)) {
            if (!this.disguisedChestplates.containsKey(player.getUniqueId())) {
                this.setDisguise(player, this.deserializeChestplate(pdc.get(this.storedChestplateKey, PersistentDataType.STRING)));
            }
            return;
        }

        final String existingBackup = pdc.get(this.storedChestplateKey, PersistentDataType.STRING);

        if (existingBackup != null) {
            if (chestplate == null || Material.AIR == chestplate.getType()) {
                this.setDisguise(player, this.deserializeChestplate(existingBackup));
                player.getInventory().setChestplate(this.createTempElytra());
                return;
            }
            final ItemStack previousOriginal = this.deserializeChestplate(existingBackup);
            pdc.remove(this.storedChestplateKey);
            if (previousOriginal != null) {
                this.giveOrDrop(player, previousOriginal);
            }
        }

        pdc.set(this.storedChestplateKey, PersistentDataType.STRING, this.serializeChestplate(chestplate));
        this.setDisguise(player, chestplate);
        player.getInventory().setChestplate(this.createTempElytra());
    }

    public void ensureRestored(final Player player) {
        this.disguisedChestplates.remove(player.getUniqueId());
        this.removeRogueTempElytras(player);

        final ItemStack chestplate = player.getInventory().getChestplate();
        final boolean hadTemp = this.isTempElytra(chestplate);
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final String backup = pdc.get(this.storedChestplateKey, PersistentDataType.STRING);

        if (!hadTemp && backup == null) {
            return;
        }

        if (hadTemp) {
            player.getInventory().setChestplate(null);
        }

        if (backup != null) {
            pdc.remove(this.storedChestplateKey);
            final ItemStack original = this.deserializeChestplate(backup);
            if (original != null) {
                final ItemStack currentSlot = player.getInventory().getChestplate();
                if (currentSlot == null || Material.AIR == currentSlot.getType()) {
                    player.getInventory().setChestplate(original);
                } else {
                    this.giveOrDrop(player, original);
                }
            }
        }
    }

    public void restoreAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (this.keepEquippedWhileAirborne(player)) {
                continue;
            }
            this.ensureRestored(player);
        }
    }

    public boolean hasDisguise(final Player wearer) {
        return this.disguisedChestplates.containsKey(wearer.getUniqueId());
    }

    public void sendDisguise(final Player wearer, final Player viewer) {
        final ItemStack shown = this.disguisedChestplates.get(wearer.getUniqueId());
        if (shown == null || viewer.equals(wearer)) {
            return;
        }
        try {
            viewer.sendEquipmentChange(wearer, EquipmentSlot.CHEST, shown);
        } catch (final Throwable apiUnavailable) {
        }
    }

    private void setDisguise(final Player player, final ItemStack shownItem) {
        final ItemStack shown = (shownItem == null || Material.AIR == shownItem.getType())
                ? new ItemStack(Material.AIR)
                : shownItem.clone();
        this.disguisedChestplates.put(player.getUniqueId(), shown);
        SchedulerUtil.runAtEntityLater(this.plugin, player, 2L, () -> this.broadcastDisguise(player));
    }

    private void broadcastDisguise(final Player wearer) {
        if (!wearer.isOnline() || !this.hasDisguise(wearer)) {
            return;
        }
        for (final Player viewer : this.viewersOf(wearer)) {
            this.sendDisguise(wearer, viewer);
        }
    }

    private Collection<? extends Player> viewersOf(final Player wearer) {
        try {
            return wearer.getTrackedPlayers();
        } catch (final Throwable paperApiUnavailable) {
            return wearer.getWorld().getPlayers();
        }
    }

    @EventHandler
    public void onAdvancementDone(final PlayerAdvancementDoneEvent event) {
        final NamespacedKey key = event.getAdvancement().getKey();
        if (!NamespacedKey.MINECRAFT.equals(key.getNamespace()) || !"end/elytra".equals(key.getKey())) {
            return;
        }

        final Player player = event.getPlayer();
        if (!this.hasTempElytraEquipped(player)) {
            return;
        }

        try {
            event.message(null);
        } catch (final Throwable paperApiUnavailable) {
        }
    }

    private ItemStack createTempElytra() {
        final ItemStack elytra = new ItemStack(Material.ELYTRA);
        final ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(this.tempElytraKey, PersistentDataType.BYTE, (byte) 1);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            final Component lore = MessageUtil.component("temp_elytra_lore").decoration(TextDecoration.ITALIC, false);
            try {
                meta.lore(List.of(lore));
            } catch (final Throwable paperApiUnavailable) {
                meta.setLore(List.of(LegacyComponentSerializer.legacySection().serialize(lore)));
            }
            elytra.setItemMeta(meta);
        }
        return elytra;
    }

    private String serializeChestplate(final ItemStack chestplate) {
        if (chestplate == null || Material.AIR == chestplate.getType()) {
            return "";
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", chestplate);
        return yaml.saveToString();
    }

    private ItemStack deserializeChestplate(final String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(data);
            return yaml.getItemStack("item");
        } catch (final Exception e) {
            this.plugin.getLogger().warning("Failed to restore a stored chestplate: " + e.getMessage());
            return null;
        }
    }

    private void giveOrDrop(final Player player, final ItemStack item) {
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (final ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (this.hasTempElytraEquipped(player) && BedrockSupport.isManaged(player)) {
            final SpawnElytra instance = this.plugin.getSpawnElytraInstance(player.getWorld().getName());
            if (instance != null && instance.isValid()) {
                this.ensureEquipped(player);
                instance.resumeBedrockFlight(player);
                return;
            }
        }

        this.ensureRestored(player);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (this.keepEquippedWhileAirborne(player)) {
            return;
        }
        this.ensureRestored(player);
    }

    private boolean keepEquippedWhileAirborne(final Player player) {
        if (!this.hasTempElytraEquipped(player) || player.isOnGround()) {
            return false;
        }
        this.disguisedChestplates.remove(player.getUniqueId());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final boolean removedFromDrops = event.getDrops().removeIf(this::isTempElytra);

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final String backup = pdc.get(this.storedChestplateKey, PersistentDataType.STRING);
        if (backup == null) {
            return;
        }

        if (removedFromDrops) {
            pdc.remove(this.storedChestplateKey);
            final ItemStack original = this.deserializeChestplate(backup);
            if (original != null) {
                event.getDrops().add(original);
            }
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
            if (player.isOnline()) {
                this.ensureRestored(player);
            }
        });
    }

    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        this.ensureRestored(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        final GameMode newMode = event.getNewGameMode();
        if (GameMode.CREATIVE == newMode || GameMode.SPECTATOR == newMode) {
            this.ensureRestored(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (this.isTempElytra(event.getCurrentItem()) || this.isTempElytra(event.getCursor())) {
            event.setCancelled(true);
            this.resyncInventory(event.getWhoClicked());
            return;
        }

        if (event.getWhoClicked() instanceof final Player player
                && this.hasTempElytraEquipped(player)
                && ClickType.RIGHT == event.getClick()
                && this.isChestEquippable(event.getCurrentItem())) {
            event.setCancelled(true);
            this.resyncInventory(player);
        }
    }

    private boolean isChestEquippable(final ItemStack item) {
        if (item == null) {
            return false;
        }
        return switch (item.getType()) {
            case ELYTRA, LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE,
                 GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onRightClickArmor(final PlayerInteractEvent event) {
        if (Action.RIGHT_CLICK_AIR != event.getAction() && Action.RIGHT_CLICK_BLOCK != event.getAction()) {
            return;
        }
        final Player player = event.getPlayer();
        if (this.isChestEquippable(event.getItem()) && this.hasTempElytraEquipped(player)) {
            event.setUseItemInHand(Event.Result.DENY);
            this.resyncInventory(player);
        }
    }

    private void removeRogueTempElytras(final Player player) {
        final ItemStack[] storage = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (this.isTempElytra(storage[i])) {
                storage[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(storage);
        }
        if (this.isTempElytra(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void resyncInventory(final HumanEntity human) {
        if (human instanceof final Player player) {
            SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (this.isTempElytra(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (final ItemStack item : event.getNewItems().values()) {
            if (this.isTempElytra(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(final PlayerDropItemEvent event) {
        if (this.isTempElytra(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(final PlayerArmorStandManipulateEvent event) {
        if (this.isTempElytra(event.getPlayerItem()) || this.isTempElytra(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(final PlayerItemDamageEvent event) {
        if (this.isTempElytra(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(final ItemSpawnEvent event) {
        if (this.isTempElytra(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
