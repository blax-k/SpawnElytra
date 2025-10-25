package com.blaxk.spawnelytra.config;

import com.blaxk.spawnelytra.util.BackupUtil;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ConfigUpdater {
    ;
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en", "de", "es", "fr", "ar");
    private static final String CONFIG_VERSION_1_4 = "1.4";
    
    public static void updateConfig(final JavaPlugin plugin) {
        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        
        final FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        final ConfigVersion version = ConfigUpdater.detectConfigVersion(config);
        
        switch (version) {
            case V1_2:
                plugin.getLogger().info("Detected v1.2 configuration. Migrating to v1.4...");
                ConfigUpdater.migrateFromV12(plugin, config, configFile);
                break;
            case V1_3:
                plugin.getLogger().info("Detected v1.3 configuration. Migrating to v1.4...");
                ConfigUpdater.migrateFromV13(plugin, config, configFile);
                break;
            case V1_4:
                ConfigUpdater.updateV14Config(plugin, config, configFile);
                break;
            case UNKNOWN:
                plugin.getLogger().warning("Unknown configuration format. Creating backup and generating new config.");
                ConfigUpdater.createBackupAndGenerateNew(plugin, configFile);
                break;
        }
        
        plugin.reloadConfig();
    }

    private enum ConfigVersion {
        V1_2,
        V1_3,
        V1_4,
        UNKNOWN
    }
    
    private static ConfigVersion detectConfigVersion(final FileConfiguration config) {
        if (config.contains("worlds")) {
            return ConfigVersion.V1_4;
        }
        
        if (config.contains("boost_enabled") || config.contains("disable_fireworks_in_spawn_elytra")) {
            return ConfigVersion.V1_3;
        }
        
        if (config.contains("activation_mode") || config.contains("radius") || config.contains("world")) {
            return ConfigVersion.V1_2;
        }
        
        return ConfigVersion.UNKNOWN;
    }
    
    private static void migrateFromV12(final JavaPlugin plugin, final FileConfiguration oldConfig, final File configFile) {
        ConfigUpdater.createBackup(plugin, configFile);
        
        final String activationMode = oldConfig.getString("activation_mode", "double_jump");
        final int radius = oldConfig.getInt("radius", 100);
        final int strength = oldConfig.getInt("strength", 2);
        final String world = oldConfig.getString("world", "world");
        final String language = oldConfig.getString("language", "de");
        final String mode = oldConfig.getString("mode", "auto");
        final String boostDirection = oldConfig.getString("boost_direction", "forward");
        final String boostSound = oldConfig.getString("boost_sound", "ENTITY_BAT_TAKEOFF");
        final boolean disableInCreative = oldConfig.getBoolean("disable_in_creative", true);
        final boolean disableInAdventure = oldConfig.getBoolean("disable_in_adventure", false);
        
        int spawnX = 0, spawnY = 64, spawnZ = 0, spawnX2 = 0, spawnY2 = 0, spawnZ2 = 0;
        if (oldConfig.contains("spawn")) {
            final ConfigurationSection spawnSection = oldConfig.getConfigurationSection("spawn");
            if (spawnSection != null) {
                spawnX = spawnSection.getInt("x", 0);
                spawnY = spawnSection.getInt("y", 64);
                spawnZ = spawnSection.getInt("z", 0);
                spawnX2 = spawnSection.getInt("x2", 0);
                spawnY2 = spawnSection.getInt("y2", 0);
                spawnZ2 = spawnSection.getInt("z2", 0);
            }
        }
        
        boolean showPressToBoost = true;
        boolean showBoostActivated = true;
        String customPressMessage = "&aPress &a&l{key} &ato boost yourself.";
        String customBoostMessage = "&aBoost activated!";
        
        if (oldConfig.contains("messages")) {
            final ConfigurationSection msgSection = oldConfig.getConfigurationSection("messages");
            if (msgSection != null) {
                showPressToBoost = msgSection.getBoolean("show_press_to_boost", true);
                showBoostActivated = msgSection.getBoolean("show_boost_activated", true);
                customPressMessage = msgSection.getString("press_to_boost", customPressMessage);
                customBoostMessage = msgSection.getString("boost_activated", customBoostMessage);
            }
        }

        ConfigUpdater.generateV14Config(plugin, configFile, language, activationMode, radius, strength, boostDirection,
                world, mode, spawnX, spawnY, spawnZ, spawnX2, spawnY2, spawnZ2, boostSound,
                disableInCreative, disableInAdventure, showPressToBoost, showBoostActivated, 
                true, false, 1.5);
    }
    
    private static void migrateFromV13(final JavaPlugin plugin, final FileConfiguration oldConfig, final File configFile) {
        ConfigUpdater.createBackup(plugin, configFile);
        
        final String activationMode = oldConfig.getString("activation_mode", "double_jump");
        final int radius = oldConfig.getInt("radius", 100);
        final int strength = oldConfig.getInt("strength", 2);
        final String world = oldConfig.getString("world", "world");
        final String language = oldConfig.getString("language", "de");
        final String boostDirection = oldConfig.getString("boost_direction", "forward");
        final String boostSound = oldConfig.getString("boost_sound", "ENTITY_BAT_TAKEOFF");
        final boolean disableInCreative = oldConfig.getBoolean("disable_in_creative", true);
        final boolean disableInAdventure = oldConfig.getBoolean("disable_in_adventure", false);
        
        final boolean boostEnabled = oldConfig.getBoolean("boost_enabled", true);
        final boolean disableFireworks = oldConfig.getBoolean("disable_fireworks_in_spawn_elytra", false);
        final double fKeyLaunchStrength = oldConfig.getDouble("f_key_launch_strength", 1.5);
        
        String mode = "auto";
        int spawnX = 0, spawnY = 64, spawnZ = 0, spawnX2 = 0, spawnY2 = 0, spawnZ2 = 0;
        
        if (oldConfig.contains("spawn")) {
            final ConfigurationSection spawnSection = oldConfig.getConfigurationSection("spawn");
            if (spawnSection != null) {
                mode = spawnSection.getString("mode", "advanced");
                spawnX = spawnSection.getInt("x", 0);
                spawnY = spawnSection.getInt("y", 64);
                spawnZ = spawnSection.getInt("z", 0);
                spawnX2 = spawnSection.getInt("x2", 0);
                spawnY2 = spawnSection.getInt("y2", 0);
                spawnZ2 = spawnSection.getInt("z2", 0);
            }
        }
        
        boolean showPressToBoost = true;
        boolean showBoostActivated = true;
        
        if (oldConfig.contains("messages")) {
            final ConfigurationSection msgSection = oldConfig.getConfigurationSection("messages");
            if (msgSection != null) {
                showPressToBoost = msgSection.getBoolean("show_press_to_boost", true);
                showBoostActivated = msgSection.getBoolean("show_boost_activated", true);
            }
        }

        ConfigUpdater.generateV14Config(plugin, configFile, language, activationMode, radius, strength, boostDirection,
                world, mode, spawnX, spawnY, spawnZ, spawnX2, spawnY2, spawnZ2, boostSound,
                disableInCreative, disableInAdventure, showPressToBoost, showBoostActivated, 
                boostEnabled, disableFireworks, fKeyLaunchStrength);
    }
    
    private static void updateV14Config(final JavaPlugin plugin, final FileConfiguration config, final File configFile) {
        boolean needsUpdate = false;
        
        if (!config.contains("language")) {
            config.set("language", "en");
            needsUpdate = true;
        }
        
        if (!config.contains("game_modes")) {
            config.createSection("game_modes");
            needsUpdate = true;
        }
        
        if (!config.contains("fireworks")) {
            config.createSection("fireworks");
            needsUpdate = true;
        }
        
        if (!config.contains("messages")) {
            config.createSection("messages");
            needsUpdate = true;
        }
        
        if (!config.contains("messages.show_creative_disabled")) {
            config.set("messages.show_creative_disabled", false);
            needsUpdate = true;
        }
        
        if (!config.contains("hunger_consumption")) {
            config.createSection("hunger_consumption");
            needsUpdate = true;
        }
        
        if (!config.contains("worlds")) {
            config.createSection("worlds");
            needsUpdate = true;
        }
        
        if (needsUpdate) {
            try {
                BackupUtil.backupFile(plugin, configFile, "config/config.yml");
                config.save(configFile);
                plugin.getLogger().info("Updated v1.4 configuration with missing fields.");
            } catch (final IOException e) {
                plugin.getLogger().severe("Failed to save updated v1.4 configuration: " + e.getMessage());
            }
        }
    }
    
    
    private static void generateV14Config(final JavaPlugin plugin, final File configFile, final String language,
                                          final String activationMode, final int radius, final int strength, final String boostDirection,
                                          final String worldName, final String spawnMode, final int spawnX, final int spawnY, final int spawnZ,
                                          final int spawnX2, final int spawnY2, final int spawnZ2, final String boostSound,
                                          final boolean disableInCreative, final boolean disableInAdventure,
                                          final boolean showPressToBoost, final boolean showBoostActivated,
                                          final boolean boostEnabled, final boolean disableFireworks, final double fKeyLaunchStrength) {
        
        try {
            final List<String> lines = new ArrayList<>();
            
            
            lines.add("# Spawn Elytra Plugin by blaxk");
            lines.add("# Plugin Version: " + ConfigUpdater.CONFIG_VERSION_1_4);
            lines.add("# Modrinth: https://modrinth.com/plugin/spawn-elytra");
            lines.add("");
            lines.add("# ==========================================");
            lines.add("# GLOBAL SETTINGS");
            lines.add("# ==========================================");
            lines.add("");
            
            
            lines.add("# Available languages: en, de, es, fr, ar");
            lines.add("language: " + language);
            lines.add("");
            
            
            lines.add("# Game mode restrictions");
            lines.add("game_modes:");
            lines.add("  # Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)");
            lines.add("  disable_in_creative: " + disableInCreative);
            lines.add("  # If you don't want to disable elytra in adventure mode, set this to false");
            lines.add("  disable_in_adventure: " + disableInAdventure);
            lines.add("");
            
            
            lines.add("# Fireworks settings");
            lines.add("fireworks:");
            lines.add("  # Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)");
            lines.add("  disable_in_spawn_elytra: " + disableFireworks);
            lines.add("");
            
            
            lines.add("# Message settings");
            lines.add("messages:");
            lines.add("  # Set to false to disable the \"press to boost\" message");
            lines.add("  show_press_to_boost: " + showPressToBoost);
            lines.add("  # Set to false to disable the \"boost activated\" message");
            lines.add("  show_boost_activated: " + showBoostActivated);
            lines.add("  # Set to true to show an actionbar when Elytra is disabled in Creative mode");
            lines.add("  show_creative_disabled: false");
            lines.add("  # Message style: classic or modern");
            lines.add("  style: classic");
            lines.add("");
            
            
            lines.add("# Hunger consumption settings (global defaults, can be overridden per-world)");
            lines.add("hunger_consumption:");
            lines.add("  # Enable hunger consumption while using the spawn elytra features");
            lines.add("  enabled: false");
            lines.add("  # How hunger should be consumed: activation, distance, or time");
            lines.add("  mode: activation");
            lines.add("  # Minimum food level to keep (players will never drop below this value)");
            lines.add("  minimum_food_level: 0");
            lines.add("");
            lines.add("  activation:");
            lines.add("    # Hunger consumed each time the elytra activates");
            lines.add("    hunger_cost: 1");
            lines.add("");
            lines.add("  distance:");
            lines.add("    # Blocks travelled while gliding before hunger is consumed");
            lines.add("    blocks_per_point: 50.0");
            lines.add("    # Hunger consumed every time the distance threshold is reached");
            lines.add("    hunger_cost: 1");
            lines.add("");
            lines.add("  time:");
            lines.add("    # Seconds of gliding before hunger is consumed");
            lines.add("    seconds_per_point: 30");
            lines.add("    # Hunger consumed each time the timer elapses");
            lines.add("    hunger_cost: 1");
            lines.add("");
            
            
            lines.add("# ==========================================");
            lines.add("# WORLD-SPECIFIC SETTINGS");
            lines.add("# ==========================================");
            lines.add("");
            lines.add("# Configure elytra settings per world");
            lines.add("worlds:");
            lines.add("  # If you want to add another world, copy the entire '" + worldName + "' section and change the name and preferences");
            lines.add("  " + worldName + ":");
            lines.add("    # Enable spawn elytra in this world");
            lines.add("    enabled: true");
            lines.add("    ");
            lines.add("    # Activation mode for elytra:");
            lines.add("    # double_jump: Player needs to double-press space to activate elytra");
            lines.add("    # auto: Automatically activates elytra when player has air below and is in spawn area");
            lines.add("    # sneak_jump: Player needs to sneak while jumping to activate elytra");
            lines.add("    # f_key: Player needs to press F (swap hands) to activate elytra, this also boosts a player upwards on activation");
            lines.add("    activation_mode: " + activationMode);
            lines.add("    ");
            lines.add("    # The radius around spawn where elytra boosting is enabled");
            lines.add("    # (only used when area_mode is 'circular' or spawn coords x2/y2/z2 are all 0)");
            lines.add("    radius: " + radius);
            lines.add("    ");
            lines.add("    # Spawn area configuration");
            lines.add("    spawn_area:");
            lines.add("      # Mode options: 'auto' or 'advanced'");
            lines.add("      # auto: Uses the world spawn point with radius");
            lines.add("      # advanced: Uses custom spawn coordinates defined below");
            lines.add("      mode: " + spawnMode);
            lines.add("      ");
            lines.add("      # Area type: 'circular' or 'rectangular'");
            lines.add("      area_type: " + (spawnX2 == 0 && spawnY2 == 0 && spawnZ2 == 0 ? "circular" : "rectangular"));
            lines.add("      ");
            lines.add("      # Primary spawn coordinates (center for circular, first corner for rectangular)");
            lines.add("      x: " + spawnX);
            lines.add("      y: " + spawnY);
            lines.add("      z: " + spawnZ);
            lines.add("      ");
            lines.add("      # Secondary coordinates (only used for rectangular areas)");
            lines.add("      # Setting all to 0 uses circular area with radius instead");
            lines.add("      x2: " + spawnX2);
            lines.add("      y2: " + spawnY2);
            lines.add("      z2: " + spawnZ2);
            lines.add("    ");
            lines.add("    # Boost settings");
            lines.add("    boost:");
            lines.add("      # Enable boost functionality");
            lines.add("      enabled: " + boostEnabled);
            lines.add("      # The strength of the boost when pressing the boost key");
            lines.add("      strength: " + strength);
            lines.add("      # Boost direction: 'forward' or 'upward'");
            lines.add("      # forward: Boosts player in the direction they are looking");
            lines.add("      # upward: Boosts player straight up");
            lines.add("      direction: " + boostDirection);
            lines.add("      # Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");
            lines.add("      # Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING");
            lines.add("      sound: " + boostSound);
            lines.add("    ");
            lines.add("    # F-key specific settings (only used when activation_mode: f_key)");
            lines.add("    f_key:");
            lines.add("      # Launch strength when pressing F key (1.5 = ~14-15 blocks upward)");
            lines.add("      launch_strength: " + fKeyLaunchStrength);
            
            final String content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
            Files.writeString(configFile.toPath(), content, StandardCharsets.UTF_8);
            
            plugin.getLogger().info("Successfully generated v1.4 configuration.");
            
        } catch (final IOException e) {
            plugin.getLogger().severe("Failed to generate v1.4 configuration: " + e.getMessage());
        }
    }
    
    
    private static void createBackup(final JavaPlugin plugin, final File configFile) {
        BackupUtil.backupFile(plugin, configFile, "config/config.yml");
    }
    
    
    private static void createBackupAndGenerateNew(final JavaPlugin plugin, final File configFile) {
        ConfigUpdater.createBackup(plugin, configFile);

        ConfigUpdater.generateV14Config(plugin, configFile, "en", "double_jump", 100, 2, "forward",
                "world", "auto", 0, 64, 0, 0, 0, 0, "ENTITY_BAT_TAKEOFF",
                true, false, true, true, true, false, 1.5);
    }
    
    
    private static String validateSound(final String sound) {
        try {
            Sound.valueOf(sound.toUpperCase());
            return sound;
        } catch (final IllegalArgumentException e) {
            return "ENTITY_BAT_TAKEOFF"; 
        }
    }
    
    
    private static String validateLanguage(final String language) {
        if (ConfigUpdater.SUPPORTED_LANGUAGES.contains(language.toLowerCase())) {
            return language.toLowerCase();
        }
        return "en"; 
    }
}

