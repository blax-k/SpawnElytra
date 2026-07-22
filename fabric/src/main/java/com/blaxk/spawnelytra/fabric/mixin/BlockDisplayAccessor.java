/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block state key of block displays (packet-only editor preview). */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
    @Accessor("DATA_BLOCK_STATE_ID")
    static EntityDataAccessor<BlockState> spawnelytra$blockState() {
        throw new AssertionError();
    }
}
