/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Vec2;

/**
 * An immutable spawn-elytra zone (spec §1). Optional fields are {@code null} when absent:
 * {@code minHeight}/{@code maxHeight} ({@code min_y}/{@code max_y}; unlimited), {@code hungerOverride}, {@code fireworksDisabledOverride},
 * {@code boostDisplayOverride} (all: use the global value).
 *
 * @param name                      lowercase unique name ({@link ZoneNames#PATTERN})
 * @param world                     world name as written in the config (Bukkit name; Fabric may use dimension ids)
 * @param launchStrength            {@code f_key.launch_strength}
 * @param fireworksDisabledOverride {@code fireworks.disable_in_spawn_elytra} override
 */
public record Zone(String name, String world, boolean enabled, int priority, Shape shape, Double minHeight, Double maxHeight,
                   ActivationMode activationMode, BoostSettings boost, double launchStrength,
                   HungerSettings hungerOverride, Boolean fireworksDisabledOverride, BoostDisplay boostDisplayOverride) {

    public static final double DEFAULT_LAUNCH_STRENGTH = 1.5;
    public static final int MIN_Y_LIMIT = -2048;
    public static final int MAX_Y_LIMIT = 4096;

    /** A fresh zone with shipped defaults (double_jump, boost defaults, launch 1.5, no overrides). */
    public static Zone createDefault(final String name, final String world, final Shape shape) {
        return new Zone(name, world, true, 0, shape, null, null, ActivationMode.DOUBLE_JUMP, BoostSettings.DEFAULTS,
                Zone.DEFAULT_LAUNCH_STRENGTH, null, null, null);
    }

    /**
     * Full 3D containment: shape contains (x, z) and {@code min_y <= y <= max_y} (raw doubles, inclusive — exactly the
     * 1.5 box test for migrated rectangles) for the bounds that are set.
     */
    public boolean contains(final double x, final double y, final double z, final Vec2 worldSpawn) {
        return this.containsY(y) && this.shape.contains(x, z, worldSpawn);
    }

    /** Height test only ({@code true} when no bound is set). */
    public boolean containsY(final double y) {
        if (this.minHeight != null && y < this.minHeight) {
            return false;
        }
        return this.maxHeight == null || y <= this.maxHeight;
    }

    public boolean hasHeightLimit() {
        return this.minHeight != null || this.maxHeight != null;
    }

    /** {@code null} if the zone is internally valid, else the lang key (geometry or height problem). */
    public String validate() {
        if (!ZoneNames.isValid(this.name)) {
            return "zone_error_invalid_name";
        }
        if (this.world == null || this.world.isBlank()) {
            return "zone_error_world_missing";
        }
        final String shapeProblem = this.shape == null ? "editor_error_shape_missing" : this.shape.validate();
        if (shapeProblem != null) {
            return shapeProblem;
        }
        if (this.minHeight != null && this.maxHeight != null && this.minHeight > this.maxHeight) {
            return "editor_error_height_order";
        }
        return null;
    }

    // ---- withers ----------------------------------------------------------------------------------------------

    public Zone withName(final String v) {
        return new Zone(v, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withWorld(final String v) {
        return new Zone(this.name, v, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withEnabled(final boolean v) {
        return new Zone(this.name, this.world, v, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withPriority(final int v) {
        return new Zone(this.name, this.world, this.enabled, v, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withShape(final Shape v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, v, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withMinHeight(final Double v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, v, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withMaxHeight(final Double v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, v, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withActivationMode(final ActivationMode v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, v, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withBoost(final BoostSettings v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, v, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withLaunchStrength(final double v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, v, this.hungerOverride, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withHungerOverride(final HungerSettings v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, v, this.fireworksDisabledOverride, this.boostDisplayOverride);
    }

    public Zone withFireworksDisabledOverride(final Boolean v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, v, this.boostDisplayOverride);
    }

    public Zone withBoostDisplayOverride(final BoostDisplay v) {
        return new Zone(this.name, this.world, this.enabled, this.priority, this.shape, this.minHeight, this.maxHeight, this.activationMode, this.boost, this.launchStrength, this.hungerOverride, this.fireworksDisabledOverride, v);
    }

    // ---- block-rounded compatibility views (API change 1, see CORE-API.md) ------------------------------------------

    /**
     * @deprecated use {@link #minHeight()} (exact double). Block view: {@code ceil(min_y)}, so an integer Y is
     * {@code >= minY()} exactly when it is {@code >= min_y}.
     */
    @Deprecated
    public Integer minY() {
        return this.minHeight == null ? null : (int) Math.ceil(this.minHeight);
    }

    /**
     * @deprecated use {@link #maxHeight()} (exact double). Block view: {@code floor(max_y)}, so an integer Y is
     * {@code <= maxY()} exactly when it is {@code <= max_y}.
     */
    @Deprecated
    public Integer maxY() {
        return this.maxHeight == null ? null : (int) Math.floor(this.maxHeight);
    }

    /** Convenience for whole-block bounds ({@code null} removes the bound). */
    public Zone withMinY(final Integer v) {
        return this.withMinHeight(v == null ? null : v.doubleValue());
    }

    /** Convenience for whole-block bounds ({@code null} removes the bound). */
    public Zone withMaxY(final Integer v) {
        return this.withMaxHeight(v == null ? null : v.doubleValue());
    }
}
