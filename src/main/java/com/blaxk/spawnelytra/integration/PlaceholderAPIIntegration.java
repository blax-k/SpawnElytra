/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.integration;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.listener.SpawnElytra;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIIntegration extends PlaceholderExpansion {
    private final Main plugin;
    private final PlayerDataManager playerDataManager;

    public PlaceholderAPIIntegration(final Main plugin, final PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "spawnelytra";
    }

    @Override
    public @NotNull String getAuthor() {
        return "blaxk";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(final Player player, @NotNull final String identifier) {
        if (player == null) {
            return "";
        }

        if ("fly_count".equals(identifier)) {
            return String.valueOf(this.playerDataManager.getPlayerData(player.getUniqueId()).getFlyCount());
        }

        if ("boost_count".equals(identifier)) {
            return String.valueOf(this.playerDataManager.getPlayerData(player.getUniqueId()).getBoostCount());
        }

        if ("total_count".equals(identifier)) {
            final PlayerDataManager.PlayerData data = this.playerDataManager.getPlayerData(player.getUniqueId());
            return String.valueOf(data.getFlyCount() + data.getBoostCount());
        }

        if ("flying".equals(identifier)) {
            final SpawnElytra instance = this.plugin.getSpawnElytraInstance(player.getWorld().getName());
            return String.valueOf(instance != null && instance.isFlying(player));
        }

        if ("in_area".equals(identifier)) {
            final SpawnElytra instance = this.plugin.getSpawnElytraInstance(player.getWorld().getName());
            return String.valueOf(instance != null && instance.isInSpawnArea(player));
        }

        if ("boosts_remaining".equals(identifier)) {
            final SpawnElytra instance = this.plugin.getSpawnElytraInstance(player.getWorld().getName());
            return String.valueOf(instance != null ? instance.getBoostsRemaining(player) : 0);
        }

        return null;
    }
}
