/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paper PlayerAdvancementDoneEvent#message(null): while "Sky's the Limit" is awarded to a
 * player wearing the Bedrock temp elytra, the chat broadcast is suppressed.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"))
    private void spawnelytra$beginAward(final AdvancementHolder advancement, final String criterion, final CallbackInfoReturnable<Boolean> cir) {
        Hooks.beginAdvancement(this.player, advancement.id().toString());
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void spawnelytra$endAward(final AdvancementHolder advancement, final String criterion, final CallbackInfoReturnable<Boolean> cir) {
        Hooks.endAdvancement();
    }
}
