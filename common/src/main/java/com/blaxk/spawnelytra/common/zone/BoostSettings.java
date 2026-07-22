/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.config.ConfigView;

/**
 * A zone's {@code boost} section (1.5 semantics). Raw values; {@link #effectiveMaxBoosts()} and
 * {@link #cooldownMillis()} apply the 1.5 clamping.
 */
public record BoostSettings(boolean enabled, double strength, BoostDirection direction, int maxBoosts,
                            double cooldownSeconds, String sound) {

    public static final String DEFAULT_SOUND = "ENTITY_BAT_TAKEOFF";

    /** Shipped defaults ({@code strength: 4}). */
    public static final BoostSettings DEFAULTS = new BoostSettings(true, 4, BoostDirection.FORWARD, 1, 0, BoostSettings.DEFAULT_SOUND);

    /**
     * Reads a {@code boost} section with the 1.5 code defaults for missing keys
     * (enabled true, strength 2, forward, max_boosts 1, cooldown 0, ENTITY_BAT_TAKEOFF).
     */
    public static BoostSettings read(final ConfigView section) {
        if (section == null) {
            return new BoostSettings(true, 2, BoostDirection.FORWARD, 1, 0, BoostSettings.DEFAULT_SOUND);
        }
        return new BoostSettings(
                section.getBoolean("enabled", true),
                section.getDouble("strength", 2),
                BoostDirection.fromIdOrDefault(section.getString("direction", "forward")),
                section.getInt("max_boosts", 1),
                section.getDouble("boost_cooldown", 0),
                section.getString("sound", BoostSettings.DEFAULT_SOUND));
    }

    /** Writes all keys (canonical numbers) into the section. */
    public void write(final ConfigView section) {
        section.set("enabled", this.enabled);
        section.set("strength", ConfigNumbers.canonical(this.strength));
        section.set("direction", this.direction.id());
        section.set("max_boosts", this.maxBoosts);
        section.set("boost_cooldown", ConfigNumbers.canonical(this.cooldownSeconds));
        section.set("sound", this.sound);
    }

    /** 1.5: {@code max(1, max_boosts)}. */
    public int effectiveMaxBoosts() {
        return Math.max(1, this.maxBoosts);
    }

    /** 1.5: {@code max(0, (long) (boost_cooldown * 1000))}. */
    public long cooldownMillis() {
        return Math.max(0L, (long) (this.cooldownSeconds * 1000));
    }

    public BoostSettings withEnabled(final boolean v) {
        return new BoostSettings(v, this.strength, this.direction, this.maxBoosts, this.cooldownSeconds, this.sound);
    }

    public BoostSettings withStrength(final double v) {
        return new BoostSettings(this.enabled, v, this.direction, this.maxBoosts, this.cooldownSeconds, this.sound);
    }

    public BoostSettings withDirection(final BoostDirection v) {
        return new BoostSettings(this.enabled, this.strength, v, this.maxBoosts, this.cooldownSeconds, this.sound);
    }

    public BoostSettings withMaxBoosts(final int v) {
        return new BoostSettings(this.enabled, this.strength, this.direction, v, this.cooldownSeconds, this.sound);
    }

    public BoostSettings withCooldownSeconds(final double v) {
        return new BoostSettings(this.enabled, this.strength, this.direction, this.maxBoosts, v, this.sound);
    }

    public BoostSettings withSound(final String v) {
        return new BoostSettings(this.enabled, this.strength, this.direction, this.maxBoosts, this.cooldownSeconds, v);
    }
}
