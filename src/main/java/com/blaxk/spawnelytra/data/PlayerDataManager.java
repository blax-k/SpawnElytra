package com.blaxk.spawnelytra.data;

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
    private boolean initialized;

    public PlayerDataManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        dataFolder = new File(plugin.getDataFolder(), "playerdata");

        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }
    }

    public void initialize() {
        if (!this.dataFolder.exists()) {
            this.dataFolder.mkdirs();
        }

        final File[] dataFiles = this.dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (dataFiles != null) {
            for (final File file : dataFiles) {
                this.loadPlayerData(file);
            }
        }

        this.initialized = true;
    }

    private void loadPlayerData(final File file) {
        final String filename = file.getName();
        if (filename.endsWith(".yml")) {
            try {
                final String uuidString = filename.substring(0, filename.length() - 4);
                final UUID uuid = UUID.fromString(uuidString);

                final FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                final int flyCount = config.getInt("fly_count", 0);
                final int boostCount = config.getInt("boost_count", 0);

                this.playerDataMap.put(uuid, new PlayerData(uuid, flyCount, boostCount));
            } catch (final IllegalArgumentException e) {
                this.plugin.getLogger().warning("Invalid player data file: " + file.getName());
            }
        }
    }

    public PlayerData getPlayerData(final UUID uuid) {
        if (!this.initialized) {
            this.initialize();
        }

        return this.playerDataMap.computeIfAbsent(uuid, id -> new PlayerData(id, 0, 0));
    }

    public void incrementFlyCount(final Player player) {
        final PlayerData data = this.getPlayerData(player.getUniqueId());
        data.incrementFlyCount();
        this.savePlayerData(data);
    }

    public void incrementBoostCount(final Player player) {
        final PlayerData data = this.getPlayerData(player.getUniqueId());
        data.incrementBoostCount();
        this.savePlayerData(data);
    }

    public void savePlayerData(final PlayerData data) {
        try {
            final File file = new File(this.dataFolder, data.getUuid().toString() + ".yml");
            final FileConfiguration config = new YamlConfiguration();

            config.set("fly_count", data.getFlyCount());
            config.set("boost_count", data.getBoostCount());

            config.save(file);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
        }
    }

    public void saveAllPlayerData() {
        for (final PlayerData data : this.playerDataMap.values()) {
            this.savePlayerData(data);
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        private int flyCount;
        private int boostCount;

        public PlayerData(final UUID uuid, final int flyCount, final int boostCount) {
            this.uuid = uuid;
            this.flyCount = flyCount;
            this.boostCount = boostCount;
        }

        public UUID getUuid() {
            return this.uuid;
        }

        public int getFlyCount() {
            return this.flyCount;
        }

        public int getBoostCount() {
            return this.boostCount;
        }

        public void incrementFlyCount() {
            this.flyCount++;
        }

        public void incrementBoostCount() {
            this.boostCount++;
        }
    }
}
