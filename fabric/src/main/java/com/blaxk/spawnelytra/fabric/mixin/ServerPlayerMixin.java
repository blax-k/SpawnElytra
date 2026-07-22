/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bukkit PlayerGameModeChangeEvent (before the change) and PlayerDeathEvent (temp elytra drops). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "setGameMode", at = @At("HEAD"))
    private void spawnelytra$onGameModeChange(final GameType gameMode, final CallbackInfoReturnable<Boolean> cir) {
        final ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.gameMode.getGameModeForPlayer() != gameMode) {
            Hooks.onGameModeChange(player, gameMode);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void spawnelytra$onDeath(final DamageSource source, final CallbackInfo ci) {
        Hooks.onDeath((ServerPlayer) (Object) this);
    }
}
