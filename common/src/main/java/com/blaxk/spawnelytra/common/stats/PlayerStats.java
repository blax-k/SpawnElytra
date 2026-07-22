/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.stats;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.config.ConfigView;

/**
 * Per-player statistics (spec §5). Immutable; platforms keep the current value per player and replace it on
 * every event (mark the player dirty, flush async).
 *
 * @param flights          number of activations
 * @param distance         blocks glided (3D, see {@link FlightTracker})
 * @param boosts           boosts used
 * @param glideTimeSeconds total gliding time in seconds
 * @param longestFlight    longest single flight in blocks
 */
public record PlayerStats(long flights, double distance, long boosts, double glideTimeSeconds, double longestFlight) {

    public static final PlayerStats ZERO = new PlayerStats(0, 0, 0, 0, 0);

    /** Player-data section name ({@code stats:} in {@code playerdata/<uuid>.yml}). */
    public static final String SECTION = "stats";

    /** Counts one activation. */
    public PlayerStats withFlightStarted() {
        return new PlayerStats(this.flights + 1, this.distance, this.boosts, this.glideTimeSeconds, this.longestFlight);
    }

    /** Counts one boost. */
    public PlayerStats withBoost() {
        return new PlayerStats(this.flights, this.distance, this.boosts + 1, this.glideTimeSeconds, this.longestFlight);
    }

    /** Adds a finished flight's distance and duration and updates the longest flight. */
    public PlayerStats withFlightCompleted(final double flightDistance, final double flightSeconds) {
        final double d = Math.max(0, flightDistance);
        final double t = Math.max(0, flightSeconds);
        return new PlayerStats(this.flights, this.distance + d, this.boosts, this.glideTimeSeconds + t,
                Math.max(this.longestFlight, d));
    }

    /** Reads {@code flights, distance, boosts, glide_time, longest_flight} (missing → 0). {@code null} → ZERO. */
    public static PlayerStats read(final ConfigView section) {
        if (section == null) {
            return PlayerStats.ZERO;
        }
        return new PlayerStats(section.getLong("flights", 0), section.getDouble("distance", 0),
                section.getLong("boosts", 0), section.getDouble("glide_time", 0), section.getDouble("longest_flight", 0));
    }

    /** Writes the stats (distance/longest/glide time rounded to 1 decimal) into the section. */
    public void write(final ConfigView section) {
        section.set("flights", this.flights);
        section.set("distance", ConfigNumbers.round(this.distance, 1));
        section.set("boosts", this.boosts);
        section.set("glide_time", ConfigNumbers.round(this.glideTimeSeconds, 1));
        section.set("longest_flight", ConfigNumbers.round(this.longestFlight, 1));
    }
}
