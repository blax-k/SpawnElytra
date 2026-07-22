/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.integration;

import com.blaxk.spawnelytra.fabric.Main;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock (Geyser/Floodgate) detection, ported from the Paper plugin. Floodgate and Geyser
 * for Fabric expose the same APIs; both are soft dependencies accessed reflectively, so the
 * mod loads fine without them. Without either, Floodgate-style UUIDs (proxy setups) are used.
 */
public enum BedrockSupport {
    ;

    private enum DetectionMode {
        UNRESOLVED,
        FLOODGATE,
        GEYSER,
        UUID_HEURISTIC
    }

    private static final Map<UUID, Boolean> CACHE = new ConcurrentHashMap<>();

    private static Main plugin;
    private static DetectionMode detectionMode = DetectionMode.UNRESOLVED;

    private static Object floodgateApi;
    private static Method floodgateIsBedrockPlayer;

    private static Object geyserApi;
    private static Method geyserConnectionByUuid;

    private static boolean supportEnabled = true;

    public static void initialize(final Main plugin) {
        BedrockSupport.plugin = plugin;
        detectionMode = DetectionMode.UNRESOLVED;
        CACHE.clear();
        reloadSettings(plugin);
        resolveDetectionMode();
    }

    public static void reloadSettings(final Main plugin) {
        BedrockSupport.plugin = plugin;
        supportEnabled = plugin.getConfig().getBoolean("bedrock.enabled", true);
    }

    private static void resolveDetectionMode() {
        if (FabricLoader.getInstance().isModLoaded("floodgate")) {
            try {
                final Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                floodgateIsBedrockPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                if (floodgateApi != null) {
                    detectionMode = DetectionMode.FLOODGATE;
                    plugin.getLogger().info("Bedrock support: detecting Bedrock players via the Floodgate API.");
                    return;
                }
            } catch (final Throwable t) {
                plugin.getLogger().warn("Floodgate is installed but its API could not be accessed: {}", t.getMessage());
            }
        }

        if (FabricLoader.getInstance().isModLoaded("geyser-fabric")) {
            try {
                final Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = apiClass.getMethod("api").invoke(null);
                geyserConnectionByUuid = apiClass.getMethod("connectionByUuid", UUID.class);
                if (geyserApi != null) {
                    detectionMode = DetectionMode.GEYSER;
                    plugin.getLogger().info("Bedrock support: detecting Bedrock players via the Geyser API.");
                    return;
                }
            } catch (final Throwable t) {
                plugin.getLogger().warn("Geyser-Fabric is installed but its API could not be accessed: {}", t.getMessage());
            }
        }

        detectionMode = DetectionMode.UUID_HEURISTIC;
    }

    public static boolean isBedrockPlayer(final ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return CACHE.computeIfAbsent(player.getUUID(), BedrockSupport::detect);
    }

    public static boolean isManaged(final ServerPlayer player) {
        return supportEnabled && isBedrockPlayer(player);
    }

    public static void forget(final UUID uuid) {
        if (uuid != null) {
            CACHE.remove(uuid);
        }
    }

    private static boolean detect(final UUID uuid) {
        if (detectionMode == DetectionMode.UNRESOLVED) {
            resolveDetectionMode();
        }

        switch (detectionMode) {
            case FLOODGATE:
                try {
                    return (Boolean) floodgateIsBedrockPlayer.invoke(floodgateApi, uuid);
                } catch (final Throwable t) {
                    return isFloodgateStyleUuid(uuid);
                }
            case GEYSER:
                try {
                    return geyserConnectionByUuid.invoke(geyserApi, uuid) != null;
                } catch (final Throwable t) {
                    return isFloodgateStyleUuid(uuid);
                }
            default:
                return isFloodgateStyleUuid(uuid);
        }
    }

    private static boolean isFloodgateStyleUuid(final UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }
}
