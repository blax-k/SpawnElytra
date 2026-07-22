/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.config;

import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.util.BackupUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum LanguageUpdater {
    ;
    private static final List<String> SUPPORTED = List.of("en", "de", "es", "fr", "ar", "pl");
    private static final List<String> DEPRECATED = List.of("hi", "zh");
    private static final String REQUIRED_LANG_VERSION = "1.6";

    public static void updateLanguages(final Main plugin) {
        LanguageUpdater.updateLanguages(plugin, false);
    }

    public static void updateLanguages(final Main plugin, final boolean removeDeprecated) {
        final File dataFolder = plugin.getDataFolder();
        final File langDir = new File(dataFolder, "lang");
        if (!langDir.mkdirs() && !langDir.isDirectory()) {
            plugin.getLogger().warn("Failed to create language directory: " + langDir.getAbsolutePath());
            return;
        }

        if (removeDeprecated) {
            for (final String code : LanguageUpdater.DEPRECATED) {
                final File f = new File(langDir, code + ".yml");
                if (f.exists()) {
                    BackupUtil.backupFile(plugin, f, "lang/" + f.getName());
                    try {
                        Files.deleteIfExists(f.toPath());
                    } catch (final IOException e) {
                        plugin.getLogger().warn("Failed to delete deprecated language file '" + f.getName() + "': " + e.getMessage());
                    }
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
                    plugin.getLogger().warn("Failed to write language file '" + code + ".yml': " + e.getMessage());
                }
                continue;
            }

            if (LanguageUpdater.needsMerge(f, LanguageUpdater.bundledKeys(plugin, code))) {
                BackupUtil.backupFile(plugin, f, "lang/" + f.getName());
                LanguageUpdater.mergeMissingKeys(plugin, f, code);
                continue;
            }

            if (LanguageUpdater.needsLanguageUpgrade(f)) {
                BackupUtil.backupFile(plugin, f, "lang/" + f.getName());
                try (final InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                    if (in != null) {
                        Files.write(f.toPath(), in.readAllBytes());
                    } else {
                        plugin.getLogger().warn("Missing bundled resource for '" + code + ".yml'.");
                    }
                } catch (final IOException e) {
                    plugin.getLogger().warn("Failed to update language file '" + code + ".yml': " + e.getMessage());
                }
            }
        }
    }

    /**
     * A 1.5+ file (no legacy placeholders) whose version is older or that misses keys: keep the user's texts and add
     * the missing keys from the bundled file (spec 9).
     */
    private static Set<String> bundledKeys(final Main plugin, final String code) {
        try (final InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                return Set.of();
            }
            return YamlConfiguration.loadConfiguration(in).getKeys(false);
        } catch (final IOException e) {
            return Set.of();
        }
    }

    private static boolean needsMerge(final File langFile, final Set<String> bundled) {
        try {
            final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(langFile, null);
            final String fileVersion = cfg.getString("lang-version", null);
            if (fileVersion == null || !"1.5".equals(fileVersion.trim()) && !REQUIRED_LANG_VERSION.equals(fileVersion.trim())) {
                return false;
            }
            if (LanguageUpdater.hasLegacyContent(cfg)) {
                return false;
            }
            if (!REQUIRED_LANG_VERSION.equals(fileVersion.trim())) {
                return true;
            }
            return !cfg.getKeys(false).containsAll(bundled);
        } catch (final Exception e) {
            return false;
        }
    }

    private static boolean hasLegacyContent(final YamlConfiguration cfg) {
        final Set<String> keys = new HashSet<>(cfg.getKeys(false));
        if (keys.contains("new_version_available") || keys.contains("update_to_version") || keys.contains("download_link")) {
            return true;
        }
        for (final String k : keys) {
            final String v = cfg.getString(k, "");
            if (v != null && (v.contains("{key}") || v.contains("{currentVersion}") || v.contains("{latestVersion}"))) {
                return true;
            }
        }
        return false;
    }

    /** Adds every key of the bundled file that the user's file lacks; user texts stay untouched. */
    public static void mergeMissingKeys(final Main plugin, final File f, final String code) {
        try (final InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                return;
            }
            final YamlConfiguration bundled = YamlConfiguration.loadConfiguration(in);
            final YamlConfiguration user = new YamlConfiguration();
            user.load(f);
            final List<String> versionComments = user.getComments("lang-version");
            user.set("lang-version", null);
            int added = 0;
            for (final String key : bundled.getKeys(false)) {
                if ("lang-version".equals(key) || user.contains(key, true)) {
                    continue;
                }
                user.set(key, bundled.get(key));
                added++;
            }
            user.set("lang-version", REQUIRED_LANG_VERSION);
            user.setComments("lang-version", versionComments.isEmpty() ? bundled.getComments("lang-version") : versionComments);
            user.save(f);
            plugin.getLogger().info("Updated language file " + f.getName() + " to " + REQUIRED_LANG_VERSION + " (" + added + " new keys).");
        } catch (final Exception e) {
            plugin.getLogger().warn("Failed to update language file '" + f.getName() + "': " + e.getMessage());
        }
    }

    private static boolean needsLanguageUpgrade(final File langFile) {
        try {
            final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(langFile, null);
            
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
