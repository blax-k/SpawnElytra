/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

/**
 * Value types of settable keys and their accepted command syntax.
 * <ul>
 *     <li>{@code BOOLEAN}: {@code true|false|on|off|yes|no|toggle}</li>
 *     <li>{@code INT}, {@code DOUBLE}: a number within [min, max] ({@code .} decimal separator)</li>
 *     <li>{@code OPTIONAL_INT}: a whole number or {@code none|unset|null|-} (removes the key)</li>
 *     <li>{@code OPTIONAL_DOUBLE}: a number or {@code none|unset|null|-} (removes the key; {@code min_y}/{@code max_y})</li>
 *     <li>{@code OPTION}: one of the options, or {@code next} (cycles, used by chat menus)</li>
 *     <li>{@code TEXT}: free text (name, world, sound; validated per key)</li>
 *     <li>{@code POINT}: {@code x,z}</li>
 *     <li>{@code POINT_LIST}: {@code x,z;x,z;x,z} (≥ 3)</li>
 *     <li>{@code CENTER}: {@code world_spawn} or {@code x,z}</li>
 * </ul>
 */
public enum KeyType {
    BOOLEAN,
    INT,
    DOUBLE,
    OPTIONAL_INT,
    OPTIONAL_DOUBLE,
    OPTION,
    TEXT,
    POINT,
    POINT_LIST,
    CENTER
}
