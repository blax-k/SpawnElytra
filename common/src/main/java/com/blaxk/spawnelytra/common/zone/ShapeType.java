/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import java.util.Locale;

/** Zone shape kinds; {@link #id()} is the config/command value. */
public enum ShapeType {
    CIRCLE("circle"),
    RECTANGLE("rectangle"),
    POLYGON("polygon");

    private final String id;

    ShapeType(final String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** Lang key of the display name ({@code zone_shape_circle}, ...). */
    public String langKey() {
        return "zone_shape_" + this.id;
    }

    /** The next shape in the editor's cycle circle → rectangle → polygon → circle. */
    public ShapeType next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    /** Parses {@code circle|rectangle|polygon} (case-insensitive); {@code null} if unknown. */
    public static ShapeType fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String s = id.trim().toLowerCase(Locale.ROOT);
        for (final ShapeType t : values()) {
            if (t.id.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
