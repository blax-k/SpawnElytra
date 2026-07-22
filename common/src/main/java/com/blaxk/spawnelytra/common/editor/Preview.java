/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor preview model (spec §6.2 "Feedback"): straight line segments with a style, plus the floating label.
 * Platforms render each segment as one stretched block display (or a row of text displays), visible only to the
 * editing player, and diff the list between refreshes (segments are value objects, use {@code equals}).
 */
public final class Preview {

    /** Segment styles; suggested colors in parentheses. */
    public enum Style {
        /** The draft's outline (bright green / lime glass). */
        DRAFT,
        /** Part of the draft's outline inside another zone (red). */
        DRAFT_OVERLAP,
        /** Another zone in the same world (dim gray). */
        OTHER,
        /** Part of another zone's outline inside the draft (red). */
        OTHER_OVERLAP,
        /** Draft's {@code max_y} ring (light blue). */
        HEIGHT_TOP,
        /** Draft's {@code min_y} ring (orange). */
        HEIGHT_BOTTOM
    }

    /** One straight segment at height {@code y} (block Y the line is drawn at). */
    public record Segment(Vec2 from, Vec2 to, double y, Style style) {
        public double length() {
            return this.from.distance(this.to);
        }
    }

    /**
     * Rendering limits.
     *
     * @param range            only segments whose midpoint is within this XZ distance of the player (default 64)
     * @param maxSegmentLength edges are split into pieces of at most this length before classification (default 4)
     * @param maxSegments      hard cap of returned segments, nearest first (default 256)
     */
    public record Options(double range, double maxSegmentLength, int maxSegments) {
        public static final Options DEFAULT = new Options(64, 4, 256);
    }

    /** The floating label: position (XZ + y) and text ({@code editor_label}). */
    public record Label(Vec2 position, double y, Msg text) {
    }

    private final List<Segment> segments;
    private final Label label;

    public Preview(final List<Segment> segments, final Label label) {
        this.segments = List.copyOf(segments);
        this.label = label;
    }

    public List<Segment> segments() {
        return this.segments;
    }

    /** {@code null} if the label position is unresolvable or out of range. */
    public Label label() {
        return this.label;
    }

    /**
     * Builds the preview for a draft.
     *
     * @param draft      the draft zone
     * @param others     other zones in the same world (the draft's saved version excluded)
     * @param worldSpawn current world spawn (for world-spawn circles), may be {@code null}
     * @param px         player X
     * @param py         player Y (the outline is drawn at the player's block Y, clamped into the height bounds)
     * @param pz         player Z
     */
    public static Preview build(final Zone draft, final List<Zone> others, final Vec2 worldSpawn,
                                final double px, final double py, final double pz, final Options options) {
        final Options opt = options == null ? Options.DEFAULT : options;
        final Vec2 player = new Vec2(px, pz);
        double y = Math.floor(py);
        if (draft.minHeight() != null) {
            y = Math.max(y, draft.minHeight());
        }
        if (draft.maxHeight() != null) {
            y = Math.min(y, draft.maxHeight());
        }
        final List<Segment> out = new ArrayList<>();
        final List<Vec2> draftOutline = draft.shape().outline(worldSpawn, opt.maxSegmentLength());
        final double lineY = y;
        Preview.addOutline(out, draftOutline, lineY, player, opt, mid -> {
            for (final Zone o : others) {
                if (o.containsY(lineY) && o.shape().contains(mid.x(), mid.z(), worldSpawn)) {
                    return Style.DRAFT_OVERLAP;
                }
            }
            return Style.DRAFT;
        });
        if (draft.maxHeight() != null) {
            Preview.addOutline(out, draftOutline, draft.maxHeight(), player, opt, mid -> Style.HEIGHT_TOP);
        }
        if (draft.minHeight() != null) {
            Preview.addOutline(out, draftOutline, draft.minHeight(), player, opt, mid -> Style.HEIGHT_BOTTOM);
        }
        for (final Zone o : others) {
            final double oy = Math.max(o.minHeight() == null ? lineY : o.minHeight(), Math.min(o.maxHeight() == null ? lineY : o.maxHeight(), lineY));
            Preview.addOutline(out, o.shape().outline(worldSpawn, opt.maxSegmentLength()), oy, player, opt,
                    mid -> draft.containsY(oy) && draft.shape().contains(mid.x(), mid.z(), worldSpawn) ? Style.OTHER_OVERLAP : Style.OTHER);
        }
        if (out.size() > opt.maxSegments()) {
            out.sort((a, b) -> Double.compare(Preview.mid(a).distanceSquared(player), Preview.mid(b).distanceSquared(player)));
            out.subList(opt.maxSegments(), out.size()).clear();
        }
        Label label = null;
        final Vec2 c = draft.shape().center(worldSpawn);
        if (c != null && c.distance(player) <= opt.range() * 2) {
            label = new Label(c, y + 2, Preview.labelText(draft));
        }
        return new Preview(out, label);
    }

    /** {@code editor_label}: {@code name · shape dims · activation mode · priority}. */
    public static Msg labelText(final Zone zone) {
        return Msg.of("editor_label", "zone", zone.name(), "shape", Msg.of(zone.shape().type().langKey()),
                "dims", zone.shape().dims(), "mode", Msg.of(zone.activationMode().langKey()),
                "priority", String.valueOf(zone.priority()));
    }

    private static Vec2 mid(final Segment s) {
        return new Vec2((s.from().x() + s.to().x()) / 2, (s.from().z() + s.to().z()) / 2);
    }

    private interface Classifier {
        Style classify(Vec2 mid);
    }

    private static void addOutline(final List<Segment> out, final List<Vec2> outline, final double y, final Vec2 player,
                                   final Options opt, final Classifier classifier) {
        final int n = outline.size();
        if (n < 2) {
            return;
        }
        for (int i = 0; i < n; i++) {
            final Vec2 a = outline.get(i);
            final Vec2 b = outline.get((i + 1) % n);
            final double len = a.distance(b);
            final int pieces = Math.max(1, (int) Math.ceil(len / Math.max(0.5, opt.maxSegmentLength())));
            Vec2 runStart = null;
            Vec2 runEnd = null;
            Style runStyle = null;
            for (int k = 0; k < pieces; k++) {
                final Vec2 p0 = new Vec2(a.x() + (b.x() - a.x()) * k / pieces, a.z() + (b.z() - a.z()) * k / pieces);
                final Vec2 p1 = new Vec2(a.x() + (b.x() - a.x()) * (k + 1) / pieces, a.z() + (b.z() - a.z()) * (k + 1) / pieces);
                final Vec2 m = new Vec2((p0.x() + p1.x()) / 2, (p0.z() + p1.z()) / 2);
                final boolean inRange = m.distance(player) <= opt.range();
                final Style style = inRange ? classifier.classify(m) : null;
                if (style != null && style == runStyle) {
                    runEnd = p1;
                    continue;
                }
                if (runStyle != null) {
                    out.add(new Segment(runStart, runEnd, y, runStyle));
                }
                runStart = style == null ? null : p0;
                runEnd = style == null ? null : p1;
                runStyle = style;
            }
            if (runStyle != null) {
                out.add(new Segment(runStart, runEnd, y, runStyle));
            }
        }
    }
}
