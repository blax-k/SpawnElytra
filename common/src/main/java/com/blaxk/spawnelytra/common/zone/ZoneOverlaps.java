/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Zone-vs-zone overlap detection (editor highlighting, save warnings). */
public enum ZoneOverlaps {
    ;

    /**
     * {@code true} if both zones are in the same world (after {@code worldKey}), their height ranges intersect and
     * their shapes share at least one point. Enabled state is ignored.
     */
    public static boolean overlaps(final Zone a, final Zone b, final Function<String, String> worldKey, final Vec2 worldSpawn) {
        final Function<String, String> key = worldKey == null ? Function.identity() : worldKey;
        if (!key.apply(a.world()).equals(key.apply(b.world()))) {
            return false;
        }
        final double aMin = a.minHeight() == null ? Double.NEGATIVE_INFINITY : a.minHeight();
        final double aMax = a.maxHeight() == null ? Double.POSITIVE_INFINITY : a.maxHeight();
        final double bMin = b.minHeight() == null ? Double.NEGATIVE_INFINITY : b.minHeight();
        final double bMax = b.maxHeight() == null ? Double.POSITIVE_INFINITY : b.maxHeight();
        if (aMin > bMax || bMin > aMax) {
            return false;
        }
        return ShapeOps.intersects(a.shape(), b.shape(), worldSpawn);
    }

    /**
     * Zones of {@code registry} (same world, other name than {@code zone}) that overlap {@code zone}, in resolution
     * order. Used for the save warning ("overlaps X (priority 5)").
     */
    public static List<Zone> overlapping(final Zone zone, final ZoneRegistry registry, final ZoneRegistry.SpawnLookup spawns) {
        final Vec2 spawn = spawns == null ? null : spawns.spawn(zone.world());
        final List<Zone> out = new ArrayList<>();
        for (final Zone other : registry.inWorld(zone.world())) {
            if (!other.name().equalsIgnoreCase(zone.name()) && ZoneOverlaps.overlaps(zone, other, registry.worldKey(), spawn)) {
                out.add(other);
            }
        }
        return out;
    }
}
