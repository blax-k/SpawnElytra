/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.integration;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public enum BedrockSupport {
    ;

    private enum DetectionMode {
        UNRESOLVED,
        FLOODGATE,
        GEYSER,
        UUID_HEURISTIC
    }

    private static final Map<UUID, Boolean> CACHE = new ConcurrentHashMap<>();

    private static Plugin plugin;
    private static DetectionMode detectionMode = DetectionMode.UNRESOLVED;

    private static Object floodgateApi;
    private static Method floodgateIsBedrockPlayer;

    private static Object geyserApi;
    private static Method geyserConnectionByUuid;

    private static boolean supportEnabled = true;

    public static void initialize(final Plugin plugin) {
        BedrockSupport.plugin = plugin;
        BedrockSupport.reloadSettings(plugin);
        BedrockSupport.resolveDetectionMode();
    }

    public static void reloadSettings(final Plugin plugin) {
        BedrockSupport.plugin = plugin;
        supportEnabled = plugin.getConfig().getBoolean("bedrock.enabled", true);
    }

    private static void resolveDetectionMode() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                floodgateIsBedrockPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                if (floodgateApi != null) {
                    detectionMode = DetectionMode.FLOODGATE;
                    BedrockSupport.plugin.getLogger().info("Bedrock support: detecting Bedrock players via the Floodgate API.");
                    return;
                }
            } catch (final Throwable t) {
                BedrockSupport.plugin.getLogger().warning("Floodgate is installed but its API could not be accessed: " + t.getMessage());
            }
        }

        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            try {
                final Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = apiClass.getMethod("api").invoke(null);
                geyserConnectionByUuid = apiClass.getMethod("connectionByUuid", UUID.class);
                if (geyserApi != null) {
                    detectionMode = DetectionMode.GEYSER;
                    BedrockSupport.plugin.getLogger().info("Bedrock support: detecting Bedrock players via the Geyser API.");
                    return;
                }
            } catch (final Throwable t) {
                BedrockSupport.plugin.getLogger().warning("Geyser-Spigot is installed but its API could not be accessed: " + t.getMessage());
            }
        }

        detectionMode = DetectionMode.UUID_HEURISTIC;
    }

    public static boolean isBedrockPlayer(final Player player) {
        if (player == null) {
            return false;
        }
        return CACHE.computeIfAbsent(player.getUniqueId(), BedrockSupport::detect);
    }

    public static boolean isManaged(final Player player) {
        return supportEnabled && BedrockSupport.isBedrockPlayer(player);
    }

    public static void forget(final UUID uuid) {
        if (uuid != null) {
            CACHE.remove(uuid);
        }
    }

    private static boolean detect(final UUID uuid) {
        if (detectionMode == DetectionMode.UNRESOLVED) {
            BedrockSupport.resolveDetectionMode();
        }

        switch (detectionMode) {
            case FLOODGATE:
                try {
                    return (Boolean) floodgateIsBedrockPlayer.invoke(floodgateApi, uuid);
                } catch (final Throwable t) {
                    return BedrockSupport.isFloodgateStyleUuid(uuid);
                }
            case GEYSER:
                try {
                    return geyserConnectionByUuid.invoke(geyserApi, uuid) != null;
                } catch (final Throwable t) {
                    return BedrockSupport.isFloodgateStyleUuid(uuid);
                }
            default:
                return BedrockSupport.isFloodgateStyleUuid(uuid);
        }
    }

    private static boolean isFloodgateStyleUuid(final UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }
}
