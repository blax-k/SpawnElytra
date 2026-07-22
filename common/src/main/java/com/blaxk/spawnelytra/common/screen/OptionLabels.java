/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.text.Msg;

/** Display labels (lang keys) of option values for every {@code OPTION} key of both key tables. */
public enum OptionLabels {
    ;

    /** Label of {@code value} for option key {@code key}; falls back to a literal of the value. */
    public static Msg of(final String key, final String value) {
        if ("inherit".equals(value)) {
            return Msg.of("menu_option_inherit");
        }
        return switch (key) {
            case "shape" -> Msg.of("zone_shape_" + value);
            case "activation_mode" -> Msg.of("zone_mode_" + value);
            case "boost.direction" -> Msg.of("zone_direction_" + value);
            case "boost_display" -> Msg.of("menu_boost_display_" + value);
            case "fireworks.disable_in_spawn_elytra" -> Msg.of("true".equals(value) ? "menu_fireworks_blocked" : "menu_fireworks_allowed");
            case "hunger_consumption" -> Msg.of("off".equals(value) ? "menu_hunger_off" : "menu_hunger_mode_" + value);
            case "hunger_consumption.mode" -> Msg.of("menu_hunger_mode_" + value);
            case "language" -> Msg.of("menu_language_" + value);
            case "messages.style" -> Msg.of("menu_style_" + value);
            case "menus.mode" -> Msg.of("menu_menus_mode_" + value);
            default -> Msg.literal(value);
        };
    }
}
