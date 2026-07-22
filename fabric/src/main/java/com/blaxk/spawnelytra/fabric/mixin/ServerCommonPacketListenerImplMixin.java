/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.6 {
import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * Custom click actions (dialog buttons and {@code custom} click events, Minecraft 1.21.6+) in
 * the {@code spawnelytra:} namespace go to the menus; everything else continues to vanilla.
 * On older nodes this mixin is empty.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    //? if >=1.21.6 {
    @Inject(method = "handleCustomClickAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ServerboundCustomClickActionPacket;id()Lnet/minecraft/resources/Identifier;", ordinal = 0),
            cancellable = true)
    private void spawnelytra$onCustomClick(final ServerboundCustomClickActionPacket packet, final CallbackInfo ci) {
        if ((Object) this instanceof final ServerGamePacketListenerImpl game
                && Hooks.onCustomClick(game.player, packet.id(), packet.payload())) {
            ci.cancel();
        }
    }
    //?}
}
