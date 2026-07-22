/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common;

/**
 * Shared, platform-neutral core of SpawnElytra (pure Java 21, no Bukkit/Minecraft/Adventure).
 * See {@code common/CORE-API.md} for an overview of the packages and how the platforms use them.
 */
public enum SpawnElytraCore {
    ;

    /** Plugin/mod and config version. */
    public static final String VERSION = "1.6";

    /** Namespace for dialog actions, PDC keys and custom data. */
    public static final String NAMESPACE = "spawnelytra";

    // ---- permissions (spec §10) ----
    public static final String PERM_ADMIN = "spawnelytra.admin";
    public static final String PERM_INFO = "spawnelytra.info";
    public static final String PERM_TOGGLE = "spawnelytra.toggle";
    public static final String PERM_STATS = "spawnelytra.stats";
    public static final String PERM_STATS_OTHERS = "spawnelytra.stats.others";
    public static final String PERM_USE = "spawnelytra.use";
    public static final String PERM_USE_BOOST = "spawnelytra.useboost";

    /** Player-data key of the {@code /se toggle} state ({@code true} = spawn elytra enabled for the player). */
    public static final String PLAYER_DATA_ENABLED = "enabled";
    /** Player-data section holding the editor inventory snapshot (platform-specific content). */
    public static final String PLAYER_DATA_EDITOR_SNAPSHOT = "editor_snapshot";
}
