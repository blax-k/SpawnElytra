/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bukkit PlayerToggleSneakEvent: fired whenever the server-side sneak state of a player
 * changes (player command packet before 1.21.6, player input packet since).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract boolean isShiftKeyDown();

    @Inject(method = "setShiftKeyDown", at = @At("HEAD"))
    private void spawnelytra$onSneak(final boolean sneaking, final CallbackInfo ci) {
        if ((Object) this instanceof final ServerPlayer player && sneaking != this.isShiftKeyDown()) {
            Hooks.onSneak(player, sneaking);
        }
    }
}
