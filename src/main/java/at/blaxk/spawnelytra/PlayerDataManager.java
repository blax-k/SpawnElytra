package at.blaxk.spawnelytra;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final ConcurrentHashMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private boolean initialized = false;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void initialize() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File[] dataFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (dataFiles != null) {
            for (File file : dataFiles) {
                loadPlayerData(file);
            }
        }

        initialized = true;
    }

    private void loadPlayerData(File file) {
        String filename = file.getName();
        if (filename.endsWith(".yml")) {
            try {
                String uuidString = filename.substring(0, filename.length() - 4);
                UUID uuid = UUID.fromString(uuidString);

                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                int flyCount = config.getInt("fly_count", 0);
                int boostCount = config.getInt("boost_count", 0);

                playerDataMap.put(uuid, new PlayerData(uuid, flyCount, boostCount));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid player data file: " + file.getName());
            }
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        if (!initialized) {
            initialize();
        }

        return playerDataMap.computeIfAbsent(uuid, id -> new PlayerData(id, 0, 0));
    }

    public void incrementFlyCount(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        data.incrementFlyCount();
        savePlayerData(data);
    }

    public void incrementBoostCount(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        data.incrementBoostCount();
        savePlayerData(data);
    }

    public void savePlayerData(PlayerData data) {
        try {
            File file = new File(dataFolder, data.getUuid().toString() + ".yml");
            FileConfiguration config = new YamlConfiguration();

            config.set("fly_count", data.getFlyCount());
            config.set("boost_count", data.getBoostCount());

            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
        }
    }

    public void saveAllPlayerData() {
        for (PlayerData data : playerDataMap.values()) {
            savePlayerData(data);
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        private int flyCount;
        private int boostCount;

        public PlayerData(UUID uuid, int flyCount, int boostCount) {
            this.uuid = uuid;
            this.flyCount = flyCount;
            this.boostCount = boostCount;
        }

        public UUID getUuid() {
            return uuid;
        }

        public int getFlyCount() {
            return flyCount;
        }

        public int getBoostCount() {
            return boostCount;
        }

        public void incrementFlyCount() {
            flyCount++;
        }

        public void incrementBoostCount() {
            boostCount++;
        }
    }
}