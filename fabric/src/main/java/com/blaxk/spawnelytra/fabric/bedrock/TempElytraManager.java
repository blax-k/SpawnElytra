/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.bedrock;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.integration.BedrockSupport;
import com.blaxk.spawnelytra.fabric.listener.SpawnElytra;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Texts;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
//? if >=1.21.5 {
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.TooltipDisplay;
//?} else {
/*import net.minecraft.world.item.component.Unbreakable;
*///?}

/**
 * Bedrock temporary elytra, ported from the Paper plugin. The temp elytra is marked with the
 * same custom data the Paper plugin's PersistentDataContainer produces
 * ({@code PublicBukkitValues."spawnelytra:temp_elytra" = 1b}); the original chestplate is
 * stored on the player in a persistent Fabric data attachment, which is saved together with
 * the player's inventory and survives death (copyOnDeath) like the player's PDC on Paper.
 */
public class TempElytraManager {
    private static final String BUKKIT_VALUES = "PublicBukkitValues";
    private static final String TEMP_KEY = "spawnelytra:temp_elytra";

    /** Backup of the chestplate that was replaced; an empty stack means "nothing was worn". */
    public static final AttachmentType<ItemStack> STORED_CHESTPLATE = AttachmentRegistry.<ItemStack>builder()
            .persistent(ItemStack.OPTIONAL_CODEC)
            .copyOnDeath()
            .buildAndRegister(Compat.id("spawnelytra", "stored_chestplate"));

    private final Main plugin;
    private final Map<UUID, ItemStack> disguisedChestplates = new HashMap<>();

    public TempElytraManager(final Main plugin) {
        this.plugin = plugin;
    }

    /** Forces class initialisation (attachment registration) during mod init. */
    public static void bootstrap() {
    }

    public boolean isTempElytra(final ItemStack item) {
        if (item == null || item.isEmpty() || !item.is(Items.ELYTRA)) {
            return false;
        }
        final CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        final Tag values = data.copyTag().get(BUKKIT_VALUES);
        return values instanceof final CompoundTag compound && compound.contains(TEMP_KEY);
    }

    public boolean hasTempElytraEquipped(final ServerPlayer player) {
        return this.isTempElytra(player.getItemBySlot(EquipmentSlot.CHEST));
    }

