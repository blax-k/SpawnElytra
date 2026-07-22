/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

import com.blaxk.spawnelytra.fabric.mixin.BlockDisplayAccessor;
import com.blaxk.spawnelytra.fabric.mixin.DisplayAccessor;
import com.blaxk.spawnelytra.fabric.mixin.EntityAccessor;
import com.blaxk.spawnelytra.fabric.mixin.TextDisplayAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Packet-only display entities for the zone editor preview. Nothing exists on the server: the
 * entities are announced to exactly one player with add/metadata packets and removed with a
 * remove packet, so they are never saved, ticked or visible to anybody else. Ids count down
 * from {@code Integer.MAX_VALUE}, far away from the ids the server hands out.
 */
public final class FakeEntities {
    private static final AtomicInteger IDS = new AtomicInteger(Integer.MAX_VALUE - 16);
    private static final int FULL_BRIGHT = (15 << 4) | (15 << 20);
    private static final byte FLAG_GLOWING = 0x40;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte TEXT_SHADOW_SEE_THROUGH = 0x01 | 0x02;

    //? if >=26.3 {
    /*private static final EntityType<?> BLOCK_DISPLAY = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY;
    private static final EntityType<?> TEXT_DISPLAY = net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY;
    *///?} else {
    private static final EntityType<?> BLOCK_DISPLAY = EntityType.BLOCK_DISPLAY;
    private static final EntityType<?> TEXT_DISPLAY = EntityType.TEXT_DISPLAY;
    //?}

    private FakeEntities() {
    }

    public static int nextId() {
        return IDS.getAndDecrement();
    }

    private static void add(final ServerPlayer player, final int id, final EntityType<?> type, final double x, final double y, final double z) {
        player.connection.send(new ClientboundAddEntityPacket(id, UUID.randomUUID(), x, y, z, 0.0F, 0.0F, type, 0, Vec3.ZERO, 0.0D));
    }

    /**
     * A glowing bar from {@code (x, y, z)} along the horizontal direction {@code angle}
     * (radians, 0 = +X, counter clockwise seen from above) with the given length.
     */
    public static int horizontalBar(final ServerPlayer player, final double x, final double y, final double z, final double dx, final double dz,
                                    final float thickness, final BlockState block, final int glowColor) {
        final float length = (float) Math.sqrt(dx * dx + dz * dz);
        final float yaw = (float) Math.atan2(-dz, dx);
        final Quaternionf rotation = new Quaternionf().rotateY(yaw);
        final Vector3f translation = rotation.transform(new Vector3f(0.0F, -thickness / 2.0F, -thickness / 2.0F));
        return blockDisplay(player, x, y, z, translation, rotation, new Vector3f(Math.max(length, thickness), thickness, thickness), block, glowColor);
    }

    /** A glowing vertical post of the given height, centered on {@code (x, z)}. */
    public static int verticalBar(final ServerPlayer player, final double x, final double y, final double z, final float height,
                                  final float thickness, final BlockState block, final int glowColor) {
        return blockDisplay(player, x, y, z, new Vector3f(-thickness / 2.0F, 0.0F, -thickness / 2.0F), new Quaternionf(),
                new Vector3f(thickness, height, thickness), block, glowColor);
    }

    /** A small glowing cube centered on the position (markers such as polygon points). */
    public static int cube(final ServerPlayer player, final double x, final double y, final double z, final float size,
                           final BlockState block, final int glowColor) {
        return blockDisplay(player, x, y, z, new Vector3f(-size / 2.0F, -size / 2.0F, -size / 2.0F), new Quaternionf(),
                new Vector3f(size, size, size), block, glowColor);
    }

    private static int blockDisplay(final ServerPlayer player, final double x, final double y, final double z, final Vector3f translation,
                                    final Quaternionf rotation, final Vector3f scale, final BlockState block, final int glowColor) {
        final int id = nextId();
        add(player, id, BLOCK_DISPLAY, x, y, z);
        final List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        data.add(SynchedEntityData.DataValue.create(EntityAccessor.spawnelytra$sharedFlags(), glowColor >= 0 ? FLAG_GLOWING : (byte) 0));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$translation(), translation));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$scale(), scale));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$leftRotation(), rotation));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$brightness(), FULL_BRIGHT));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$viewRange(), 4.0F));
        if (glowColor >= 0) {
            data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$glowColor(), glowColor));
        }
        data.add(SynchedEntityData.DataValue.create(BlockDisplayAccessor.spawnelytra$blockState(), block));
        player.connection.send(new ClientboundSetEntityDataPacket(id, data));
        return id;
    }

    /** A billboard text label (always faces the player). */
    public static int text(final ServerPlayer player, final double x, final double y, final double z, final Component text) {
        final int id = nextId();
        add(player, id, TEXT_DISPLAY, x, y, z);
        final List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$billboard(), BILLBOARD_CENTER));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$brightness(), FULL_BRIGHT));
        data.add(SynchedEntityData.DataValue.create(DisplayAccessor.spawnelytra$viewRange(), 4.0F));
        data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.spawnelytra$background(), 0x80000000));
        data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.spawnelytra$lineWidth(), 400));
        data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.spawnelytra$styleFlags(), TEXT_SHADOW_SEE_THROUGH));
        data.add(SynchedEntityData.DataValue.create(TextDisplayAccessor.spawnelytra$text(), text));
        player.connection.send(new ClientboundSetEntityDataPacket(id, data));
        return id;
    }

    public static void updateText(final ServerPlayer player, final int id, final Component text) {
        player.connection.send(new ClientboundSetEntityDataPacket(id,
                List.of(SynchedEntityData.DataValue.create(TextDisplayAccessor.spawnelytra$text(), text))));
    }

    public static void remove(final ServerPlayer player, final Collection<Integer> ids) {
        if (player == null || ids.isEmpty()) {
            return;
        }
        final IntList list = new IntArrayList(ids.size());
        for (final Integer id : ids) {
            list.add(id.intValue());
        }
        player.connection.send(new ClientboundRemoveEntitiesPacket(list));
    }
}
