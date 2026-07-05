/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.util;

import java.util.Locale;

/**
 * Human-friendly display names for config values, shared across the command handler,
 * the settings menu and the setup wizard so the mappings live in a single place.
 */
public enum DisplayNames {
    ;

    public static String language(final String lang) {
        if (lang == null) {
            return "-";
        }
        return switch (lang.toLowerCase(Locale.ROOT)) {
            case "de" -> "Deutsch";
            case "en" -> "English";
            case "es" -> "Español";
            case "fr" -> "Français";
            case "pl" -> "Polski";
            default -> lang;
        };
    }

    public static String activationMode(final String mode) {
        if (mode == null) {
            return "-";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "double_jump" -> "Double Jump";
            case "auto" -> "Auto";
            case "sneak_jump" -> "Sneak Jump";
            case "f_key" -> "F Key";
            default -> mode;
        };
    }

    public static String spawnMode(final String mode) {
        if (mode == null) {
            return "-";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "auto" -> "Auto";
            case "advanced" -> "Advanced";
            default -> mode;
        };
    }
}
