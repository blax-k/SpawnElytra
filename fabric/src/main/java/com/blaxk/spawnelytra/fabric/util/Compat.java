/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import com.blaxk.spawnelytra.fabric.mixin.EntityInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.9 {
import net.minecraft.server.players.NameAndId;
//?}
//? if <1.21.2 {
/*import org.joml.Vector3f;
*///?}

/**
 * All Minecraft calls whose signatures differ between the supported versions live here,
 * so the gameplay code stays identical for every Stonecutter node.
 */
public final class Compat {
    private static final int FLAG_FALL_FLYING = 7;

    private Compat() {
    }

    public static ServerLevel level(final ServerPlayer player) {
        //? if >=1.21.6 {
        return player.level();
        //?} else {
        /*return player.serverLevel();
        *///?}
    }

    public static MinecraftServer server(final ServerPlayer player) {
        return level(player).getServer();
    }

    public static Identifier id(final String namespace, final String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    /** {@code ResourceKey#location()}, renamed to {@code identifier()} in 1.21.11. */
    public static Identifier keyId(final net.minecraft.resources.ResourceKey<?> key) {
        //? if >=1.21.11 {
        return key.identifier();
        //?} else {
        /*return key.location();
        *///?}
    }

    public static Identifier parseId(final String id) {
        return Identifier.tryParse(id);
    }

    /** Equivalent of Bukkit's {@code Player#isOp()}: membership in the server operator list. */
    public static boolean isOp(final ServerPlayer player) {
        final MinecraftServer server = server(player);
        if (server == null) {
            return false;
        }
        //? if >=1.21.9 {
        return server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
        //?} else {
        /*return server.getPlayerList().isOp(player.getGameProfile());
        *///?}
    }

    public static GameType gameMode(final ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer();
    }

    /** Block position of the world spawn, like Bukkit's {@code World#getSpawnLocation()}. */
    public static BlockPos spawnPos(final ServerLevel level) {
        //? if >=1.21.9 {
        return level.getRespawnData().pos();
        //?} else {
        /*return level.getSharedSpawnPos();
        *///?}
    }

    /** Bukkit's {@code Entity#setVelocity}: set motion and mark it for syncing to the client. */
    public static void setVelocity(final ServerPlayer player, final Vec3 velocity) {
        player.setDeltaMovement(velocity);
        //? if >=26.3 {
        /*player.syncVelocity = true;
        *///?} else {
        player.hurtMarked = true;
        //?}
    }

    /** Bukkit's {@code LivingEntity#setGliding}: toggles the shared fall-flying flag without events. */
    public static void setGliding(final ServerPlayer player, final boolean gliding) {
        ((EntityInvoker) player).spawnelytra$setSharedFlag(FLAG_FALL_FLYING, gliding);
    }

    public static ParticleOptions dust(final int rgb, final float size) {
        //? if >=1.21.2 {
        return new DustParticleOptions(rgb, size);
        //?} else {
        /*return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F), size);
        *///?}
    }

    /** Bukkit's {@code Player#spawnParticle}: sends the particle packet to this player only. */
    public static void particle(final ServerPlayer player, final ParticleOptions options, final double x, final double y, final double z,
                                final int count, final double dx, final double dy, final double dz, final double speed) {
        //? if >=1.21.4 {
        player.connection.send(new ClientboundLevelParticlesPacket(options, false, false, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count));
        //?} else {
        /*player.connection.send(new ClientboundLevelParticlesPacket(options, false, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count));
        *///?}
    }

    /** Bukkit's {@code Player#playSound(Location, Sound, float, float)} (MASTER category, this player only). */
    public static void playSound(final ServerPlayer player, final Holder<SoundEvent> sound, final double x, final double y, final double z,
                                 final float volume, final float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, x, y, z, volume, pitch, player.getRandom().nextLong()));
    }

    /** A server bossbar (26.3 added an explicit id). */
    public static net.minecraft.server.level.ServerBossEvent bossBar(final net.minecraft.network.chat.Component name,
                                                                    final net.minecraft.world.BossEvent.BossBarColor color,
                                                                    final net.minecraft.world.BossEvent.BossBarOverlay overlay) {
        //? if >=26.1 {
        /*return new net.minecraft.server.level.ServerBossEvent(java.util.UUID.randomUUID(), name, color, overlay);
        *///?} else {
        return new net.minecraft.server.level.ServerBossEvent(name, color, overlay);
        //?}
    }

    public static int selectedSlot(final ServerPlayer player) {
        //? if >=1.21.5 {
        return player.getInventory().getSelectedSlot();
        //?} else {
        /*return player.getInventory().selected;
        *///?}
    }

    /** Selects a hotbar slot server side and tells the client. */
    public static void selectSlot(final ServerPlayer player, final int slot) {
        //? if >=1.21.5 {
        player.getInventory().setSelectedSlot(slot);
        //?} else {
        /*player.getInventory().selected = slot;
        *///?}
        sendSelectedSlot(player, slot);
    }

    /** Re-sends the held slot (the client keeps or returns to it). */
    public static void sendSelectedSlot(final ServerPlayer player, final int slot) {
        //? if >=1.21.2 {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(slot));
        //?} else {
        /*player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
        *///?}
    }

    /** {@code CompoundTag#getString} (an Optional since 1.21.5); {@code null} when absent. */
    public static String tagString(final net.minecraft.nbt.CompoundTag tag, final String key) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        //? if >=1.21.5 {
        return tag.getString(key).orElse(null);
        //?} else {
        /*return tag.getString(key);
        *///?}
    }

    /** Teleports a player (also across dimensions), keeping the rotation. */
    public static void teleport(final ServerPlayer player, final ServerLevel level, final double x, final double y, final double z) {
        //? if >=1.21.2 {
        player.teleportTo(level, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        //?} else {
        /*player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        *///?}
    }

    public static java.util.Set<String> keys(final net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.keySet();
        //?} else {
        /*return tag.getAllKeys();
        *///?}
    }

    /** The string content of a tag (StringTag value, otherwise its SNBT form). */
    public static String stringOf(final net.minecraft.nbt.Tag tag) {
        //? if >=1.21.5 {
        return tag.asString().orElse(tag.toString());
        //?} else {
        /*return tag.getAsString();
        *///?}
    }

    /** A known (cached) player: Bukkit's {@code getOfflinePlayerIfCached} + {@code hasPlayedBefore}. */
    public record KnownPlayer(java.util.UUID id, String name) {
    }

    /** The cached profile of a player who has played on this server before, or {@code null}. */
    public static KnownPlayer knownPlayer(final MinecraftServer server, final String name) {
        //? if >=1.21.9 {
        final KnownPlayer found = server.services().nameToIdCache().get(name).map(n -> new KnownPlayer(n.id(), n.name())).orElse(null);
        //?} else {
        /*final KnownPlayer found = server.getProfileCache() == null ? null
                : server.getProfileCache().get(name).map(p -> new KnownPlayer(p.getId(), p.getName())).orElse(null);
        *///?}
        if (found == null) {
            return null;
        }
        final java.nio.file.Path dat = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                .resolve(found.id() + ".dat");
        return java.nio.file.Files.exists(dat) ? found : null;
    }

    public static int intOf(final net.minecraft.nbt.NumericTag tag) {
        //? if >=1.21.5 {
        return tag.intValue();
        //?} else {
        /*return tag.getAsInt();
        *///?}
    }

    public static float floatOf(final net.minecraft.nbt.NumericTag tag) {
        //? if >=1.21.5 {
        return tag.floatValue();
        //?} else {
        /*return tag.getAsFloat();
        *///?}
    }

    public static int dataVersion() {
        //? if >=1.21.6 {
        return SharedConstants.getCurrentVersion().dataVersion().version();
        //?} else {
        /*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        *///?}
    }

    public static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
