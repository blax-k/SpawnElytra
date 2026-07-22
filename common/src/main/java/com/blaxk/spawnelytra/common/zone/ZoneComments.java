/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The explanatory comments of the shipped {@code config.yml} for the 1.6 keys, used when the migration writes
 * zones and new global keys (so a migrated config reads like a fresh one). A {@code null} line is a blank line,
 * exactly like Bukkit's comment API.
 */
public enum ZoneComments {
    ;

    private static List<String> lines(final String... lines) {
        return Collections.unmodifiableList(Arrays.asList(lines));
    }

    /** Comment block above the top-level {@code zones:} key. */
    public static final List<String> ZONES_SECTION = ZoneComments.lines(
            null,
            "==========================================",
            "ZONES",
            "==========================================",
            null,
            "A zone is a named area in one world where spawn elytra works. You can have many zones",
            "in many worlds. Manage them in-game with /se (overview), /se zone create <name>,",
            "/se zone edit <name> and /se zone set <name> <key> <value>.",
            "Zone names: lowercase letters, digits, '_' and '-' (max. 32 characters).",
            null,
            "Optional keys per zone (remove them to use the global value / no limit):",
            "  min_y / max_y: height limits of the zone",
            "  boost_display: actionbar, bossbar or none",
            "  fireworks: { disable_in_spawn_elytra: true/false }",
            "  hunger_consumption: same keys as the global hunger_consumption section");

    /** Per-key comments inside one zone section (paths relative to the zone). */
    public static final Map<String, List<String>> DEFAULTS;

    static {
        final Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("world", ZoneComments.lines("World of this zone (e.g. world, world_nether, world_the_end)"));
        m.put("enabled", ZoneComments.lines("Enable spawn elytra in this zone"));
        m.put("priority", ZoneComments.lines("If zones overlap, the zone with the highest priority wins (same priority: alphabetical)"));
        m.put("shape", ZoneComments.lines(
                null,
                "Shape of the zone: circle, rectangle or polygon",
                "circle: uses 'center' and 'radius'",
                "rectangle: uses 'corner1' and 'corner2' ({x, z} each)",
                "polygon: uses 'points' (a list of at least 3 {x, z} points)"));
        m.put("center", ZoneComments.lines("Center of the circle: world_spawn (follows the world spawn point) or fixed coordinates {x, z}"));
        m.put("radius", ZoneComments.lines("Radius of the circle in blocks"));
        m.put("corner1", ZoneComments.lines("First corner of the rectangle"));
        m.put("corner2", ZoneComments.lines("Opposite corner of the rectangle"));
        m.put("points", ZoneComments.lines("Corner points of the polygon (in order)"));
        m.put("min_y", ZoneComments.lines("Lowest Y level of the zone (remove for no limit)"));
        m.put("max_y", ZoneComments.lines("Highest Y level of the zone (remove for no limit)"));
        m.put("activation_mode", ZoneComments.lines(
                null,
                "Activation mode for elytra:",
                "double_jump: Player needs to double-press space to activate elytra",
                "auto: Automatically activates elytra when player has air below and is in the zone",
                "sneak_jump: Player needs to sneak while jumping to activate elytra",
                "f_key: Player needs to press F (swap hands) to activate elytra, this also boosts a player upwards on activation"));
        m.put("boost", ZoneComments.lines(null, "Boost settings"));
        m.put("boost.enabled", ZoneComments.lines("Enable boost functionality"));
        m.put("boost.strength", ZoneComments.lines("The strength of the boost when pressing the boost key"));
        m.put("boost.direction", ZoneComments.lines(
                "Boost direction: 'forward' or 'upward'",
                "forward: Boosts player in the direction they are looking",
                "upward: Boosts player straight up"));
        m.put("boost.max_boosts", ZoneComments.lines("Maximum number of boosts allowed per elytra flight (1 = single boost)"));
        m.put("boost.boost_cooldown", ZoneComments.lines("Cooldown in seconds between boosts (0 = no cooldown, only applies when max_boosts > 1)"));
        m.put("boost.sound", ZoneComments.lines(
                "Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html",
                "Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING"));
        m.put("f_key", ZoneComments.lines(null, "F-key specific settings (only used when activation_mode: f_key)"));
        m.put("f_key.launch_strength", ZoneComments.lines("Launch strength when pressing F key (1.5 = ~14-15 blocks upward)"));
        m.put("boost_display", ZoneComments.lines(null, "Overrides the global boost_display for this zone"));
        m.put("fireworks", ZoneComments.lines(null, "Overrides the global fireworks setting for this zone"));
        m.put("hunger_consumption", ZoneComments.lines(null, "Overrides the global hunger consumption settings for this zone"));
        DEFAULTS = Collections.unmodifiableMap(m);
    }
}
