/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.editor;

import com.blaxk.spawnelytra.common.zone.ShapeType;

/**
 * The editor hotbar (spec §6.2). {@link #slot()} is 0-based (hotbar slot 1 = index 0). Lang keys:
 * {@code editor_tool_<id>_name}, {@code editor_tool_<id>_lore} (one line, may contain {@code <br>}),
 * {@code editor_hint_<id>} (actionbar hint while held). The shape tool uses per-shape keys
 * ({@code editor_tool_shape_circle_name}, ...), see {@link #nameKey(ShapeType)}.
 */
public enum EditorTool {
    SHAPE("shape"),
    RESIZE("resize"),
    HEIGHT("height"),
    MOVE("move"),
    SHAPE_SWITCH("shape_switch"),
    UNDO_REDO("undo_redo"),
    CANCEL("cancel"),
    SETTINGS("settings"),
    SAVE("save");

    private final String id;

    EditorTool(final String id) {
        this.id = id;
    }

    /** Stable id, also stored in the item's PDC / custom data ({@code spawnelytra:editor_tool = <id>}). */
    public String id() {
        return this.id;
    }

    /** 0-based hotbar slot. */
    public int slot() {
        return this.ordinal();
    }

    /** Item display name key; tools 1–3 depend on the shape. */
    public String nameKey(final ShapeType shape) {
        return switch (this) {
            case SHAPE, RESIZE -> "editor_tool_" + this.id + "_" + shape.id() + "_name";
            default -> "editor_tool_" + this.id + "_name";
        };
    }

    /** Item lore key; tools 1–2 depend on the shape. */
    public String loreKey(final ShapeType shape) {
        return switch (this) {
            case SHAPE, RESIZE -> "editor_tool_" + this.id + "_" + shape.id() + "_lore";
            default -> "editor_tool_" + this.id + "_lore";
        };
    }

    /** Actionbar hint key while the tool is held; tools 1–2 depend on the shape. */
    public String hintKey(final ShapeType shape) {
        return switch (this) {
            case SHAPE, RESIZE -> "editor_hint_" + this.id + "_" + shape.id();
            default -> "editor_hint_" + this.id;
        };
    }

    /** Suggested vanilla item id per tool (platforms may choose others). */
    public String suggestedItem() {
        return switch (this) {
            case SHAPE -> "minecraft:blaze_rod";
            case RESIZE -> "minecraft:slime_ball";
            case HEIGHT -> "minecraft:ladder";
            case MOVE -> "minecraft:piston";
            case SHAPE_SWITCH -> "minecraft:amethyst_shard";
            case UNDO_REDO -> "minecraft:clock";
            case CANCEL -> "minecraft:barrier";
            case SETTINGS -> "minecraft:comparator";
            case SAVE -> "minecraft:lime_dye";
        };
    }

    public static EditorTool bySlot(final int slot) {
        return slot >= 0 && slot < values().length ? values()[slot] : null;
    }

    public static EditorTool byId(final String id) {
        for (final EditorTool t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
