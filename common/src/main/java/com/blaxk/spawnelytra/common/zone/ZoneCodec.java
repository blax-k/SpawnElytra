/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the {@code zones:} section (spec §1.1) through {@link ConfigView}. Writing keeps a canonical key
 * order, preserves existing comments of every key it rewrites, and keeps unknown user keys.
 */
public enum ZoneCodec {
    ;

    public static final String ZONES = "zones";
    public static final String WORLD_SPAWN = "world_spawn";

    /** Keys owned by the codec, in canonical write order. */
    public static final List<String> KNOWN_KEYS = List.of("world", "enabled", "priority", "shape", "center", "radius",
            "corner1", "corner2", "points", "min_y", "max_y", "activation_mode", "boost", "f_key", "boost_display",
            "fireworks", "hunger_consumption");

    /** Result of reading all zones: valid zones plus warnings for skipped/repaired entries. */
    public record ReadResult(List<Zone> zones, List<Msg> warnings) {
    }

    // ---- reading ----------------------------------------------------------------------------------------------

    /**
     * Reads every zone of the root's {@code zones} section. Invalid entries (bad name, duplicate name, missing
     * world, invalid geometry) are skipped with a warning ({@code zone_load_*} lang keys).
     *
     * @param globalHunger the global hunger settings (fallback for keys missing in a per-zone override)
     */
    public static ReadResult readAll(final ConfigView root, final HungerSettings globalHunger) {
        final List<Zone> zones = new ArrayList<>();
        final List<Msg> warnings = new ArrayList<>();
        final ConfigView section = root.section(ZoneCodec.ZONES);
        if (section == null) {
            return new ReadResult(zones, warnings);
        }
        final Set<String> seen = new java.util.HashSet<>();
        for (final String key : section.keys()) {
            final String name = ZoneNames.normalize(key);
            final ConfigView zs = section.section(key);
            if (zs == null) {
                warnings.add(Msg.of("zone_load_not_section", "zone", key));
                continue;
            }
            if (!ZoneNames.isValid(name)) {
                warnings.add(Msg.of("zone_load_invalid_name", "zone", key));
                continue;
            }
            if (!seen.add(name)) {
                warnings.add(Msg.of("zone_load_duplicate", "zone", key));
                continue;
            }
            final Zone zone = ZoneCodec.read(name, zs, globalHunger);
            if (zone.world() == null || zone.world().isBlank()) {
                warnings.add(Msg.of("zone_load_no_world", "zone", name));
                continue;
            }
            final String problem = zone.validate();
            if (problem != null) {
                warnings.add(Msg.of("zone_load_invalid", "zone", name, "reason", Msg.of(problem)));
                continue;
            }
            zones.add(zone);
        }
        return new ReadResult(zones, warnings);
    }

    /** Reads one zone section (no validation; see {@link Zone#validate()}). */
    public static Zone read(final String name, final ConfigView s, final HungerSettings globalHunger) {
        final Shape shape = ZoneCodec.readShape(s);
        final ConfigView hunger = s.section("hunger_consumption");
        final Object fireworks = s.get("fireworks.disable_in_spawn_elytra");
        final String display = s.getString("boost_display", null);
        return new Zone(
                ZoneNames.normalize(name),
                s.getString("world", null),
                s.getBoolean("enabled", true),
                s.getInt("priority", 0),
                shape,
                s.getDoubleOrNull("min_y"),
                s.getDoubleOrNull("max_y"),
                ActivationMode.fromIdOrDefault(s.getString("activation_mode", "double_jump")),
                BoostSettings.read(s.section("boost")),
                s.getDouble("f_key.launch_strength", Zone.DEFAULT_LAUNCH_STRENGTH),
                hunger == null ? null : HungerSettings.read(hunger, globalHunger == null ? HungerSettings.DEFAULTS : globalHunger),
                fireworks instanceof final Boolean b ? b : null,
                display == null ? null : BoostDisplay.fromId(display));
    }

