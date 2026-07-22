/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bukkit InventoryClickEvent: the Bedrock temp elytra cannot be moved. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void spawnelytra$onClick(final int slotId, final int button, final ClickType clickType, final Player player, final CallbackInfo ci) {
        if (player instanceof final ServerPlayer serverPlayer
                && Hooks.onContainerClick(serverPlayer, (AbstractContainerMenu) (Object) this, slotId, button, clickType)) {
            ci.cancel();
        }
    }
}
