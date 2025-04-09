package at.blaxk.spawnelytra;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * SpawnElytra PlaceholderAPI expansion
 */
public class SpawnElytraExpansion extends PlaceholderExpansion {
    private final main plugin;
    private final PlayerDataManager playerDataManager;

    public SpawnElytraExpansion(main plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "spawnelytra";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Blaxk_";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
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
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        // %spawnelytra_fly_count%
        if (identifier.equals("fly_count")) {
            return String.valueOf(playerDataManager.getPlayerData(player.getUniqueId()).getFlyCount());
        }

        // %spawnelytra_boost_count%
        if (identifier.equals("boost_count")) {
            return String.valueOf(playerDataManager.getPlayerData(player.getUniqueId()).getBoostCount());
        }

        // %spawnelytra_total_count%
        if (identifier.equals("total_count")) {
            PlayerDataManager.PlayerData data = playerDataManager.getPlayerData(player.getUniqueId());
            return String.valueOf(data.getFlyCount() + data.getBoostCount());
        }

        return null;
    }
}