    /** Reads the shape keys; missing {@code shape} is inferred from the keys present. */
    public static Shape readShape(final ConfigView s) {
        ShapeType type = ShapeType.fromId(s.getString("shape", null));
        if (type == null) {
            type = s.contains("points") ? ShapeType.POLYGON : (s.contains("corner1") ? ShapeType.RECTANGLE : ShapeType.CIRCLE);
        }
        return switch (type) {
            case CIRCLE -> {
                final Object center = s.get("center");
                Vec2 c = null;
                if (center instanceof final ConfigView cv) {
                    c = new Vec2(cv.getDouble("x", 0), cv.getDouble("z", 0));
                } else if (center instanceof final String str && !ZoneCodec.WORLD_SPAWN.equalsIgnoreCase(str.trim())) {
                    c = ZoneCodec.parsePoint(str);
                }
                yield new CircleShape(c, s.getDouble("radius", 100));
            }
            case RECTANGLE -> new RectangleShape(ZoneCodec.readPoint(s.get("corner1")), ZoneCodec.readPoint(s.get("corner2")));
            case POLYGON -> {
                final List<Vec2> pts = new ArrayList<>();
                for (final Object o : s.getList("points")) {
                    final Vec2 p = ZoneCodec.readPoint(o);
                    if (p != null) {
                        pts.add(p);
                    }
                }
                yield new PolygonShape(pts);
            }
        };
    }

    /** Reads {x, z} from a section, a map or an {@code "x,z"} string; missing → (0, 0). */
    private static Vec2 readPoint(final Object o) {
        if (o instanceof final ConfigView cv) {
            return new Vec2(cv.getDouble("x", 0), cv.getDouble("z", 0));
        }
        if (o instanceof final Map<?, ?> m) {
            return new Vec2(m.get("x") instanceof final Number x ? x.doubleValue() : 0,
                    m.get("z") instanceof final Number z ? z.doubleValue() : 0);
        }
        if (o instanceof final String str) {
            final Vec2 p = ZoneCodec.parsePoint(str);
            return p == null ? new Vec2(0, 0) : p;
        }
        return new Vec2(0, 0);
    }

