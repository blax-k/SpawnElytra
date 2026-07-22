/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.integration;

import com.blaxk.spawnelytra.common.stats.StatsFormat;
import com.blaxk.spawnelytra.common.tier.PermissionTier;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.data.PlayerDataManager;
import com.blaxk.spawnelytra.fabric.listener.SpawnElytra;
import com.blaxk.spawnelytra.fabric.util.Compat;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiFunction;

/**
 * Text Placeholder API (eu.pb4) integration, the Fabric counterpart of the PlaceholderAPI
 * expansion. Same identifier and names: {@code %spawnelytra:fly_count%},
 * {@code %spawnelytra:boost_count%}, {@code %spawnelytra:total_count%},
 * {@code %spawnelytra:flying%}, {@code %spawnelytra:in_area%},
 * {@code %spawnelytra:boosts_remaining%} and (1.6) {@code flights}, {@code distance},
 * {@code boosts}, {@code glide_time}, {@code longest_flight}, {@code zone}, {@code enabled},
 * {@code tier}. Only loaded when placeholder-api is installed.
 */
public final class PlaceholderIntegration {
    private PlaceholderIntegration() {
    }

    public static void register() {
        register("fly_count", (plugin, player) ->
                String.valueOf(data(plugin, player).getFlyCount()));
        register("boost_count", (plugin, player) ->
                String.valueOf(data(plugin, player).getBoostCount()));
        register("total_count", (plugin, player) -> {
            final PlayerDataManager.PlayerData data = data(plugin, player);
            return String.valueOf(data.getFlyCount() + data.getBoostCount());
        });
        register("flying", (plugin, player) -> {
            final SpawnElytra instance = plugin.getController();
            return String.valueOf(instance != null && instance.isFlying(player));
        });
        register("in_area", (plugin, player) -> {
            final SpawnElytra instance = plugin.getController();
            return String.valueOf(instance != null && instance.isInSpawnArea(player));
        });
        register("boosts_remaining", (plugin, player) -> {
            final SpawnElytra instance = plugin.getController();
            return String.valueOf(instance != null ? instance.getBoostsRemaining(player) : 0);
        });
        for (final String stat : new String[]{"flights", "distance", "boosts", "glide_time", "longest_flight"}) {
            register(stat, (plugin, player) -> StatsFormat.placeholder(data(plugin, player).getStats(), stat));
        }
        register("zone", (plugin, player) -> {
            final Zone zone = plugin.getZoneService().zoneAt(player);
            return zone == null ? "" : zone.name();
        });
        register("enabled", (plugin, player) -> String.valueOf(data(plugin, player).isEnabled()));
        register("tier", (plugin, player) -> {
            final PermissionTier tier = plugin.getZoneService().tierOf(player);
            return tier == null ? "" : tier.name();
        });
    }

    private static PlayerDataManager.PlayerData data(final Main plugin, final ServerPlayer player) {
        return plugin.getPlayerDataManager().getPlayerData(player.getUUID());
    }

    private static PlaceholderResult resolve(final Object contextPlayer, final BiFunction<Main, ServerPlayer, String> value) {
        final Main plugin = Main.get();
        if (plugin == null || !(contextPlayer instanceof final ServerPlayer player)) {
            return PlaceholderResult.value("");
        }
        return PlaceholderResult.value(value.apply(plugin, player));
    }

    private static void register(final String name, final BiFunction<Main, ServerPlayer, String> value) {
        //? if >=26.1 {
        /*Placeholders.registerServer(Compat.id("spawnelytra", name), (ctx, arg) -> resolve(ctx.player(), value));
        *///?} else {
        Placeholders.register(Compat.id("spawnelytra", name), (ctx, arg) -> resolve(ctx.player(), value));
        //?}
    }
}
