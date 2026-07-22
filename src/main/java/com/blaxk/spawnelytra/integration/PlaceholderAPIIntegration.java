/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.integration;

import com.blaxk.spawnelytra.common.stats.StatsFormat;
import com.blaxk.spawnelytra.common.tier.PermissionTier;

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
        final PlayerDataManager.PlayerData data = this.playerDataManager.getPlayerData(player.getUniqueId());
        final SpawnElytra elytra = this.plugin.getSpawnElytra();
        switch (identifier) {
            case "fly_count":
                return String.valueOf(data.getFlyCount());
            case "boost_count":
                return String.valueOf(data.getBoostCount());
            case "total_count":
                return String.valueOf(data.getFlyCount() + data.getBoostCount());
            case "flying":
                return String.valueOf(elytra != null && elytra.isFlying(player));
            case "in_area":
                return String.valueOf(elytra != null && !elytra.currentZoneName(player).isEmpty());
            case "boosts_remaining":
                return String.valueOf(elytra != null ? elytra.getBoostsRemaining(player) : 0);
            case "zone":
                return elytra != null ? elytra.currentZoneName(player) : "";
            case "enabled":
                return String.valueOf(data.isEnabled());
            case "tier": {
                final PermissionTier tier = this.plugin.getZoneService().tierOf(player);
                return tier == null ? "" : tier.name();
            }
            default: {
                final String stat = StatsFormat.placeholder(data.stats(), identifier);
                return stat;
            }
        }
    }
}