    /** Parses {@code "x,z"} / {@code "x z"}; {@code null} if malformed. */
    public static Vec2 parsePoint(final String str) {
        if (str == null) {
            return null;
        }
        final String[] parts = str.trim().split("\\s*[, ]\\s*");
        if (parts.length != 2) {
            return null;
        }
        try {
            final double x = Double.parseDouble(parts[0]);
            final double z = Double.parseDouble(parts[1]);
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                return null;
            }
            return new Vec2(x, z);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // ---- writing ----------------------------------------------------------------------------------------------

    /** Finds the existing key of a zone case-insensitively in the {@code zones} section, or {@code null}. */
    public static String findKey(final ConfigView zones, final String name) {
        if (zones == null || name == null) {
            return null;
        }
        for (final String k : zones.keys()) {
            if (k.equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Writes a zone into {@code root.zones}, replacing the section of {@code previousName} (or of the zone's own name
     * when {@code previousName} is {@code null}). Existing comments of rewritten keys and unknown user keys are kept.
     * A renamed zone is moved to the end of the section. With {@code defaultComments}, keys without a comment get the
     * shipped explanatory comments ({@link ZoneComments}).
     */
    public static void write(final ConfigView root, final Zone zone, final String previousName, final boolean defaultComments) {
        ConfigView zones = root.section(ZoneCodec.ZONES);
        if (zones == null) {
            zones = root.createSection(ZoneCodec.ZONES);
        }
        final String existingKey = ZoneCodec.findKey(zones, previousName != null ? previousName : zone.name());
        final Map<String, List<String>> comments = new LinkedHashMap<>();
        final Map<String, Object> extras = new LinkedHashMap<>();
        List<String> zoneComment = List.of();
        if (existingKey != null) {
            zoneComment = zones.comments(existingKey);
            final ConfigView old = zones.section(existingKey);
            if (old != null) {
                ZoneCodec.collectComments(old, "", comments);
                for (final String k : old.keys()) {
                    if (!ZoneCodec.KNOWN_KEYS.contains(k)) {
                        extras.put(k, old.get(k));
                    }
                }
            }
            if (!existingKey.equals(zone.name())) {
                zones.set(existingKey, null);
            }
        }
        final ConfigView s = zones.createSection(zone.name());
        ZoneCodec.writeValues(s, zone);
        for (final Map.Entry<String, Object> e : extras.entrySet()) {
            if (e.getValue() instanceof final ConfigView cv) {
                s.createSection(e.getKey());
                s.copyFrom(cv, e.getKey());
            } else {
                s.set(e.getKey(), e.getValue());
            }
        }
        if (!zoneComment.isEmpty()) {
            zones.setComments(zone.name(), zoneComment);
        }
        for (final Map.Entry<String, List<String>> e : comments.entrySet()) {
            if (s.get(e.getKey()) != null) {
                s.setComments(e.getKey(), e.getValue());
            }
        }
        if (defaultComments) {
            for (final Map.Entry<String, List<String>> e : ZoneComments.DEFAULTS.entrySet()) {
                if (s.get(e.getKey()) != null && s.comments(e.getKey()).isEmpty()) {
                    s.setComments(e.getKey(), e.getValue());
                }
            }
        }
    }

    /** Removes a zone (case-insensitive). Returns {@code true} if something was removed. */
    public static boolean remove(final ConfigView root, final String name) {
        final ConfigView zones = root.section(ZoneCodec.ZONES);
        final String key = ZoneCodec.findKey(zones, name);
        if (key == null) {
            return false;
        }
        zones.set(key, null);
        return true;
    }

    private static void collectComments(final ConfigView v, final String prefix, final Map<String, List<String>> out) {
        for (final String k : v.keys()) {
            final String path = prefix.isEmpty() ? k : prefix + "." + k;
            final List<String> c = v.comments(k);
            if (!c.isEmpty()) {
                out.put(path, new ArrayList<>(c));
            }
            if (v.get(k) instanceof final ConfigView sub) {
                ZoneCodec.collectComments(sub, path, out);
            }
        }
    }

    /** Writes the zone's values in canonical order into an empty section. */
    private static void writeValues(final ConfigView s, final Zone zone) {
        s.set("world", zone.world());
        s.set("enabled", zone.enabled());
        s.set("priority", zone.priority());
        s.set("shape", zone.shape().type().id());
        switch (zone.shape()) {
            case final CircleShape c -> {
                if (c.followsWorldSpawn()) {
                    s.set("center", ZoneCodec.WORLD_SPAWN);
                } else {
                    s.createSection("center");
                    s.set("center.x", ConfigNumbers.canonical(c.center().x()));
                    s.set("center.z", ConfigNumbers.canonical(c.center().z()));
                }
                s.set("radius", ConfigNumbers.canonical(c.radius()));
            }
            case final RectangleShape r -> {
                s.createSection("corner1");
                s.set("corner1.x", ConfigNumbers.canonical(r.corner1().x()));
                s.set("corner1.z", ConfigNumbers.canonical(r.corner1().z()));
                s.createSection("corner2");
                s.set("corner2.x", ConfigNumbers.canonical(r.corner2().x()));
                s.set("corner2.z", ConfigNumbers.canonical(r.corner2().z()));
            }
            case final PolygonShape p -> s.set("points", ZoneCodec.pointList(p.points()));
        }
        if (zone.minHeight() != null) {
            s.set("min_y", ConfigNumbers.canonical(zone.minHeight()));
        }
        if (zone.maxHeight() != null) {
            s.set("max_y", ConfigNumbers.canonical(zone.maxHeight()));
        }
        s.set("activation_mode", zone.activationMode().id());
        s.createSection("boost");
        zone.boost().write(s.section("boost"));
        s.createSection("f_key");
        s.set("f_key.launch_strength", ConfigNumbers.canonical(zone.launchStrength()));
        if (zone.boostDisplayOverride() != null) {
            s.set("boost_display", zone.boostDisplayOverride().id());
        }
        if (zone.fireworksDisabledOverride() != null) {
            s.createSection("fireworks");
            s.set("fireworks.disable_in_spawn_elytra", zone.fireworksDisabledOverride());
        }
        if (zone.hungerOverride() != null) {
            zone.hungerOverride().write(s.createSection("hunger_consumption"));
        }
    }

    /** Polygon points as a YAML list of {@code {x, z}} maps (canonical numbers). */
    public static List<Map<String, Object>> pointList(final List<Vec2> points) {
        final List<Map<String, Object>> list = new ArrayList<>(points.size());
        for (final Vec2 p : points) {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", ConfigNumbers.canonical(p.x()));
            m.put("z", ConfigNumbers.canonical(p.z()));
            list.add(m);
        }
        return list;
    }

    /** {@code "x,z"} formatting for commands and menus. */
    public static String formatPoint(final Vec2 p) {
        return ConfigNumbers.format(p.x()) + "," + ConfigNumbers.format(p.z());
    }
}
