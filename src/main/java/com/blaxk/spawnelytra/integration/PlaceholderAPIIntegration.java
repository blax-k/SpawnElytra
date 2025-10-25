package com.blaxk.spawnelytra.integration;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.data.PlayerDataManager;
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

        return null;
    }
}
