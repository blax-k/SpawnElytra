package com.blaxk.spawnelytra.config;

import com.blaxk.spawnelytra.util.BackupUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum LanguageUpdater {
    ;
    private static final List<String> SUPPORTED = Arrays.asList("en", "de", "es", "fr", "ar");
    private static final List<String> DEPRECATED = Arrays.asList("hi", "zh");
    private static final String REQUIRED_LANG_VERSION = "1.4";

    public static void updateLanguages(final JavaPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        final File langDir = new File(dataFolder, "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create language directory at: " + langDir.getAbsolutePath());
            return;
        }

        for (final String code : LanguageUpdater.DEPRECATED) {
            final File f = new File(langDir, code + ".yml");
            if (f.exists()) {
                BackupUtil.backupFile(plugin, f, "lang/" + f.getName());
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (final IOException e) {
                    plugin.getLogger().warning("Failed to delete unsupported language file '" + f.getName() + "': " + e.getMessage());
                }
            }
        }

        for (final String code : LanguageUpdater.SUPPORTED) {
            final File f = new File(langDir, code + ".yml");
            if (!f.exists()) {
                try (final InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                    if (in != null) {
                        Files.write(f.toPath(), in.readAllBytes());
                    }
                } catch (final IOException e) {
                    plugin.getLogger().warning("Failed to write language file '" + code + ".yml': " + e.getMessage());
                }
                continue;
            }

            if (LanguageUpdater.needsLanguageUpgrade(f)) {
                BackupUtil.backupFile(plugin, f, "lang/" + f.getName());
                try (final InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                    if (in != null) {
                        Files.write(f.toPath(), in.readAllBytes());
                    } else {
                        plugin.getLogger().warning("Missing bundled resource for language '" + code + "' during update.");
                    }
                } catch (final IOException e) {
                    plugin.getLogger().warning("Failed to update language file '" + code + ".yml': " + e.getMessage());
                }
            }
        }
    }

    private static boolean needsLanguageUpgrade(final File langFile) {
        try {
            final FileConfiguration cfg = YamlConfiguration.loadConfiguration(langFile);
            
            final String fileVersion = cfg.getString("lang-version", null);
            if (fileVersion == null || !LanguageUpdater.REQUIRED_LANG_VERSION.equalsIgnoreCase(fileVersion.trim())) {
                return true;
            }

            final Set<String> keys = new HashSet<>(cfg.getKeys(false));
            
            if (keys.contains("new_version_available") || keys.contains("update_to_version") || keys.contains("download_link")) {
                return true;
            }
            
            for (final String k : keys) {
                final String v = cfg.getString(k, "");
                if (v == null) continue;
                
                if (v.contains("{key}") || v.contains("{currentVersion}") || v.contains("{latestVersion}")) {
                    return true;
                }
            }
            return false;
        } catch (final Exception e) {
            return true;
        }
    }
}
