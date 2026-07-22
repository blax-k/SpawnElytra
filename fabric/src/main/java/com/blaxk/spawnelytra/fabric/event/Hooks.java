/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.event;

import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.fabric.editor.EditorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import com.blaxk.spawnelytra.fabric.listener.SpawnElytra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Event bus between the Fabric callbacks/mixins and the plugin logic. Every Bukkit listener
 * of the Paper plugin receives every event: all world instances are notified (in listener
 * registration order) and an event is cancelled when any listener cancelled it. Exceptions
 * are logged instead of propagating into Minecraft, like Bukkit's event executor does.
 */
public final class Hooks {
    /** Paper fires PlayerMoveEvent only above these thresholds (squared distance / summed degrees). */
    private static final double MOVE_THRESHOLD_SQ = 1.0D / 256.0D;
    private static final float ROTATION_THRESHOLD = 10.0F;

    private static final Map<UUID, double[]> lastMove = new HashMap<>();
    private static ServerPlayer advancementPlayer;
    private static String advancementId;

    private Hooks() {
    }

    private static boolean anyInstance(final String event, final Predicate<SpawnElytra> handler) {
        final Main plugin = Main.get();
        if (plugin == null) {
            return false;
        }
        boolean cancelled = false;
        for (final SpawnElytra instance : plugin.instances()) {
            try {
                cancelled |= handler.test(instance);
            } catch (final Throwable t) {
                plugin.getLogger().error("Could not pass {} to Spawn Elytra", event, t);
            }
        }
        return cancelled;
    }

    private static void eachInstance(final String event, final Consumer<SpawnElytra> handler) {
        anyInstance(event, instance -> {
            handler.accept(instance);
            return false;
        });
    }

    private static boolean temp(final String event, final Predicate<TempElytraManager> handler) {
        final Main plugin = Main.get();
        if (plugin == null || plugin.getTempElytraManager() == null) {
            return false;
        }
        try {
            return handler.test(plugin.getTempElytraManager());
        } catch (final Throwable t) {
            plugin.getLogger().error("Could not pass {} to Spawn Elytra", event, t);
            return false;
        }
    }

    public static boolean onToggleFlight(final ServerPlayer player, final boolean flying) {
        return anyInstance("PlayerToggleFlightEvent", i -> i.onDoubleJump(player, flying));
    }

    public static boolean onSwapHands(final ServerPlayer player) {
        return anyInstance("PlayerSwapHandItemsEvent", i -> i.onSwapItem(player));
    }

    public static void onSneak(final ServerPlayer player, final boolean sneaking) {
        eachInstance("PlayerToggleSneakEvent", i -> i.onPlayerSneak(player, sneaking));
    }

    public static void onCommand(final ServerPlayer player, final String message) {
        eachInstance("PlayerCommandPreprocessEvent", i -> i.onPlayerCommandPreprocess(player, message));
    }

    public static boolean onGlideToggle(final ServerPlayer player, final boolean gliding) {
        return anyInstance("EntityToggleGlideEvent", i -> i.onToggleGlide(player, gliding));
    }

    public static boolean allowDamage(final ServerPlayer player, final DamageSource source) {
        return !anyInstance("EntityDamageEvent", i -> i.onEntityDamage(player, source));
    }

    /** Bukkit PlayerInteractEvent (right click with an item). Returns true to deny the use. */
    public static boolean onUseItem(final ServerPlayer player, final ItemStack stack) {
        final boolean fireworks = anyInstance("PlayerInteractEvent", i -> i.onPlayerInteract(player, stack));
        final boolean armor = temp("PlayerInteractEvent", m -> m.onRightClickArmor(player, stack));
        return fireworks || armor;
    }

    /** Bukkit PlayerInteractEvent (right click on a block). Returns true to deny the interaction. */
    public static boolean onUseBlock(final ServerPlayer player, final ItemStack stack) {
        return anyInstance("PlayerInteractEvent", i -> i.onPlayerInteract(player, stack));
    }

    public static void onGameModeChange(final ServerPlayer player, final GameType newMode) {
        eachInstance("PlayerGameModeChangeEvent", i -> i.onGameModeChange(player, newMode));
        temp("PlayerGameModeChangeEvent", m -> {
            m.onGameModeChange(player, newMode);
            return false;
        });
    }

    public static void onDeath(final ServerPlayer player) {
        final EditorManager editor = editor();
        if (editor != null) {
            try {
                editor.onDeath(player);
            } catch (final Throwable t) {
                Main.get().getLogger().error("Could not pass PlayerDeathEvent to Spawn Elytra", t);
            }
        }
        temp("PlayerDeathEvent", m -> {
            m.onDeath(player);
            return false;
        });
    }

    public static boolean onContainerClick(final ServerPlayer player, final AbstractContainerMenu menu, final int slotId,
                                           final int button, final ClickType clickType) {
        if (isEditorProtected(player)) {
            resyncInventory(player);
            return true;
        }
        return temp("InventoryClickEvent", m -> m.onInventoryClick(player, menu, slotId, button, clickType));
    }

