/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Maps the world keys used in config.yml to Fabric dimensions. Both Bukkit world names
 * ({@code world}, {@code world_nether}, {@code world_the_end}, {@code world_<ns>_<path>} -
 * where "world" is the server's level name) and dimension ids ({@code minecraft:overworld},
 * {@code my_pack:skylands}) are accepted, so a Paper config can be dropped in unchanged.
 * Like {@code Bukkit.getWorld(String)}, matching is case-insensitive.
 */
public final class WorldNames {
    private WorldNames() {
    }

    /** The Bukkit main world name: the world folder name ({@code level-name}). */
    public static String levelName(final MinecraftServer server) {
        final Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        final Path name = root.getFileName();
        return name != null ? name.toString() : "world";
    }

    public static ServerLevel resolve(final MinecraftServer server, final String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        final String lower = key.toLowerCase(Locale.ROOT);
        for (final String base : new String[]{levelName(server).toLowerCase(Locale.ROOT), "world"}) {
            if (lower.equals(base)) {
                return server.getLevel(Level.OVERWORLD);
            }
            if (lower.equals(base + "_nether")) {
                return server.getLevel(Level.NETHER);
            }
            if (lower.equals(base + "_the_end")) {
                return server.getLevel(Level.END);
            }
        }
        if (lower.indexOf(':') >= 0) {
            final Identifier id = Compat.parseId(lower);
            return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        for (final ServerLevel level : server.getAllLevels()) {
            final Identifier id = Compat.keyId(level.dimension());
            for (final String base : new String[]{levelName(server).toLowerCase(Locale.ROOT), "world"}) {
                if (lower.equals(base + "_" + id.getNamespace() + "_" + id.getPath())) {
                    return level;
                }
            }
        }
        final Identifier id = Compat.parseId(lower);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** The name Bukkit would give this dimension. */
    public static String bukkitName(final MinecraftServer server, final ServerLevel level) {
        final String base = levelName(server);
        final ResourceKey<Level> key = level.dimension();
        if (Level.OVERWORLD.equals(key)) {
            return base;
        }
        if (Level.NETHER.equals(key)) {
            return base + "_nether";
        }
        if (Level.END.equals(key)) {
            return base + "_the_end";
        }
        final Identifier id = Compat.keyId(key);
        return base + "_" + id.getNamespace() + "_" + id.getPath();
    }

    public static String available(final MinecraftServer server) {
        final StringBuilder sb = new StringBuilder();
        for (final ServerLevel level : server.getAllLevels()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(bukkitName(server, level)).append(" (").append(Compat.keyId(level.dimension())).append(")");
        }
        return sb.isEmpty() ? "none" : sb.toString();
    }
}
