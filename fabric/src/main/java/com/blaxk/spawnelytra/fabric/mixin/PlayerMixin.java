/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit EntityToggleGlideEvent (start), e.g. a Bedrock player deploying the temp elytra. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "startFallFlying", at = @At("HEAD"), cancellable = true)
    private void spawnelytra$onStartGliding(final CallbackInfo ci) {
        if ((Object) this instanceof final ServerPlayer player && Hooks.onGlideToggle(player, true)) {
            // Same as CraftBukkit on a cancelled toggle: flip the flag so the client resyncs.
            ((EntityInvoker) player).spawnelytra$setSharedFlag(7, true);
            ((EntityInvoker) player).spawnelytra$setSharedFlag(7, false);
            ci.cancel();
        }
    }
}