    private static ItemStack chest(final ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    private static void setChest(final ServerPlayer player, final ItemStack item) {
        player.setItemSlot(EquipmentSlot.CHEST, item == null ? ItemStack.EMPTY : item);
    }

    public void ensureEquipped(final ServerPlayer player) {
        this.removeRogueTempElytras(player);

        final ItemStack chestplate = chest(player);

        if (this.isTempElytra(chestplate)) {
            if (!this.disguisedChestplates.containsKey(player.getUUID())) {
                this.setDisguise(player, player.getAttached(STORED_CHESTPLATE));
            }
            return;
        }

        final ItemStack existingBackup = player.getAttached(STORED_CHESTPLATE);

        if (existingBackup != null) {
            if (chestplate.isEmpty()) {
                this.setDisguise(player, existingBackup);
                setChest(player, this.createTempElytra());
                return;
            }
            player.removeAttached(STORED_CHESTPLATE);
            if (!existingBackup.isEmpty()) {
                this.giveOrDrop(player, existingBackup.copy());
            }
        }

        player.setAttached(STORED_CHESTPLATE, chestplate.copy());
        this.setDisguise(player, chestplate);
        setChest(player, this.createTempElytra());
    }

    public void ensureRestored(final ServerPlayer player) {
        this.disguisedChestplates.remove(player.getUUID());
        this.removeRogueTempElytras(player);

        final ItemStack chestplate = chest(player);
        final boolean hadTemp = this.isTempElytra(chestplate);
        final ItemStack backup = player.getAttached(STORED_CHESTPLATE);

        if (!hadTemp && backup == null) {
            return;
        }

        if (hadTemp) {
            setChest(player, ItemStack.EMPTY);
        }

        if (backup != null) {
            player.removeAttached(STORED_CHESTPLATE);
            if (!backup.isEmpty()) {
                final ItemStack original = backup.copy();
                if (chest(player).isEmpty()) {
                    setChest(player, original);
                } else {
                    this.giveOrDrop(player, original);
                }
            }
        }
    }

    public void restoreAll() {
        for (final ServerPlayer player : this.plugin.getOnlinePlayers()) {
            if (this.keepEquippedWhileAirborne(player)) {
                continue;
            }
            this.ensureRestored(player);
        }
    }

    public boolean hasDisguise(final ServerPlayer wearer) {
        return this.disguisedChestplates.containsKey(wearer.getUUID());
    }

    public void sendDisguise(final ServerPlayer wearer, final ServerPlayer viewer) {
        final ItemStack shown = this.disguisedChestplates.get(wearer.getUUID());
        if (shown == null || viewer.getUUID().equals(wearer.getUUID())) {
            return;
        }
        viewer.connection.send(new ClientboundSetEquipmentPacket(wearer.getId(), List.of(Pair.of(EquipmentSlot.CHEST, shown.copy()))));
    }

    private void setDisguise(final ServerPlayer player, final ItemStack shownItem) {
        final ItemStack shown = (shownItem == null || shownItem.isEmpty()) ? ItemStack.EMPTY : shownItem.copy();
        this.disguisedChestplates.put(player.getUUID(), shown);
        final UUID uuid = player.getUUID();
        this.plugin.getScheduler().runLater(2L, () -> {
            final ServerPlayer online = this.plugin.getPlayer(uuid);
            if (online != null) {
                this.broadcastDisguise(online);
            }
        });
    }

    private void broadcastDisguise(final ServerPlayer wearer) {
        if (!this.hasDisguise(wearer)) {
            return;
        }
        for (final ServerPlayer viewer : this.viewersOf(wearer)) {
            this.sendDisguise(wearer, viewer);
        }
    }

    private Collection<ServerPlayer> viewersOf(final ServerPlayer wearer) {
        return PlayerLookup.tracking(wearer);
    }

    /** Paper {@code PlayerTrackEntityEvent}: re-send the disguise to a player that starts tracking the wearer. */
    public void onTrack(final ServerPlayer wearer, final ServerPlayer viewer) {
        if (!this.hasDisguise(wearer)) {
            return;
        }
        final UUID wearerId = wearer.getUUID();
        final UUID viewerId = viewer.getUUID();
        this.plugin.getScheduler().runLater(2L, () -> {
            final ServerPlayer w = this.plugin.getPlayer(wearerId);
            final ServerPlayer v = this.plugin.getPlayer(viewerId);
            if (w != null && v != null) {
                this.sendDisguise(w, v);
            }
        });
    }

    /** Paper {@code PlayerAdvancementDoneEvent}: hide the "Sky's the Limit" broadcast for temp elytras. */
    public boolean shouldSuppressAdvancementMessage(final ServerPlayer player, final String advancementId) {
        return "minecraft:end/elytra".equals(advancementId) && this.hasTempElytraEquipped(player);
    }

    private ItemStack createTempElytra() {
        final ItemStack elytra = new ItemStack(Items.ELYTRA);
        final CompoundTag values = new CompoundTag();
        values.putByte(TEMP_KEY, (byte) 1);
        final CompoundTag root = new CompoundTag();
        root.put(BUKKIT_VALUES, values);
        elytra.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        //? if >=1.21.5 {
        elytra.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        elytra.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true));
        //?} else {
        /*elytra.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        *///?}