    public static void onMovePacket(final ServerPlayer player) {
        if (Main.get() == null) {
            return;
        }
        final double[] last = lastMove.get(player.getUUID());
        final double x = player.getX();
        final double y = player.getY();
        final double z = player.getZ();
        final float yaw = player.getYRot();
        final float pitch = player.getXRot();
        if (last != null) {
            final double dx = last[0] - x;
            final double dy = last[1] - y;
            final double dz = last[2] - z;
            final double delta = dx * dx + dy * dy + dz * dz;
            final float deltaAngle = Math.abs((float) last[3] - yaw) + Math.abs((float) last[4] - pitch);
            if (delta <= MOVE_THRESHOLD_SQ && deltaAngle <= ROTATION_THRESHOLD) {
                return;
            }
        }
        lastMove.put(player.getUUID(), new double[]{x, y, z, yaw, pitch});
        eachInstance("PlayerMoveEvent", i -> i.onPlayerMove(player));
    }

    public static void resetMoveReference(final ServerPlayer player) {
        if (player != null) {
            lastMove.put(player.getUUID(), new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
        }
    }

    public static void forget(final UUID uuid) {
        lastMove.remove(uuid);
    }

    public static void reset() {
        lastMove.clear();
        advancementPlayer = null;
        advancementId = null;
    }

    // ---- zone editor input / protection ---------------------------------------------------

    private static EditorManager editor() {
        final Main plugin = Main.get();
        return plugin == null ? null : plugin.getEditorManager();
    }

    /** True while the player is in the zone editor (tool hotbar): inventory changes are blocked. */
    public static boolean isEditorProtected(final ServerPlayer player) {
        final EditorManager editor = editor();
        return editor != null && player != null && editor.isEditing(player.getUUID());
    }

    public static void resyncInventory(final ServerPlayer player) {
        player.containerMenu.sendAllDataToRemote();
        player.inventoryMenu.sendAllDataToRemote();
    }

    /** Hotbar slot change; {@code true} cancels it (the editor used it as a scroll step). */
    public static boolean onHeldSlotChange(final ServerPlayer player, final int newSlot) {
        final EditorManager editor = editor();
        if (editor == null || !editor.isEditing(player.getUUID())) {
            return false;
        }
        try {
            return editor.onHeldSlotChange(player, newSlot);
        } catch (final Throwable t) {
            Main.get().getLogger().error("Could not pass PlayerItemHeldEvent to Spawn Elytra", t);
            return false;
        }
    }

    public static void onSwing(final ServerPlayer player) {
        final EditorManager editor = editor();
        if (editor != null && editor.isEditing(player.getUUID())) {
            try {
                editor.onSwing(player);
            } catch (final Throwable t) {
                Main.get().getLogger().error("Could not pass PlayerInteractEvent to Spawn Elytra", t);
            }
        }
    }

    /** Left click on a block; {@code true} denies it (no block breaking with editor tools). */
    public static boolean onAttackBlock(final ServerPlayer player, final BlockPos pos) {
        final EditorManager editor = editor();
        if (editor == null || !editor.isEditing(player.getUUID())) {
            return false;
        }
        try {
            editor.onLeftClick(player, pos);
        } catch (final Throwable t) {
            Main.get().getLogger().error("Could not pass PlayerInteractEvent to Spawn Elytra", t);
        }
        return true;
    }

    /** Right click (on a block when {@code pos} is set); {@code true} denies the vanilla use. */
    public static boolean onEditorUse(final ServerPlayer player, final BlockPos pos) {
        final EditorManager editor = editor();
        if (editor == null || !editor.isEditing(player.getUUID())) {
            return false;
        }
        try {
            editor.onRightClick(player, pos);
        } catch (final Throwable t) {
            Main.get().getLogger().error("Could not pass PlayerInteractEvent to Spawn Elytra", t);
        }
        return true;
    }

    /** Custom click action (dialog button) with a {@code spawnelytra:} id; {@code true} = handled. */
    public static boolean onCustomClick(final ServerPlayer player, final Identifier id, final Optional<Tag> payload) {
        final Main plugin = Main.get();
        if (plugin == null || player == null || !"spawnelytra".equals(id.getNamespace())) {
            return false;
        }
        try {
            plugin.getMenuService().onCustomClick(player, id.getPath(), payload.orElse(null));
        } catch (final Throwable t) {
            plugin.getLogger().error("Could not handle dialog action {} for {}", id, player.getGameProfile(), t);
        }
        return true;
    }

    // ---- advancement broadcast suppression ---------------------------------------------------

    public static void beginAdvancement(final ServerPlayer player, final String id) {
        advancementPlayer = player;
        advancementId = id;
    }

    public static void endAdvancement() {
        advancementPlayer = null;
        advancementId = null;
    }

    public static boolean isSuppressingAdvancementMessage() {
        final ServerPlayer player = advancementPlayer;
        final String id = advancementId;
        return player != null && id != null
                && temp("PlayerAdvancementDoneEvent", m -> m.shouldSuppressAdvancementMessage(player, id));
    }
}
