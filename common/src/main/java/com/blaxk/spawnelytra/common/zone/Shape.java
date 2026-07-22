/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Bounds2D;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;

import java.util.List;

/**
 * Horizontal zone geometry. Immutable. Methods that may depend on the live world spawn (a circle with
 * {@code center: world_spawn}) take the world spawn as a parameter; pass the platform's current
 * {@code world.getSpawnLocation()} X/Z unchanged (may be {@code null} if unknown; such a circle then contains nothing).
 */
public sealed interface Shape permits CircleShape, RectangleShape, PolygonShape {

    /** Hard upper limit of polygon points (editor + validation). */
    int MAX_POLYGON_POINTS = 64;

    ShapeType type();

    /** Horizontal containment test (see each shape for the exact rule). */
    boolean contains(double x, double z, Vec2 worldSpawn);

    /** Axis-aligned bounds, or {@code null} if unresolvable (world-spawn circle without spawn). */
    Bounds2D bounds(Vec2 worldSpawn);

    /** Label / pivot position (circle center, bounding-box center otherwise); {@code null} if unresolvable. */
    Vec2 center(Vec2 worldSpawn);

    /** Moves the shape. A world-spawn circle becomes a fixed-center circle. */
    Shape translate(double dx, double dz, Vec2 worldSpawn);

    /** {@code null} if the geometry is valid, otherwise the lang key of the problem ({@code editor_error_*}). */
    String validate();

    /**
     * Closed outline as vertices (the last vertex connects back to the first). Circles are approximated by
     * chords no longer than {@code maxChord} (at least 24 vertices). Empty if unresolvable.
     */
    List<Vec2> outline(Vec2 worldSpawn, double maxChord);

    /** Short human summary of the dimensions, as a lang message ({@code zone_dims_*}). */
    Msg dims();
}
