/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Bukkit EntityToggleGlideEvent (stop): vanilla clears the fall-flying flag every tick when no
 * glider is equipped; a cancelled toggle keeps the player gliding.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyArg(method = "updateFallFlying",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"),
            index = 1)
    private boolean spawnelytra$keepGliding(final boolean gliding) {
        if (!gliding && (Object) this instanceof final ServerPlayer player && player.isFallFlying()
                && Hooks.onGlideToggle(player, false)) {
            return true;
        }
        return gliding;
    }
}
