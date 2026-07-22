/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.tier;

/**
 * One {@code permission_tiers.<name>} entry (spec §3). Every override is optional ({@code null} = keep zone value).
 *
 * @param name           lowercase tier name ({@code [a-z0-9_-]{1,32}})
 * @param priority       higher wins when a player has several tiers
 * @param maxBoosts      overrides {@code boost.max_boosts}
 * @param strength       overrides {@code boost.strength}
 * @param boostCooldown  overrides {@code boost.boost_cooldown} (seconds)
 * @param launchStrength overrides {@code f_key.launch_strength}
 */
public record PermissionTier(String name, int priority, Integer maxBoosts, Double strength, Double boostCooldown,
                             Double launchStrength) {

    public static final String PERMISSION_PREFIX = "spawnelytra.tier.";

    /** {@code spawnelytra.tier.<name>}. Default: false for everyone, ops included (see decisions log). */
    public String permission() {
        return PermissionTier.PERMISSION_PREFIX + this.name;
    }
}
