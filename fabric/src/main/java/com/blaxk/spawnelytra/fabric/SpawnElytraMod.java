/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric;

import com.blaxk.spawnelytra.fabric.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.fabric.command.CommandHandler;
import com.blaxk.spawnelytra.fabric.event.Hooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
*///?} else {
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
//?}
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
/*import net.minecraft.world.InteractionResultHolder;
*///?}

/**
 * Mod entrypoint. Server-side only logic (works with vanilla clients and on the integrated
 * server): the plugin instance lives from SERVER_STARTED until SERVER_STOPPING, and the
 * Fabric API callbacks below are the counterparts of the Bukkit events the Paper plugin uses.
 */
public final class SpawnElytraMod implements ModInitializer {
    @Override
    public void onInitialize() {
        TempElytraManager.bootstrap();

        ServerLifecycleEvents.SERVER_STARTED.register(Main::enable);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Main.disable();
            Hooks.reset();
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            final Main plugin = Main.get();
            if (plugin != null) {
                plugin.getScheduler().tick();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CommandHandler.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            final Main plugin = Main.get();
            if (plugin != null) {
                final ServerPlayer player = handler.getPlayer();
                plugin.onPlayerJoin(player);
                plugin.getTempElytraManager().onJoin(player);
                plugin.getEditorManager().onJoin(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            final ServerPlayer player = handler.getPlayer();
            final Main plugin = Main.get();
            if (plugin != null) {
                plugin.onPlayerQuit(player);
                plugin.getTempElytraManager().onQuit(player);
            }
            Hooks.forget(player.getUUID());
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof final ServerPlayer player) || Hooks.allowDamage(player, source));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            final Main plugin = Main.get();
            if (plugin != null) {
                plugin.getTempElytraManager().onRespawn(newPlayer);
                plugin.getEditorManager().onRespawn(newPlayer);
            }
        });
        //? if >=26.1 {
        /*ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
        *///?} else {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
        //?}
            final Main plugin = Main.get();
            if (plugin != null) {
                plugin.getTempElytraManager().onChangedWorld(player);
                plugin.getEditorManager().onWorldChange(player);
                plugin.getController().onChangedWorld(player);
            }
        });
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            final Main plugin = Main.get();
            if (plugin != null && tracked instanceof final ServerPlayer wearer) {
                plugin.getTempElytraManager().onTrack(wearer, viewer);
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            final boolean deny = player instanceof final ServerPlayer serverPlayer
                    && (Hooks.onEditorUse(serverPlayer, null) || Hooks.onUseItem(serverPlayer, player.getItemInHand(hand)));
            //? if >=1.21.2 {
            return deny ? InteractionResult.FAIL : InteractionResult.PASS;
            //?} else {
            /*return deny ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand));
            *///?}
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            final boolean deny = player instanceof final ServerPlayer serverPlayer
                    && (Hooks.onEditorUse(serverPlayer, hitResult.getBlockPos()) || Hooks.onUseBlock(serverPlayer, player.getItemInHand(hand)));
            return deny ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                player instanceof final ServerPlayer serverPlayer && Hooks.onAttackBlock(serverPlayer, pos)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        // Zone editor: no entity interaction (item frames, armor stands, name tags) with the tools.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                player instanceof final ServerPlayer serverPlayer && Hooks.isEditorProtected(serverPlayer)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                player instanceof final ServerPlayer serverPlayer && Hooks.isEditorProtected(serverPlayer)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
