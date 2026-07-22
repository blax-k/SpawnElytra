/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
//? if >=26.3 {
/*import net.minecraft.network.protocol.game.ServerboundPunchPacket;
*///?} else {
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
//?}
//? if >=1.21.4 {
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
*///?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-level Bukkit events that Fabric API has no equivalent for: flight toggle (double
 * jump while allow-flight is set), offhand swap, player movement and typed commands. All
 * injections run after the packet handling has been moved to the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    private static final String ENSURE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    /** Bukkit PlayerToggleFlightEvent; a cancelled toggle re-sends the abilities like CraftBukkit. */
    @Inject(method = "handlePlayerAbilities", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onToggleFlight(final ServerboundPlayerAbilitiesPacket packet, final CallbackInfo ci) {
        if (this.player.getAbilities().mayfly && this.player.getAbilities().flying != packet.isFlying()
                && Hooks.onToggleFlight(this.player, packet.isFlying())) {
            this.player.onUpdateAbilities();
            ci.cancel();
        }
    }

    /** Bukkit PlayerSwapHandItemsEvent; drop / swap protection of the zone editor tools. */
    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onSwapHands(final ServerboundPlayerActionPacket packet, final CallbackInfo ci) {
        final ServerboundPlayerActionPacket.Action action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.DROP_ITEM || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)
                && Hooks.isEditorProtected(this.player)) {
            Hooks.resyncInventory(this.player);
            ci.cancel();
            return;
        }
        if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && !this.player.isSpectator()
                && Hooks.onSwapHands(this.player)) {
            ci.cancel();
        }
    }

    /**
     * Zone editor "scroll": while sneaking with an editor tool, a hotbar slot change is cancelled
     * and its direction is used as +1/-1 (the client is told to stay on the old slot).
     */
    @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onHeldSlot(final ServerboundSetCarriedItemPacket packet, final CallbackInfo ci) {
        if (Hooks.onHeldSlotChange(this.player, packet.getSlot())) {
            ci.cancel();
        }
    }

    /** Left click into the air (arm swing / punch) for the zone editor tools. */
    //? if >=26.3 {
    /*@Inject(method = "handlePunch", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void spawnelytra$onSwing(final ServerboundPunchPacket packet, final CallbackInfo ci) {
    *///?} else {
    @Inject(method = "handleAnimate", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void spawnelytra$onSwing(final ServerboundSwingPacket packet, final CallbackInfo ci) {
    //?}
        Hooks.onSwing(this.player);
    }

    /** Creative inventory edits would let the editor tools out (or real items in). */
    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onCreativeSlot(final ServerboundSetCreativeModeSlotPacket packet, final CallbackInfo ci) {
        if (Hooks.isEditorProtected(this.player)) {
            Hooks.resyncInventory(this.player);
            ci.cancel();
        }
    }

    //? if >=1.21.4 {
    @Inject(method = "handlePickItemFromBlock", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onPickBlock(final ServerboundPickItemFromBlockPacket packet, final CallbackInfo ci) {
        if (Hooks.isEditorProtected(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePickItemFromEntity", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onPickEntity(final ServerboundPickItemFromEntityPacket packet, final CallbackInfo ci) {
        if (Hooks.isEditorProtected(this.player)) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "handlePickItem", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void spawnelytra$onPickItem(final ServerboundPickItemPacket packet, final CallbackInfo ci) {
        if (Hooks.isEditorProtected(this.player)) {
            ci.cancel();
        }
    }
    *///?}

    /** Bukkit PlayerMoveEvent (same position/rotation thresholds as Paper). */
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void spawnelytra$onMove(final ServerboundMovePlayerPacket packet, final CallbackInfo ci) {
        Hooks.onMovePacket(this.player);
    }

    /**
     * Like Paper's internalTeleport, a teleport (join, respawn, /tp) moves the reference point of
     * the PlayerMoveEvent thresholds to the destination, so no move event fires until the player
     * actually moves away from it.
     */
    @Inject(method = "teleport*", at = @At("RETURN"))
    private void spawnelytra$onTeleport(final CallbackInfo ci) {
        Hooks.resetMoveReference(this.player);
    }

    /** Bukkit PlayerCommandPreprocessEvent for unsigned commands. */
    @Inject(method = "performUnsignedChatCommand", at = @At("HEAD"))
    private void spawnelytra$onCommand(final String command, final CallbackInfo ci) {
        Hooks.onCommand(this.player, "/" + command);
    }

    /** Bukkit PlayerCommandPreprocessEvent for signed commands. */
    @Inject(method = "performSignedChatCommand", at = @At("HEAD"))
    private void spawnelytra$onSignedCommand(final ServerboundChatCommandSignedPacket packet, final LastSeenMessages lastSeen, final CallbackInfo ci) {
        Hooks.onCommand(this.player, "/" + packet.command());
    }
}
