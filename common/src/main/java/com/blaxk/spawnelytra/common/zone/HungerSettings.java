/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.config.ConfigView;

/**
 * Hunger consumption settings, same shape as the global {@code hunger_consumption} section of 1.5. Holds the raw
 * configured values; the {@code effective*} getters apply the exact 1.5 clamping.
 */
public record HungerSettings(boolean enabled, HungerMode mode, int minimumFoodLevel, int activationCost,
                             double blocksPerPoint, int distanceCost, double secondsPerPoint, int timeCost) {

    /** The shipped defaults (disabled). */
    public static final HungerSettings DEFAULTS = new HungerSettings(false, HungerMode.ACTIVATION, 0, 1, 50.0, 1, 30.0, 1);

    /**
     * Reads a {@code hunger_consumption} section. Missing keys fall back to {@code fallback} (pass
     * {@link #DEFAULTS} for the global section, the global settings for a per-zone override).
     * A {@code null} section returns {@code fallback}.
     */
    public static HungerSettings read(final ConfigView section, final HungerSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new HungerSettings(
                section.getBoolean("enabled", fallback.enabled),
                section.contains("mode") ? HungerMode.fromIdOrDefault(section.getString("mode", null)) : fallback.mode,
                section.getInt("minimum_food_level", fallback.minimumFoodLevel),
                section.getInt("activation.hunger_cost", fallback.activationCost),
                section.getDouble("distance.blocks_per_point", fallback.blocksPerPoint),
                section.getInt("distance.hunger_cost", fallback.distanceCost),
                section.getDouble("time.seconds_per_point", fallback.secondsPerPoint),
                section.getInt("time.hunger_cost", fallback.timeCost));
    }

    /** Writes all keys into the given section (canonical numbers). */
    public void write(final ConfigView section) {
        section.set("enabled", this.enabled);
        section.set("mode", this.mode.id());
        section.set("minimum_food_level", this.minimumFoodLevel);
        section.set("activation.hunger_cost", this.activationCost);
        section.set("distance.blocks_per_point", ConfigNumbers.canonical(this.blocksPerPoint));
        section.set("distance.hunger_cost", this.distanceCost);
        section.set("time.seconds_per_point", ConfigNumbers.canonical(this.secondsPerPoint));
        section.set("time.hunger_cost", this.timeCost);
    }

    /** 1.5: {@code max(0, minimum_food_level)}. */
    public int effectiveMinimumFoodLevel() {
        return Math.max(0, this.minimumFoodLevel);
    }

    /** 1.5: {@code max(0, activation.hunger_cost)}. */
    public int effectiveActivationCost() {
        return Math.max(0, this.activationCost);
    }

    /** 1.5: {@code max(1.0, distance.blocks_per_point)}. */
    public double effectiveBlocksPerPoint() {
        return Math.max(1.0, this.blocksPerPoint);
    }

    /** 1.5: {@code max(0, distance.hunger_cost)}. */
    public int effectiveDistanceCost() {
        return Math.max(0, this.distanceCost);
    }

    /** 1.5: {@code max(1, ceil(seconds_per_point * 1000))}. */
    public long effectiveTimeIntervalMillis() {
        return Math.max(1L, (long) Math.ceil(this.secondsPerPoint * 1000.0));
    }

    /** 1.5: {@code max(0, time.hunger_cost)}. */
    public int effectiveTimeCost() {
        return Math.max(0, this.timeCost);
    }

    public HungerSettings withEnabled(final boolean v) {
        return new HungerSettings(v, this.mode, this.minimumFoodLevel, this.activationCost, this.blocksPerPoint, this.distanceCost, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withMode(final HungerMode v) {
        return new HungerSettings(this.enabled, v, this.minimumFoodLevel, this.activationCost, this.blocksPerPoint, this.distanceCost, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withMinimumFoodLevel(final int v) {
        return new HungerSettings(this.enabled, this.mode, v, this.activationCost, this.blocksPerPoint, this.distanceCost, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withActivationCost(final int v) {
        return new HungerSettings(this.enabled, this.mode, this.minimumFoodLevel, v, this.blocksPerPoint, this.distanceCost, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withBlocksPerPoint(final double v) {
        return new HungerSettings(this.enabled, this.mode, this.minimumFoodLevel, this.activationCost, v, this.distanceCost, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withDistanceCost(final int v) {
        return new HungerSettings(this.enabled, this.mode, this.minimumFoodLevel, this.activationCost, this.blocksPerPoint, v, this.secondsPerPoint, this.timeCost);
    }

    public HungerSettings withSecondsPerPoint(final double v) {
        return new HungerSettings(this.enabled, this.mode, this.minimumFoodLevel, this.activationCost, this.blocksPerPoint, this.distanceCost, v, this.timeCost);
    }

    public HungerSettings withTimeCost(final int v) {
        return new HungerSettings(this.enabled, this.mode, this.minimumFoodLevel, this.activationCost, this.blocksPerPoint, this.distanceCost, this.secondsPerPoint, v);
    }
}
