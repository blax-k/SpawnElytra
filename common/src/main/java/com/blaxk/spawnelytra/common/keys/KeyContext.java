/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.HungerSettings;

/** Platform lookups needed to validate {@code /se zone set} values. */
public interface KeyContext {

    /** World name / dimension id exists (and is loaded). */
    boolean worldExists(String world);

    /**
     * Sound exists in the sound registry. Bukkit enum names ({@code ENTITY_BAT_TAKEOFF}) and namespaced keys
     * ({@code minecraft:entity.bat.takeoff}) must both be accepted.
     */
    boolean soundExists(String sound);

    /** Another zone (not the one being edited) already uses this name (case-insensitive). */
    boolean zoneNameTaken(String name);

    /** Current world spawn X/Z (for converting world-spawn circles), {@code null} if unknown. */
    Vec2 worldSpawn(String world);

    /** Global hunger settings (seed values when a per-zone hunger override is created). */
    HungerSettings globalHunger();

    /** Accept-everything context (tests, offline validation). */
    static KeyContext lenient() {
        return new KeyContext() {
            @Override
            public boolean worldExists(final String world) {
                return true;
            }

            @Override
            public boolean soundExists(final String sound) {
                return true;
            }

            @Override
            public boolean zoneNameTaken(final String name) {
                return false;
            }

            @Override
            public Vec2 worldSpawn(final String world) {
                return new Vec2(0, 0);
            }

            @Override
            public HungerSettings globalHunger() {
                return HungerSettings.DEFAULTS;
            }
        };
    }
}
