/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.keys.KeyContext;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.keys.ZoneKeyTable;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.Shape;
import com.blaxk.spawnelytra.common.zone.ShapeOps;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneNames;
import com.blaxk.spawnelytra.common.zone.ZoneOverlaps;
import com.blaxk.spawnelytra.common.zone.ZoneRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's in-world editor session state (spec §6.2): the draft zone, its undo/redo history and the transient
 * tool state (radius step, selected height bound, move pick-up). Not thread-safe: use it from the editing player's
 * (region) thread only. All actions validate and return an {@link EditResult}; failed actions leave the draft
 * unchanged. Every successful geometry/settings change is one undo step.
 *
 * <p>Coordinates from block clicks should be block centers ({@link Vec2#blockCenter(int, int)}); see decisions log.</p>
 */
public final class ZoneDraft {

    /** Height bound selected by the height tool. */
    public enum HeightBound {
        TOP, BOTTOM;

        public String langKey() {
            return this == TOP ? "editor_height_top" : "editor_height_bottom";
        }
    }

    public static final double RADIUS_STEP_SMALL = 1.0;
    public static final double RADIUS_STEP_LARGE = 5.0;
    public static final double GROW_STEP = 1.0;

    private final String originalName;
    private final Zone initial;
    private final EditHistory<Zone> history;
    private Vec2 worldSpawn;

    private double radiusStep = ZoneDraft.RADIUS_STEP_SMALL;
    private HeightBound heightBound = HeightBound.TOP;

    private Zone moveOrigin;
    private Vec2 moveAnchor;
    private Zone moving;

    /**
     * @param zone         the starting zone (a fresh {@link Zone#createDefault} for {@code zone create})
     * @param originalName the saved name being edited, or {@code null} for a new zone
     * @param worldSpawn   current spawn of the zone's world (for world-spawn circles), may be {@code null}
     */
    public ZoneDraft(final Zone zone, final String originalName, final Vec2 worldSpawn) {
        this.initial = zone;
        this.originalName = originalName;
        this.history = new EditHistory<>(zone);
        this.worldSpawn = worldSpawn;
    }

    /** The zone as currently shown (includes an in-progress move). */
    public Zone zone() {
        return this.moving != null ? this.moving : this.history.current();
    }

    /** {@code null} for a draft created with {@code zone create}. */
    public String originalName() {
        return this.originalName;
    }

    public boolean isNew() {
        return this.originalName == null;
    }

    /** {@code true} if the draft differs from the state the editor was opened with. */
    public boolean isDirty() {
        return !this.zone().equals(this.initial);
    }

    public void updateWorldSpawn(final Vec2 spawn) {
        this.worldSpawn = spawn;
    }

    public Vec2 worldSpawn() {
        return this.worldSpawn;
    }

    public EditHistory<Zone> history() {
        return this.history;
    }

    public double radiusStep() {
        return this.radiusStep;
    }

    public HeightBound heightBound() {
        return this.heightBound;
    }

    public boolean isMoving() {
        return this.moving != null;
    }

    private EditResult commit(final Zone next, final Msg feedback) {
        final String problem = next.validate();
        if (problem != null) {
            return EditResult.fail(Msg.of(problem));
        }
        this.history.push(next);
        return EditResult.ok(feedback);
    }

    private EditResult requireNotMoving() {
        return this.moving != null ? EditResult.fail(Msg.of("editor_error_moving")) : null;
    }

    // ---- slot 1: shape tool ---------------------------------------------------------------------------------------

    /** Circle: right-click block = set center. */
    public EditResult setCircleCenter(final Vec2 center) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        if (!(this.zone().shape() instanceof final CircleShape c)) {
            return EditResult.fail("editor_error_wrong_shape");
        }
        return this.commit(this.zone().withShape(c.withCenter(center)),
                Msg.of("editor_center_set", "x", ConfigNumbers.format(center.x()), "z", ConfigNumbers.format(center.z())));
    }

    /** Rectangle: left-click = corner 1, right-click = corner 2. */
    public EditResult setRectangleCorner(final int corner, final Vec2 p) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        if (!(this.zone().shape() instanceof final RectangleShape r)) {
            return EditResult.fail("editor_error_wrong_shape");
        }
        return this.commit(this.zone().withShape(r.withCorner(corner, p)),
                Msg.of("editor_corner_set", "corner", String.valueOf(corner), "x", ConfigNumbers.format(p.x()),
                        "z", ConfigNumbers.format(p.z())));
    }

    /** Polygon: right-click = add a point into the nearest edge. */
    public EditResult addPolygonPoint(final Vec2 p) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        if (!(this.zone().shape() instanceof final PolygonShape poly)) {
            return EditResult.fail("editor_error_wrong_shape");
        }
        if (poly.points().size() >= Shape.MAX_POLYGON_POINTS) {
            return EditResult.fail("editor_error_polygon_too_many", "max", String.valueOf(Shape.MAX_POLYGON_POINTS));
        }
        final PolygonShape next = poly.withPointInserted(p);
        return this.commit(this.zone().withShape(next), Msg.of("editor_point_added", "points", String.valueOf(next.points().size())));
    }

    /** Polygon: left-click = remove the point nearest to the clicked position. */
    public EditResult removePolygonPoint(final Vec2 near) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        if (!(this.zone().shape() instanceof final PolygonShape poly)) {
            return EditResult.fail("editor_error_wrong_shape");
        }
        if (poly.points().size() <= 3) {
            return EditResult.fail("editor_error_polygon_too_few");
        }
        final PolygonShape next = poly.withNearestPointRemoved(near.x(), near.z());
        return this.commit(this.zone().withShape(next), Msg.of("editor_point_removed", "points", String.valueOf(next.points().size())));
    }

    // ---- slot 2: radius / resize --------------------------------------------------------------------------------

    /** Right-click with the resize tool on a circle: toggles the radius step 1 ↔ 5 (no undo step). */
    public EditResult toggleRadiusStep() {
        this.radiusStep = this.radiusStep == ZoneDraft.RADIUS_STEP_SMALL ? ZoneDraft.RADIUS_STEP_LARGE : ZoneDraft.RADIUS_STEP_SMALL;
        return EditResult.ok(Msg.of("editor_radius_step", "step", ConfigNumbers.format(this.radiusStep)));
    }

    /**
     * Sneak+scroll with the resize tool. {@code direction} is +1/-1. Circle: radius ± step (min
     * {@link CircleShape#MIN_RADIUS}); rectangle/polygon: grow/shrink outward by {@link #GROW_STEP}.
     */
    public EditResult scrollResize(final int direction) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        final int dir = Integer.signum(direction);
        if (dir == 0) {
            return EditResult.ok();
        }
        final Shape shape = this.zone().shape();
        return switch (shape) {
            case final CircleShape c -> {
                final double r = c.radius() + dir * this.radiusStep;
                if (r < CircleShape.MIN_RADIUS) {
                    yield EditResult.fail("editor_error_radius");
                }
                yield this.commit(this.zone().withShape(c.withRadius(r)), Msg.of("editor_radius_changed", "radius", ConfigNumbers.format(r)));
            }
            case final RectangleShape rect -> {
                final RectangleShape next = rect.grow(dir * ZoneDraft.GROW_STEP);
                if (next.maxX() - next.minX() < 1 || next.maxZ() - next.minZ() < 1) {
                    yield EditResult.fail("editor_error_too_small");
                }
                yield this.commit(this.zone().withShape(next), Msg.of("editor_resized", "dims", next.dims()));
            }
            case final PolygonShape poly -> {
                final PolygonShape next = poly.grow(dir * ZoneDraft.GROW_STEP);
                if (next == null) {
                    yield EditResult.fail("editor_error_too_small");
                }
                yield this.commit(this.zone().withShape(next), Msg.of("editor_resized", "dims", next.dims()));
            }
        };
    }

    // ---- slot 3: height ---------------------------------------------------------------------------------------------

    /** Right-click with the height tool: toggles the selected bound (no undo step). */
    public EditResult toggleHeightBound() {
        this.heightBound = this.heightBound == HeightBound.TOP ? HeightBound.BOTTOM : HeightBound.TOP;
        return EditResult.ok(Msg.of("editor_height_selected", "bound", Msg.of(this.heightBound.langKey())));
    }

    /**
     * Sneak+scroll with the height tool: moves the selected bound by ±1. If the bound is not set yet, it is first
     * set to {@code playerBlockY} (this first scroll only initialises it).
     */
    public EditResult scrollHeight(final int direction, final int playerBlockY) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        final int dir = Integer.signum(direction);
        final Zone z = this.zone();
        final boolean top = this.heightBound == HeightBound.TOP;
        final Double current = top ? z.maxHeight() : z.minHeight();
        double value;
        if (current == null) {
            value = playerBlockY;
        } else {
            value = current + dir;
        }
        value = Math.max(Zone.MIN_Y_LIMIT, Math.min(Zone.MAX_Y_LIMIT, value));
        final Zone next = top ? z.withMaxHeight(value) : z.withMinHeight(value);
        if (next.minHeight() != null && next.maxHeight() != null && next.minHeight() > next.maxHeight()) {
            return EditResult.fail("editor_error_height_order");
        }
        return this.commit(next, Msg.of("editor_height_changed", "bound", Msg.of(this.heightBound.langKey()),
                "value", ConfigNumbers.format(value)));
    }

    /** Left-click with the height tool: clears both bounds (unlimited). */
    public EditResult clearHeight() {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        if (!this.zone().hasHeightLimit()) {
            return EditResult.ok(Msg.of("editor_height_cleared"));
        }
        return this.commit(this.zone().withMinHeight(null).withMaxHeight(null), Msg.of("editor_height_cleared"));
    }

    // ---- slot 4: move -------------------------------------------------------------------------------------------------

    /** First right-click with the move tool: picks the zone up, anchored at the looked-at block. */
    public EditResult pickUp(final Vec2 anchor) {
        if (this.moving != null) {
            return this.place();
        }
        if (this.zone().shape().bounds(this.worldSpawn) == null) {
            return EditResult.fail("editor_error_spawn_unknown");
        }
        this.moveOrigin = this.history.current();
        this.moveAnchor = anchor;
        this.moving = this.moveOrigin;
        return EditResult.ok(Msg.of("editor_move_picked"));
    }

    /**
     * While moving: the zone follows the looked-at block. The offset is snapped to whole blocks.
     * Returns {@code true} if the preview changed (refresh it). No undo step.
     */
    public boolean moveTo(final Vec2 target) {
        if (this.moving == null) {
            return false;
        }
        final double dx = Math.round(target.x() - this.moveAnchor.x());
        final double dz = Math.round(target.z() - this.moveAnchor.z());
        final Zone next = this.moveOrigin.withShape(this.moveOrigin.shape().translate(dx, dz, this.worldSpawn));
        if (next.equals(this.moving)) {
            return false;
        }
        this.moving = next;
        return true;
    }

    /** Second right-click: places the zone (one undo step). */
    public EditResult place() {
        if (this.moving == null) {
            return EditResult.fail("editor_error_not_moving");
        }
        final Zone placed = this.moving;
        this.moving = null;
        this.moveOrigin = null;
        this.moveAnchor = null;
        this.history.push(placed);
        return EditResult.ok(Msg.of("editor_move_placed"));
    }

    /** Aborts a move (tool switched away, editor closed). */
    public void cancelMove() {
        this.moving = null;
        this.moveOrigin = null;
        this.moveAnchor = null;
    }

    // ---- slot 5: shape switch ---------------------------------------------------------------------------------------

    /** Cycles circle → rectangle → polygon → circle using {@link ShapeOps#convert}. */
    public EditResult cycleShape() {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        final Shape current = this.zone().shape();
        final Shape next = ShapeOps.convert(current, current.type().next(), this.worldSpawn);
        if (next == null) {
            return EditResult.fail("editor_error_spawn_unknown");
        }
        return this.commit(this.zone().withShape(next), Msg.of("editor_shape_changed", "shape", Msg.of(next.type().langKey())));
    }

    // ---- slot 6: undo / redo ------------------------------------------------------------------------------------

    public EditResult undo() {
        this.cancelMove();
        return this.history.undo() == null ? EditResult.fail("editor_nothing_to_undo")
                : EditResult.ok(Msg.of("editor_undone", "remaining", String.valueOf(this.history.undoSize())));
    }

    public EditResult redo() {
        this.cancelMove();
        return this.history.redo() == null ? EditResult.fail("editor_nothing_to_redo")
                : EditResult.ok(Msg.of("editor_redone", "remaining", String.valueOf(this.history.redoSize())));
    }

    // ---- slot 8: settings + /se zone set --------------------------------------------------------------------------

    /** Applies a zone-set key to the draft (settings menu opened from the editor, or {@code /se zone set}). */
    public EditResult applyKey(final String key, final String value, final KeyContext ctx) {
        final EditResult busy = this.requireNotMoving();
        if (busy != null) {
            return busy;
        }
        final SetResult<Zone> r = ZoneKeyTable.apply(this.zone(), key, value, ctx);
        if (!r.ok()) {
            return EditResult.fail(r.error());
        }
        return this.commit(r.value(), Msg.of("zone_set_success", "zone", r.value().name(), "key", key,
                "value", ZoneKeyTable.currentValue(r.value(), key)));
    }

    /** Replaces the whole draft (e.g. a dialog submit that changed several fields at once): one undo step. */
    public EditResult apply(final Zone updated) {
        this.cancelMove();
        return this.commit(updated, null);
    }

    // ---- slot 9: save -----------------------------------------------------------------------------------------------

    /** Validation outcome for saving: blocking errors and non-blocking warnings (overlaps). */
    public record SaveCheck(List<Msg> errors, List<Msg> warnings) {
        public boolean ok() {
            return this.errors.isEmpty();
        }
    }

    /**
     * Save validation (spec §6.3): name valid and unique (the original name may be kept), world exists, geometry
     * valid. Overlaps with other zones are warnings ({@code editor_save_overlap} with both priorities).
     */
    public SaveCheck checkSave(final ZoneRegistry registry, final KeyContext ctx) {
        final List<Msg> errors = new ArrayList<>();
        final List<Msg> warnings = new ArrayList<>();
        final Zone z = this.zone();
        if (this.moving != null) {
            errors.add(Msg.of("editor_error_moving"));
        }
        final String nameProblem = ZoneNames.check(z.name(), registry.names(), this.originalName);
        if (nameProblem != null) {
            errors.add(Msg.of(nameProblem, "name", z.name()));
        }
        if (!ctx.worldExists(z.world())) {
            errors.add(Msg.of("zone_error_world_unknown", "world", z.world()));
        }
        final String problem = z.validate();
        if (problem != null && nameProblem == null) {
            errors.add(Msg.of(problem));
        }
        final ZoneRegistry others = this.originalName == null ? registry : registry.without(this.originalName);
        for (final Zone o : ZoneOverlaps.overlapping(z, others, w -> this.worldSpawn)) {
            warnings.add(Msg.of("editor_save_overlap", "zone", z.name(), "priority", String.valueOf(z.priority()),
                    "other", o.name(), "other_priority", String.valueOf(o.priority())));
        }
        return new SaveCheck(errors, warnings);
    }

    // ---- HUD ----------------------------------------------------------------------------------------------------------

    /** Bossbar title while editing: zone name, held tool, key dims ({@code editor_bossbar}). */
    public Msg bossbarTitle(final EditorTool held) {
        final Zone z = this.zone();
        return Msg.of("editor_bossbar", "zone", z.name(),
                "tool", Msg.of(held == null ? "editor_tool_none" : held.nameKey(z.shape().type())),
                "shape", Msg.of(z.shape().type().langKey()), "dims", z.shape().dims());
    }

    /** Actionbar hint for the held tool (adds the radius step / selected bound where relevant). */
    public Msg actionbarHint(final EditorTool held) {
        final Zone z = this.zone();
        if (held == null) {
            return Msg.of("editor_hint_none");
        }
        if (this.moving != null && held == EditorTool.MOVE) {
            return Msg.of("editor_hint_move_active");
        }
        return Msg.of(held.hintKey(z.shape().type()), "step", ConfigNumbers.format(this.radiusStep),
                "bound", Msg.of(this.heightBound.langKey()));
    }
}
