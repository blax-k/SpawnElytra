/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.integration;

import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.util.Compat;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission checks through fabric-permissions-api (LuckPerms etc.) with the defaults of the
 * Paper plugin.yml: {@code spawnelytra.admin} defaults to operators ({@code default: op}),
 * {@code spawnelytra.use} and {@code spawnelytra.useboost} default to everyone. Non-player
 * senders (console, RCON) have every permission, like the Bukkit console.
 */
public final class Perms {
    public static final String ADMIN = "spawnelytra.admin";
    public static final String USE = "spawnelytra.use";
    public static final String USE_BOOST = "spawnelytra.useboost";
    public static final String INFO = "spawnelytra.info";
    public static final String TOGGLE = "spawnelytra.toggle";
    public static final String STATS = "spawnelytra.stats";
    public static final String STATS_OTHERS = "spawnelytra.stats.others";
    public static final String TIER_PREFIX = "spawnelytra.tier.";

    private Perms() {
    }

    /**
     * Defaults of the Paper plugin.yml: admin and stats.others for operators; use, useboost, info,
     * toggle and stats for everyone; tier nodes for nobody (not even operators, they are declared
     * with {@code default: false}).
     */
    private static boolean defaultFor(final ServerPlayer player, final String node) {
        if (node.startsWith(TIER_PREFIX)) {
            return false;
        }
        if (ADMIN.equals(node) || STATS_OTHERS.equals(node)) {
            return Compat.isOp(player);
        }
        return USE.equals(node) || USE_BOOST.equals(node) || INFO.equals(node) || TOGGLE.equals(node)
                || STATS.equals(node) || Compat.isOp(player);
    }

    private static volatile boolean apiBroken;

    public static boolean has(final ServerPlayer player, final String node) {
        if (player == null) {
            return false;
        }
        final boolean def = defaultFor(player, node);
        if (apiBroken) {
            return def;
        }
        try {
            return Permissions.check(player, node, def);
        } catch (final LinkageError e) {
            // Another mod shipped an incompatible fabric-permissions-api build; keep working with defaults.
            apiBroken = true;
            final Main plugin = Main.get();
            if (plugin != null) {
                plugin.getLogger().error("fabric-permissions-api is incompatible with this Minecraft version ({}); using default permissions.", e.toString());
            }
            return def;
        }
    }

    public static boolean has(final CommandSourceStack source, final String node) {
        final ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return has(player, node);
    }
}
