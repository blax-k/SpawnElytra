/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.stats;

/**
 * Accumulates one glide (activation → landing). Not thread-safe: owned by the player's (region) thread.
 * Distance is the 3D path length between consecutive samples; single steps longer than {@link #MAX_STEP}
 * (teleports, world changes) are ignored.
 */
public final class FlightTracker {

    /** Steps longer than this (blocks) between two samples are treated as teleports and not counted. */
    public static final double MAX_STEP = 50.0;

    private final long startMillis;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double distance;
    private int boosts;

    public FlightTracker(final long startMillis, final double x, final double y, final double z) {
        this.startMillis = startMillis;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    /** Records the current position (call on move / every tick while gliding). */
    public void move(final double x, final double y, final double z) {
        final double dx = x - this.lastX;
        final double dy = y - this.lastY;
        final double dz = z - this.lastZ;
        final double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (step <= FlightTracker.MAX_STEP) {
            this.distance += step;
        }
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    public void boost() {
        this.boosts++;
    }

    public double distance() {
        return this.distance;
    }

    public int boosts() {
        return this.boosts;
    }

    public long startMillis() {
        return this.startMillis;
    }

    public double durationSeconds(final long nowMillis) {
        return Math.max(0, nowMillis - this.startMillis) / 1000.0;
    }

    /** Adds this flight's distance and duration to the stats (flights/boosts are counted when they happen). */
    public PlayerStats complete(final PlayerStats stats, final long endMillis) {
        return stats.withFlightCompleted(this.distance, this.durationSeconds(endMillis));
    }
}
