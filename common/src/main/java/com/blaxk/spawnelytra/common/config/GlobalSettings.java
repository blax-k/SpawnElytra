/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.config;

import com.blaxk.spawnelytra.common.tier.PermissionTier;
import com.blaxk.spawnelytra.common.tier.TierResolver;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.HungerSettings;

import java.util.List;

/**
 * The global (non-zone) settings of {@code config.yml} 1.6 that the core cares about. Visualization and
 * first-install flags stay platform-read as in 1.5.
 */
public record GlobalSettings(String language, String style, BoostDisplay boostDisplay, MenusMode menusMode,
                             boolean disableInCreative, boolean disableInAdventure, boolean fireworksDisabled,
                             boolean bedrockEnabled, boolean showPressToBoost, boolean showBoostActivated,
                             boolean showCreativeDisabled, HungerSettings hunger, List<PermissionTier> tiers) {

    public static final List<String> LANGUAGES = List.of("en", "de", "es", "fr", "pl");
    public static final List<String> STYLES = List.of("classic", "small_caps");

    /** Reads the globals with the same defaults as 1.5 (+ 1.6 defaults for the new keys). */
    public static GlobalSettings read(final ConfigView root) {
        final ConfigView hunger = root.section("hunger_consumption");
        return new GlobalSettings(
                root.getString("language", "en"),
                root.getString("messages.style", "classic"),
                BoostDisplay.fromIdOrDefault(root.getString("boost_display", "actionbar")),
                MenusMode.fromIdOrDefault(root.getString("menus.mode", "auto")),
                root.getBoolean("game_modes.disable_in_creative", true),
                root.getBoolean("game_modes.disable_in_adventure", false),
                root.getBoolean("fireworks.disable_in_spawn_elytra", false),
                root.getBoolean("bedrock.enabled", true),
                root.getBoolean("messages.show_press_to_boost", true),
                root.getBoolean("messages.show_boost_activated", true),
                root.getBoolean("messages.show_creative_disabled", false),
                hunger == null ? HungerSettings.DEFAULTS : HungerSettings.read(hunger, HungerSettings.DEFAULTS),
                TierResolver.read(root).tiers());
    }
}
