package at.blaxk.spawnelytra;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class ConfigUpdater {
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en", "de", "es", "fr", "hi", "zh", "ar");

    public static void updateConfig(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean isLegacyConfig = false;

        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        if (!config.contains("spawn.mode") || !config.contains("messages")) {
            isLegacyConfig = true;

            int oldRadius = config.getInt("radius", 50);
            int oldStrength = config.getInt("strength", 2);
            String oldWorld = config.getString("world", "world");
            String oldLanguage = config.getString("language", "en");
            String oldMode = config.getString("mode", "auto");
            String oldBoostDirection = config.getString("boost_direction", "forward");

            File backupFile = new File(plugin.getDataFolder(), "config_backup.yml");

            try {
                Files.copy(configFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create backup of old configuration: " + e.getMessage());
            }

            createNewConfiguration(plugin, configFile, oldRadius, oldStrength, oldWorld, oldLanguage, oldMode, oldBoostDirection);

            if (backupFile.exists()) {
                backupFile.delete();
            }

            plugin.reloadConfig();
            return;
        }

        boolean needsUpdate = false;

        String header = "Spawn Elytra Plugin by Blaxk_\n"
                + "Plugin Version: " + plugin.getDescription().getVersion() + "\n"
                + "Modrinth: https://modrinth.com/plugin/spawn-elytra\n\n";
        config.options().header(header);
        needsUpdate = true;

        needsUpdate |= ensureConfigOption(config, "activation_mode", "double_jump",
                "Activation mode for elytra:\n" +
                        "double_jump: Player needs to double-press space to activate elytra\n" +
                        "auto: Automatically activates elytra when player has air below and is in spawn area\n" +
                        "sneak_jump: Player needs to sneak while jumping to activate elytra\n" +
                        "f_key: Player needs to press F (swap hands) to activate elytra");

        needsUpdate |= ensureConfigOption(config, "f_key_launch_strength", 1.5,
                "Launch strength for F_key activation mode (Only used when activation_mode: f_key) (1.5 = ~14-15 blocks upward)");

        needsUpdate |= ensureConfigOption(config, "radius", 50,
                "The radius around spawn where elytra boosting is enabled\n" +
                        "(only used in auto mode or if x2, y2, z2 are all set to 0)");

        needsUpdate |= ensureConfigOption(config, "strength", 2,
                "The strength of the boost when pressing the boost key");

        needsUpdate |= ensureConfigOption(config, "boost_direction", "forward",
                "Boost direction: 'forward' or 'upward'\n" +
                        "forward: Boosts player in the direction they are looking\n" +
                        "upward: Boosts player straight up");

        needsUpdate |= ensureConfigOption(config, "world", "world",
                "The world where the spawn elytra feature is enabled");

        needsUpdate |= ensureConfigOption(config, "disable_fireworks_in_spawn_elytra", false,
                "Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)");

        if (!config.contains("spawn")) {
            config.createSection("spawn");
            needsUpdate = true;
        }

        ConfigurationSection spawnSection = config.getConfigurationSection("spawn");

        if (config.contains("mode") && !spawnSection.contains("mode")) {
            String oldMode = config.getString("mode", "auto");
            spawnSection.set("mode", oldMode);
            config.set("mode", null);
            needsUpdate = true;
        }

        needsUpdate |= ensureConfigOption(spawnSection, "mode", "auto",
                "Mode options: 'auto' or 'advanced'\n" +
                        "auto: Uses the world spawn point\n" +
                        "advanced: Uses custom spawn coordinates defined below");

        needsUpdate |= ensureConfigOption(spawnSection, "x", 0,
                "X coordinate of the spawn point (used when mode is 'advanced')");

        needsUpdate |= ensureConfigOption(spawnSection, "y", 64,
                "Y coordinate of the spawn point (used when mode is 'advanced')");

        needsUpdate |= ensureConfigOption(spawnSection, "z", 0,
                "Z coordinate of the spawn point (used when mode is 'advanced')");

        needsUpdate |= ensureConfigOption(spawnSection, "x2", 0,
                "Second X coordinate for the rectangular elytra area\n" +
                        "Set all x2/y2/z2 to 0 to use radius-based circular area instead");

        needsUpdate |= ensureConfigOption(spawnSection, "y2", 0,
                "Second Y coordinate for the rectangular elytra area");

        needsUpdate |= ensureConfigOption(spawnSection, "z2", 0,
                "Second Z coordinate for the rectangular elytra area");

        if (spawnSection.contains("dx") || spawnSection.contains("dy") || spawnSection.contains("dz")) {
            double x = spawnSection.getDouble("x", 0);
            double y = spawnSection.getDouble("y", 64);
            double z = spawnSection.getDouble("z", 0);

            if (!spawnSection.contains("x2") && spawnSection.contains("dx")) {
                double dx = spawnSection.getDouble("dx", 0);
                spawnSection.set("x2", x + dx);
                needsUpdate = true;
            }

            if (!spawnSection.contains("y2") && spawnSection.contains("dy")) {
                double dy = spawnSection.getDouble("dy", 0);
                spawnSection.set("y2", y + dy);
                needsUpdate = true;
            }

            if (!spawnSection.contains("z2") && spawnSection.contains("dz")) {
                double dz = spawnSection.getDouble("dz", 0);
                spawnSection.set("z2", z + dz);
                needsUpdate = true;
            }

            spawnSection.set("dx", null);
            spawnSection.set("dy", null);
            spawnSection.set("dz", null);
        }

        needsUpdate |= ensureConfigOption(config, "boost_sound", "ENTITY_BAT_TAKEOFF",
                "Sound to play when boosting. Full list: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");

        needsUpdate |= ensureConfigOption(config, "language", "en",
                "Available languages: en, de, es, fr, hi, zh, ar");

        needsUpdate |= ensureConfigOption(config, "disable_in_creative", true,
                "Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)");

        needsUpdate |= ensureConfigOption(config, "disable_in_adventure", false,
                "If you don't want to disable elytra in adventure mode, set this to false");

        if (!config.contains("messages")) {
            config.createSection("messages");
            needsUpdate = true;
        }

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");

        needsUpdate |= ensureConfigOption(messagesSection, "show_press_to_boost", true,
                "Set to false to disable the \"press to boost\" message");

        needsUpdate |= ensureConfigOption(messagesSection, "show_boost_activated", true,
                "Set to false to disable the \"boost activated\" message");

        needsUpdate |= ensureConfigOption(messagesSection, "use_custom_messages", false,
                "Set to true to use custom messages below instead of language file messages");

        String defaultPressToBoost = config.getString("language", "en").equalsIgnoreCase("de") ?
                "&6Drücke &6&l{key} &6um dich zu boosten." :
                "&6Press &6&l{key} &6to boost yourself.";

        String defaultBoostActivated = config.getString("language", "en").equalsIgnoreCase("de") ?
                "&a&lBoost aktiviert!" :
                "&a&lBoost activated!";

        needsUpdate |= ensureConfigOption(messagesSection, "press_to_boost", defaultPressToBoost,
                "Custom messages\n" +
                        "{key} is used represent the offhand key (F by default)\n" +
                        "Due to limitations from minecraft, you cant enter any key you want.\n" +
                        "Legacy color codes (&a, &e, etc.) are supported");

        needsUpdate |= ensureConfigOption(messagesSection, "boost_activated", defaultBoostActivated, null);

        try {
            Sound.valueOf(config.getString("boost_sound", "ENTITY_BAT_TAKEOFF").toUpperCase());
        } catch (IllegalArgumentException e) {
            config.set("boost_sound", "ENTITY_BAT_TAKEOFF");
            needsUpdate = true;
        }

        String configLang = config.getString("language", "en").toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(configLang)) {
            config.set("language", "en");
            needsUpdate = true;
        }

        String boostDir = config.getString("boost_direction", "forward").toLowerCase();
        if (!boostDir.equals("forward") && !boostDir.equals("upward")) {
            config.set("boost_direction", "forward");
            needsUpdate = true;
        }

        if (needsUpdate) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save updated configuration: " + e.getMessage());
            }
        }
    }

    private static void createNewConfiguration(JavaPlugin plugin, File configFile, int oldRadius, int oldStrength, String oldWorld, String oldLanguage, String oldMode, String oldBoostDirection) {
        try {
            StringBuilder configBuilder = new StringBuilder();

            configBuilder.append("# Spawn Elytra Plugin by Blaxk_\n");
            configBuilder.append("# Plugin Version: ").append(plugin.getDescription().getVersion()).append("\n");
            configBuilder.append("# Modrinth: https://modrinth.com/plugin/spawn-elytra\n\n");

            configBuilder.append("# Activation mode for elytra:\n");
            configBuilder.append("# double_jump: Player needs to double-press space to activate elytra\n");
            configBuilder.append("# auto: Automatically activates elytra when player has air below and is in spawn area\n");
            configBuilder.append("# sneak_jump: Player needs to sneak while jumping to activate elytra\n");
            configBuilder.append("# f_key: Player needs to press F (swap hands) to activate elytra\n");
            configBuilder.append("activation_mode: double_jump\n\n");

            configBuilder.append("# The radius around spawn where elytra boosting is enabled\n");
            configBuilder.append("# (only used in auto mode or if x2, y2, z2 are all set to 0)\n");
            configBuilder.append("radius: ").append(oldRadius).append("\n\n");

            configBuilder.append("# The strength of the boost when pressing the boost key\n");
            configBuilder.append("strength: ").append(oldStrength).append("\n\n");

            configBuilder.append("# Boost direction: 'forward' or 'upward'\n");
            configBuilder.append("# forward: Boosts player in the direction they are looking\n");
            configBuilder.append("# upward: Boosts player straight up\n");
            configBuilder.append("boost_direction: ").append(oldBoostDirection).append("\n\n");

            configBuilder.append("# The world where the spawn elytra feature is enabled\n");
            configBuilder.append("world: ").append(oldWorld).append("\n\n");

            configBuilder.append("# Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)\n");
            configBuilder.append("disable_fireworks_in_spawn_elytra: false\n\n");

            configBuilder.append("# Custom spawn coordinates and dimensions\n");
            configBuilder.append("spawn:\n");
            configBuilder.append("  # Mode options: 'auto' or 'advanced'\n");
            configBuilder.append("  # auto: Uses the world spawn point\n");
            configBuilder.append("  # advanced: Uses custom spawn coordinates defined below\n");
            configBuilder.append("  mode: ").append(oldMode).append("\n\n");
            configBuilder.append("  # First point of the elytra area\n");
            configBuilder.append("  x: 0\n");
            configBuilder.append("  y: 64\n");
            configBuilder.append("  z: 0\n\n");

            configBuilder.append("  # Second point of the elytra area\n");
            configBuilder.append("  # Setting all x2/y2/z2 to 0 will use the radius-based circular area instead\n");
            configBuilder.append("  # Example: Using x=0, y=64, z=0 and x2=100, y2=128, z2=100 creates a rectangular area\n");
            configBuilder.append("  # between those two coordinate points\n");
            configBuilder.append("  x2: 0  # Second X coordinate\n");
            configBuilder.append("  y2: 0  # Second Y coordinate\n");
            configBuilder.append("  z2: 0  # Second Z coordinate\n\n");

            configBuilder.append("# Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html\n");
            configBuilder.append("# Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING\n");
            configBuilder.append("boost_sound: ENTITY_BAT_TAKEOFF\n\n");

            configBuilder.append("# Available languages: en, de, es, fr, hi, zh, ar\n");
            configBuilder.append("language: ").append(oldLanguage).append("\n\n");

            configBuilder.append("# Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)\n");
            configBuilder.append("disable_in_creative: true\n\n");

            configBuilder.append("# If you don't want to disable elytra in adventure mode, set this to false\n");
            configBuilder.append("disable_in_adventure: false\n\n");

            configBuilder.append("# Launch strength for F_key activation mode (Only used when activation_mode: f_key) (1.5 = ~14-15 blocks upward)\n");
            configBuilder.append("f_key_launch_strength: 1.5\n\n");

            configBuilder.append("# Message settings\n");
            configBuilder.append("messages:\n");
            configBuilder.append("  # Set to false to disable the \"press to boost\" message\n");
            configBuilder.append("  show_press_to_boost: true\n");
            configBuilder.append("  # Set to false to disable the \"boost activated\" message\n");
            configBuilder.append("  show_boost_activated: true\n");
            configBuilder.append("  # Set to true to use custom messages below instead of language file messages\n");
            configBuilder.append("  use_custom_messages: false\n\n");

            configBuilder.append("  # Custom messages\n");
            configBuilder.append("  # {key} is used represent the offhand key (F by default)\n");
            configBuilder.append("  # Due to limitations from minecraft, you cant enter any key you want.\n");
            configBuilder.append("  # Legacy color codes (&a, &e, etc.) are supported\n");

            if ("de".equalsIgnoreCase(oldLanguage)) {
                configBuilder.append("  press_to_boost: '&6Drücke &6&l{key} &6um dich zu boosten.'\n");
                configBuilder.append("  boost_activated: '&a&lBoost aktiviert!'\n");
            } else {
                configBuilder.append("  press_to_boost: '&6Press &6&l{key} &6to boost yourself.'\n");
                configBuilder.append("  boost_activated: '&a&lBoost activated!'\n");
            }

            Files.writeString(configFile.toPath(), configBuilder.toString());

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create new configuration file: " + e.getMessage());
        }
    }

    private static FileConfiguration loadDefaultConfig(JavaPlugin plugin) {
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream != null) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
        }
        return new YamlConfiguration();
    }

    private static boolean ensureConfigOption(FileConfiguration config, String path, Object defaultValue, String comment) {
        if (!config.contains(path)) {
            if (comment != null) {
                config.setComments(path, Arrays.asList(comment.split("\n")));
            }
            config.set(path, defaultValue);
            return true;
        }
        return false;
    }

    private static boolean ensureConfigOption(ConfigurationSection section, String path, Object defaultValue, String comment) {
        if (!section.contains(path)) {
            if (comment != null) {
                section.setComments(path, Arrays.asList(comment.split("\n")));
            }
            section.set(path, defaultValue);
            return true;
        }
        return false;
    }
}