        final net.kyori.adventure.text.Component lore = MessageUtil.component("temp_elytra_lore").decoration(TextDecoration.ITALIC, false);
        elytra.set(DataComponents.LORE, new ItemLore(List.of(Texts.toNative(lore))));
        return elytra;
    }

    private void giveOrDrop(final ServerPlayer player, final ItemStack item) {
        player.getInventory().add(item);
        if (!item.isEmpty()) {
            Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), item);
        }
    }

    public void onJoin(final ServerPlayer player) {
        if (this.hasTempElytraEquipped(player) && BedrockSupport.isManaged(player)) {
            final SpawnElytra instance = this.plugin.getController();
            if (instance != null && instance.resumeBedrockFlight(player)) {
                this.ensureEquipped(player);
                return;
            }
        }

        this.ensureRestored(player);
    }

    public void onQuit(final ServerPlayer player) {
        if (this.keepEquippedWhileAirborne(player)) {
            return;
        }
        this.ensureRestored(player);
    }

    private boolean keepEquippedWhileAirborne(final ServerPlayer player) {
        if (!this.hasTempElytraEquipped(player) || player.onGround()) {
            return false;
        }
        this.disguisedChestplates.remove(player.getUUID());
        return true;
    }

    /**
     * Paper {@code PlayerDeathEvent} (HIGHEST): the temp elytra never drops; the stored original
     * chestplate takes its place, so it drops (or is kept with keepInventory) instead.
     */
    public void onDeath(final ServerPlayer player) {
        if (!this.hasTempElytraEquipped(player)) {
            return;
        }
        final ItemStack backup = player.getAttached(STORED_CHESTPLATE);
        setChest(player, ItemStack.EMPTY);
        if (backup != null) {
            player.removeAttached(STORED_CHESTPLATE);
            setChest(player, backup.copy());
        }
        this.disguisedChestplates.remove(player.getUUID());
    }

    public void onRespawn(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        this.plugin.getScheduler().runLater(1L, () -> {
            final ServerPlayer online = this.plugin.getPlayer(uuid);
            if (online != null) {
                this.ensureRestored(online);
            }
        });
    }

    public void onChangedWorld(final ServerPlayer player) {
        this.ensureRestored(player);
    }

    public void onGameModeChange(final ServerPlayer player, final GameType newMode) {
        if (GameType.CREATIVE == newMode || GameType.SPECTATOR == newMode) {
            this.ensureRestored(player);
        }
    }

    /** Paper {@code InventoryClickEvent}; returns true to cancel the click. */
    public boolean onInventoryClick(final ServerPlayer player, final AbstractContainerMenu menu, final int slotId,
                                    final int button, final ClickType clickType) {
        final ItemStack current = slotId >= 0 && slotId < menu.slots.size() ? menu.getSlot(slotId).getItem() : ItemStack.EMPTY;
        if (this.isTempElytra(current) || this.isTempElytra(menu.getCarried())) {
            this.resyncInventory(player);
            return true;
        }

        if (this.hasTempElytraEquipped(player)
                && clickType == ClickType.PICKUP && button == 1
                && this.isChestEquippable(current)) {
            this.resyncInventory(player);
            return true;
        }
        return false;
    }

    private boolean isChestEquippable(final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return item.is(Items.ELYTRA) || item.is(Items.LEATHER_CHESTPLATE) || item.is(Items.CHAINMAIL_CHESTPLATE)
                || item.is(Items.IRON_CHESTPLATE) || item.is(Items.GOLDEN_CHESTPLATE)
                || item.is(Items.DIAMOND_CHESTPLATE) || item.is(Items.NETHERITE_CHESTPLATE);
    }

    /** Paper {@code PlayerInteractEvent} (right click): no swapping armor onto the temp elytra. */
    public boolean onRightClickArmor(final ServerPlayer player, final ItemStack item) {
        if (this.isChestEquippable(item) && this.hasTempElytraEquipped(player)) {
            this.resyncInventory(player);
            return true;
        }
        return false;
    }

    private void removeRogueTempElytras(final ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < 36; i++) {
            if (this.isTempElytra(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (this.isTempElytra(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private void resyncInventory(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        this.plugin.getScheduler().runLater(1L, () -> {
            final ServerPlayer online = this.plugin.getPlayer(uuid);
            if (online != null) {
                online.containerMenu.sendAllDataToRemote();
                online.inventoryMenu.sendAllDataToRemote();
            }
        });
    }
}
