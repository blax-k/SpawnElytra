/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.tier;

import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ZoneNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads {@code permission_tiers} and resolves a player's effective tier (spec §3). */
public enum TierResolver {
    ;

    public static final String SECTION = "permission_tiers";

    /** Resolution order: highest priority first, ties by name ascending. */
    public static final Comparator<PermissionTier> ORDER =
            Comparator.comparingInt(PermissionTier::priority).reversed().thenComparing(PermissionTier::name);

    /** Parsed tiers plus warnings for skipped entries ({@code tier_load_*} keys). */
    public record ReadResult(List<PermissionTier> tiers, List<Msg> warnings) {
    }

    /** Reads {@code root.permission_tiers}; tiers are returned in resolution order. */
    public static ReadResult read(final ConfigView root) {
        final List<PermissionTier> tiers = new ArrayList<>();
        final List<Msg> warnings = new ArrayList<>();
        final ConfigView section = root.section(TierResolver.SECTION);
        if (section != null) {
            for (final String key : section.keys()) {
                final String name = ZoneNames.normalize(key);
                final ConfigView t = section.section(key);
                if (t == null || !ZoneNames.isValid(name)) {
                    warnings.add(Msg.of("tier_load_invalid", "tier", key));
                    continue;
                }
                if (tiers.stream().anyMatch(x -> x.name().equals(name))) {
                    warnings.add(Msg.of("tier_load_invalid", "tier", key));
                    continue;
                }
                tiers.add(new PermissionTier(name, t.getInt("priority", 0), t.getIntOrNull("max_boosts"),
                        t.getDoubleOrNull("strength"), t.getDoubleOrNull("boost_cooldown"), t.getDoubleOrNull("launch_strength")));
            }
        }
        tiers.sort(TierResolver.ORDER);
        return new ReadResult(List.copyOf(tiers), warnings);
    }

    /**
     * The tier with the highest priority whose permission ({@code spawnelytra.tier.<name>}) the player has.
     *
     * @param hasPermission platform permission check of the node (must default to false for ops too)
     */
    public static Optional<PermissionTier> resolve(final List<PermissionTier> tiers, final Predicate<String> hasPermission) {
        PermissionTier best = null;
        for (final PermissionTier t : tiers) {
            if (hasPermission.test(t.permission()) && (best == null || TierResolver.ORDER.compare(t, best) < 0)) {
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }
}
