/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the shared entity flags (flag 7 = fall flying), used for Bukkit's setGliding. */
@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("setSharedFlag")
    void spawnelytra$setSharedFlag(int flag, boolean value);
}
