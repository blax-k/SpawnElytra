/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.tier;

import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.Zone;

/**
 * The settings that apply to one player's glide in one zone: zone values, overridden field-by-field by the
 * player's tier, with zone overrides falling back to globals. Already clamped like 1.5
 * ({@code maxBoosts >= 1}, {@code cooldownMillis >= 0}).
 *
 * @param tier the applied tier or {@code null}
 */
public record EffectiveSettings(Zone zone, PermissionTier tier, ActivationMode activationMode, boolean boostEnabled,
                                double strength, BoostDirection direction, int maxBoosts, long cooldownMillis,
                                String sound, double launchStrength, BoostDisplay boostDisplay,
                                boolean fireworksDisabled, HungerSettings hunger) {

    /** Resolves zone + globals + optional tier. */
    public static EffectiveSettings resolve(final Zone zone, final GlobalSettings globals, final PermissionTier tier) {
        final double strength = tier != null && tier.strength() != null ? tier.strength() : zone.boost().strength();
        final int maxBoosts = Math.max(1, tier != null && tier.maxBoosts() != null ? tier.maxBoosts() : zone.boost().maxBoosts());
        final double cooldown = tier != null && tier.boostCooldown() != null ? tier.boostCooldown() : zone.boost().cooldownSeconds();
        final double launch = tier != null && tier.launchStrength() != null ? tier.launchStrength() : zone.launchStrength();
        return new EffectiveSettings(zone, tier, zone.activationMode(), zone.boost().enabled(), strength,
                zone.boost().direction(), maxBoosts, Math.max(0L, (long) (cooldown * 1000)), zone.boost().sound(), launch,
                zone.boostDisplayOverride() != null ? zone.boostDisplayOverride() : globals.boostDisplay(),
                zone.fireworksDisabledOverride() != null ? zone.fireworksDisabledOverride() : globals.fireworksDisabled(),
                zone.hungerOverride() != null ? zone.hungerOverride() : globals.hunger());
    }

    /** Tier name for {@code /se info} and the {@code tier} placeholder ({@code ""} when none). */
    public String tierName() {
        return this.tier == null ? "" : this.tier.name();
    }
}
