/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.keys;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.PolygonShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.Shape;
import com.blaxk.spawnelytra.common.zone.ShapeOps;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import com.blaxk.spawnelytra.common.zone.ZoneNames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The {@code /se zone set <zone> <key> <value>} key table (spec §6.1): every zone field, dotted keys.
 * Also used by the menus (field ids are these keys) and the dialog submit handler.
 *
 * <p>Special values: booleans accept {@code toggle}; options accept {@code next}; optional values
 * ({@code min_y}, {@code max_y}, {@code boost_display}, {@code fireworks...}, {@code hunger_consumption}) accept
 * {@code inherit}/{@code none} to remove the override/limit. Setting any {@code hunger_consumption.*} number while
 * the zone inherits creates an override seeded from the global hunger settings.</p>
 */
public enum ZoneKeyTable {
    ;

    public static final String HUNGER_INHERIT = "inherit";
    public static final String HUNGER_OFF = "off";

    private static final List<String> HUNGER_OPTIONS = List.of("inherit", "off", "activation", "distance", "time");
    private static final List<String> DISPLAY_OPTIONS = List.of("inherit", "actionbar", "bossbar", "none");
    private static final List<String> FIREWORKS_OPTIONS = List.of("inherit", "true", "false");

    private record Entry(KeySpec spec, Function<Zone, String> getter, Setter setter) {
    }

    @FunctionalInterface
    private interface Setter {
        SetResult<Zone> apply(Zone zone, String raw, KeyContext ctx, KeySpec spec);
    }

    private static final Map<String, Entry> TABLE = new LinkedHashMap<>();

    private static void put(final KeySpec spec, final Function<Zone, String> getter, final Setter setter) {
        ZoneKeyTable.TABLE.put(spec.key(), new Entry(spec, getter, setter));
    }

    private static Setter number(final BiFunction<Zone, Double, Zone> f) {
        return (zone, raw, ctx, spec) -> {
            final SetResult<Double> r = KeyParsers.number(spec, raw);
            return r.ok() ? SetResult.ok(f.apply(zone, r.value())) : SetResult.fail(r.error());
        };
    }

    private static Setter bool(final Function<Zone, Boolean> current, final BiFunction<Zone, Boolean, Zone> f) {
        return (zone, raw, ctx, spec) -> {
            final SetResult<Boolean> r = KeyParsers.bool(spec, raw, current.apply(zone));
            return r.ok() ? SetResult.ok(f.apply(zone, r.value())) : SetResult.fail(r.error());
        };
    }

    private static HungerSettings hunger(final Zone zone, final KeyContext ctx) {
        return zone.hungerOverride() != null ? zone.hungerOverride() : ctx.globalHunger();
    }

    private static Setter hungerNumber(final BiFunction<HungerSettings, Double, HungerSettings> f) {
        return (zone, raw, ctx, spec) -> {
            final SetResult<Double> r = KeyParsers.number(spec, raw);
            return r.ok() ? SetResult.ok(zone.withHungerOverride(f.apply(ZoneKeyTable.hunger(zone, ctx), r.value())))
                    : SetResult.fail(r.error());
        };
    }

    private static String hungerValue(final Zone zone, final Function<HungerSettings, Object> f) {
        return zone.hungerOverride() == null ? ZoneKeyTable.HUNGER_INHERIT : ZoneKeyTable.fmt(f.apply(zone.hungerOverride()));
    }

    private static String fmt(final Object o) {
        return o instanceof final Double d ? ConfigNumbers.format(d) : String.valueOf(o);
    }

    private static SetResult<Zone> wrongShape(final KeySpec spec) {
        return SetResult.fail("zone_set_wrong_shape", "key", spec.key());
    }

    static {
        ZoneKeyTable.put(KeySpec.of("name", KeyType.TEXT), Zone::name, (zone, raw, ctx, spec) -> {
            final String name = ZoneNames.normalize(raw);
            if (!ZoneNames.isValid(name)) {
                return SetResult.fail("zone_error_invalid_name", "name", String.valueOf(raw));
            }
            if (!name.equals(zone.name()) && ctx.zoneNameTaken(name)) {
                return SetResult.fail("zone_error_name_taken", "name", name);
            }
            return SetResult.ok(zone.withName(name));
        });
        ZoneKeyTable.put(KeySpec.of("world", KeyType.TEXT), Zone::world, (zone, raw, ctx, spec) -> {
            final String w = raw == null ? "" : raw.trim();
            if (w.isEmpty() || !ctx.worldExists(w)) {
                return SetResult.fail("zone_error_world_unknown", "world", w);
            }
            return SetResult.ok(zone.withWorld(w));
        });
        ZoneKeyTable.put(KeySpec.of("enabled", KeyType.BOOLEAN), z -> String.valueOf(z.enabled()),
                ZoneKeyTable.bool(Zone::enabled, Zone::withEnabled));
        ZoneKeyTable.put(KeySpec.number("priority", KeyType.INT, -1000, 1000, 1, 10), z -> String.valueOf(z.priority()),
                ZoneKeyTable.number((z, v) -> z.withPriority(v.intValue())));
        ZoneKeyTable.put(KeySpec.option("shape", List.of("circle", "rectangle", "polygon")), z -> z.shape().type().id(),
                (zone, raw, ctx, spec) -> {
                    final SetResult<String> r = KeyParsers.option(spec, raw, zone.shape().type().id());
                    if (!r.ok()) {
                        return SetResult.fail(r.error());
                    }
                    final Shape converted = ShapeOps.convert(zone.shape(), ShapeType.fromId(r.value()), ctx.worldSpawn(zone.world()));
                    return converted == null ? SetResult.fail("editor_error_spawn_unknown") : SetResult.ok(zone.withShape(converted));
                });
        ZoneKeyTable.put(KeySpec.of("center", KeyType.CENTER), z -> z.shape() instanceof final CircleShape c
                        ? (c.followsWorldSpawn() ? ZoneCodec.WORLD_SPAWN : ZoneCodec.formatPoint(c.center())) : "",
                (zone, raw, ctx, spec) -> {
                    if (!(zone.shape() instanceof final CircleShape c)) {
                        return ZoneKeyTable.wrongShape(spec);
                    }
                    if (raw != null && ZoneCodec.WORLD_SPAWN.equalsIgnoreCase(raw.trim())) {
                        return SetResult.ok(zone.withShape(c.withCenter(null)));
                    }
                    final Vec2 p = ZoneCodec.parsePoint(raw);
                    return p == null ? SetResult.fail("zone_set_invalid_point", "key", spec.key(), "value", String.valueOf(raw))
                            : SetResult.ok(zone.withShape(c.withCenter(p)));
                });
        ZoneKeyTable.put(KeySpec.number("radius", KeyType.DOUBLE, CircleShape.MIN_RADIUS, CircleShape.MAX_RADIUS, 1, 10),
                z -> z.shape() instanceof final CircleShape c ? ConfigNumbers.format(c.radius()) : "",
                (zone, raw, ctx, spec) -> {
                    if (!(zone.shape() instanceof final CircleShape c)) {
                        return ZoneKeyTable.wrongShape(spec);
                    }
                    final SetResult<Double> r = KeyParsers.number(spec, raw);
                    return r.ok() ? SetResult.ok(zone.withShape(c.withRadius(r.value()))) : SetResult.fail(r.error());
                });
        for (final int corner : new int[]{1, 2}) {
            ZoneKeyTable.put(KeySpec.of("corner" + corner, KeyType.POINT),
                    z -> z.shape() instanceof final RectangleShape r ? ZoneCodec.formatPoint(corner == 1 ? r.corner1() : r.corner2()) : "",
                    (zone, raw, ctx, spec) -> {
                        if (!(zone.shape() instanceof final RectangleShape r)) {
                            return ZoneKeyTable.wrongShape(spec);
                        }
                        final Vec2 p = ZoneCodec.parsePoint(raw);
                        if (p == null) {
                            return SetResult.fail("zone_set_invalid_point", "key", spec.key(), "value", String.valueOf(raw));
                        }
                        final RectangleShape next = r.withCorner(corner, p);
                        final String problem = next.validate();
                        return problem != null ? SetResult.fail(problem) : SetResult.ok(zone.withShape(next));
                    });
        }
        ZoneKeyTable.put(KeySpec.of("points", KeyType.POINT_LIST), z -> z.shape() instanceof final PolygonShape p
                        ? ZoneKeyTable.formatPoints(p.points()) : "",
                (zone, raw, ctx, spec) -> {
                    if (!(zone.shape() instanceof PolygonShape)) {
                        return ZoneKeyTable.wrongShape(spec);
                    }
                    final List<Vec2> pts = new ArrayList<>();
                    for (final String part : (raw == null ? "" : raw).split(";")) {
                        if (part.isBlank()) {
                            continue;
                        }
                        final Vec2 p = ZoneCodec.parsePoint(part);
                        if (p == null) {
                            return SetResult.fail("zone_set_invalid_point", "key", spec.key(), "value", part.trim());
                        }
                        pts.add(p);
                    }
                    final PolygonShape poly = new PolygonShape(pts);
                    final String problem = poly.validate();
                    return problem != null ? SetResult.fail(problem) : SetResult.ok(zone.withShape(poly));
                });
        for (final String key : new String[]{"min_y", "max_y"}) {
            final boolean min = key.equals("min_y");
            ZoneKeyTable.put(KeySpec.number(key, KeyType.OPTIONAL_DOUBLE, Zone.MIN_Y_LIMIT, Zone.MAX_Y_LIMIT, 1, 10),
                    z -> {
                        final Double v = min ? z.minHeight() : z.maxHeight();
                        return v == null ? "none" : ConfigNumbers.format(v);
                    },
                    (zone, raw, ctx, spec) -> {
                        if (KeyParsers.isNone(raw)) {
                            return SetResult.ok(min ? zone.withMinHeight(null) : zone.withMaxHeight(null));
                        }
                        final SetResult<Double> r = KeyParsers.number(spec, raw);
                        if (!r.ok()) {
                            return SetResult.fail(r.error());
                        }
                        final Zone next = min ? zone.withMinHeight(r.value()) : zone.withMaxHeight(r.value());
                        if (next.minHeight() != null && next.maxHeight() != null && next.minHeight() > next.maxHeight()) {
                            return SetResult.fail("editor_error_height_order");
                        }
                        return SetResult.ok(next);
                    });
        }
        ZoneKeyTable.put(KeySpec.option("activation_mode", List.of("double_jump", "auto", "sneak_jump", "f_key")),
                z -> z.activationMode().id(), (zone, raw, ctx, spec) -> {
                    final SetResult<String> r = KeyParsers.option(spec, raw, zone.activationMode().id());
                    return r.ok() ? SetResult.ok(zone.withActivationMode(ActivationMode.fromId(r.value()))) : SetResult.fail(r.error());
                });
        ZoneKeyTable.put(KeySpec.of("boost.enabled", KeyType.BOOLEAN), z -> String.valueOf(z.boost().enabled()),
                ZoneKeyTable.bool(z -> z.boost().enabled(), (z, v) -> z.withBoost(z.boost().withEnabled(v))));
        ZoneKeyTable.put(KeySpec.number("boost.strength", KeyType.DOUBLE, 0.5, 10, 0.5, 2),
                z -> ConfigNumbers.format(z.boost().strength()),
                ZoneKeyTable.number((z, v) -> z.withBoost(z.boost().withStrength(v))));
        ZoneKeyTable.put(KeySpec.option("boost.direction", List.of("forward", "upward")), z -> z.boost().direction().id(),
                (zone, raw, ctx, spec) -> {
                    final SetResult<String> r = KeyParsers.option(spec, raw, zone.boost().direction().id());
                    return r.ok() ? SetResult.ok(zone.withBoost(zone.boost().withDirection(BoostDirection.fromId(r.value()))))
                            : SetResult.fail(r.error());
                });
        ZoneKeyTable.put(KeySpec.number("boost.max_boosts", KeyType.INT, 1, 20, 1, 5),
                z -> String.valueOf(z.boost().maxBoosts()),
                ZoneKeyTable.number((z, v) -> z.withBoost(z.boost().withMaxBoosts(v.intValue()))));
        ZoneKeyTable.put(KeySpec.number("boost.boost_cooldown", KeyType.DOUBLE, 0, 60, 1, 10),
                z -> ConfigNumbers.format(z.boost().cooldownSeconds()),
                ZoneKeyTable.number((z, v) -> z.withBoost(z.boost().withCooldownSeconds(v))));
        ZoneKeyTable.put(KeySpec.of("boost.sound", KeyType.TEXT), z -> z.boost().sound(), (zone, raw, ctx, spec) -> {
            final String s = raw == null ? "" : raw.trim();
            if (s.isEmpty() || !ctx.soundExists(s)) {
                return SetResult.fail("zone_set_invalid_sound", "value", s);
            }
            return SetResult.ok(zone.withBoost(zone.boost().withSound(s)));
        });
        ZoneKeyTable.put(KeySpec.number("f_key.launch_strength", KeyType.DOUBLE, 0.1, 5, 0.1, 1),
                z -> ConfigNumbers.format(z.launchStrength()),
                ZoneKeyTable.number(Zone::withLaunchStrength));
        ZoneKeyTable.put(KeySpec.option("boost_display", ZoneKeyTable.DISPLAY_OPTIONS),
                z -> z.boostDisplayOverride() == null ? "inherit" : z.boostDisplayOverride().id(),
                (zone, raw, ctx, spec) -> {
                    final String current = zone.boostDisplayOverride() == null ? "inherit" : zone.boostDisplayOverride().id();
                    final SetResult<String> r = KeyParsers.option(spec, raw, current);
                    if (!r.ok()) {
                        return SetResult.fail(r.error());
                    }
                    return SetResult.ok(zone.withBoostDisplayOverride("inherit".equals(r.value()) ? null : BoostDisplay.fromId(r.value())));
                });
        ZoneKeyTable.put(KeySpec.option("fireworks.disable_in_spawn_elytra", ZoneKeyTable.FIREWORKS_OPTIONS),
                z -> z.fireworksDisabledOverride() == null ? "inherit" : z.fireworksDisabledOverride().toString(),
                (zone, raw, ctx, spec) -> {
                    final String current = zone.fireworksDisabledOverride() == null ? "inherit" : zone.fireworksDisabledOverride().toString();
                    String in = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                    in = switch (in) {
                        case "on", "yes", "disabled", "blocked" -> "true";
                        case "off", "no", "allowed" -> "false";
                        case "none", "unset", "null", "-" -> "inherit";
                        default -> in;
                    };
                    final SetResult<String> r = KeyParsers.option(spec, in, current);
                    if (!r.ok()) {
                        return SetResult.fail(r.error());
                    }
                    return SetResult.ok(zone.withFireworksDisabledOverride("inherit".equals(r.value()) ? null : Boolean.valueOf(r.value())));
                });
        ZoneKeyTable.put(KeySpec.option("hunger_consumption", ZoneKeyTable.HUNGER_OPTIONS), ZoneKeyTable::hungerOptionValue,
                (zone, raw, ctx, spec) -> {
                    final SetResult<String> r = KeyParsers.option(spec, raw, ZoneKeyTable.hungerOptionValue(zone));
                    if (!r.ok()) {
                        return SetResult.fail(r.error());
                    }
                    final HungerSettings base = ZoneKeyTable.hunger(zone, ctx);
                    return SetResult.ok(switch (r.value()) {
                        case "inherit" -> zone.withHungerOverride(null);
                        case "off" -> zone.withHungerOverride(base.withEnabled(false));
                        default -> zone.withHungerOverride(base.withEnabled(true).withMode(HungerMode.fromId(r.value())));
                    });
                });
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.minimum_food_level", KeyType.INT, 0, 20, 1, 5),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::minimumFoodLevel),
                ZoneKeyTable.hungerNumber((h, v) -> h.withMinimumFoodLevel(v.intValue())));
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.activation.hunger_cost", KeyType.INT, 0, 20, 1, 5),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::activationCost),
                ZoneKeyTable.hungerNumber((h, v) -> h.withActivationCost(v.intValue())));
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.distance.blocks_per_point", KeyType.DOUBLE, 1, 10000, 5, 50),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::blocksPerPoint),
                ZoneKeyTable.hungerNumber(HungerSettings::withBlocksPerPoint));
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.distance.hunger_cost", KeyType.INT, 0, 20, 1, 5),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::distanceCost),
                ZoneKeyTable.hungerNumber((h, v) -> h.withDistanceCost(v.intValue())));
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.time.seconds_per_point", KeyType.DOUBLE, 1, 3600, 5, 60),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::secondsPerPoint),
                ZoneKeyTable.hungerNumber(HungerSettings::withSecondsPerPoint));
        ZoneKeyTable.put(KeySpec.number("hunger_consumption.time.hunger_cost", KeyType.INT, 0, 20, 1, 5),
                z -> ZoneKeyTable.hungerValue(z, HungerSettings::timeCost),
                ZoneKeyTable.hungerNumber((h, v) -> h.withTimeCost(v.intValue())));
    }

    /** {@code inherit | off | activation | distance | time}. */
    public static String hungerOptionValue(final Zone zone) {
        final HungerSettings h = zone.hungerOverride();
        if (h == null) {
            return ZoneKeyTable.HUNGER_INHERIT;
        }
        return h.enabled() ? h.mode().id() : ZoneKeyTable.HUNGER_OFF;
    }

    private static String formatPoints(final List<Vec2> pts) {
        final StringBuilder sb = new StringBuilder();
        for (final Vec2 p : pts) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(ZoneCodec.formatPoint(p));
        }
        return sb.toString();
    }

    /** All keys in table order (tab completion). */
    public static List<String> keys() {
        return List.copyOf(ZoneKeyTable.TABLE.keySet());
    }

    /** All key specs in table order. */
    public static List<KeySpec> specs() {
        final List<KeySpec> l = new ArrayList<>();
        for (final Entry e : ZoneKeyTable.TABLE.values()) {
            l.add(e.spec());
        }
        return l;
    }

    public static Optional<KeySpec> spec(final String key) {
        final Entry e = key == null ? null : ZoneKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT));
        return e == null ? Optional.empty() : Optional.of(e.spec());
    }

    /** Current value as the command syntax would accept it ({@code ""} for keys of another shape). */
    public static String currentValue(final Zone zone, final String key) {
        final Entry e = ZoneKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT));
        return e == null ? "" : e.getter().apply(zone);
    }

    /**
     * Applies {@code /se zone set <zone> <key> <value>} to the (immutable) zone. Unknown key →
     * {@code zone_set_unknown_key}. The result zone is NOT yet saved or validated against other zones.
     */
    public static SetResult<Zone> apply(final Zone zone, final String key, final String rawValue, final KeyContext ctx) {
        final Entry e = key == null ? null : ZoneKeyTable.TABLE.get(key.toLowerCase(Locale.ROOT));
        if (e == null) {
            return SetResult.fail("zone_set_unknown_key", "key", String.valueOf(key));
        }
        return e.setter().apply(zone, rawValue, ctx, e.spec());
    }

    /** Keys applicable to the zone's current shape (others are hidden from menus and completion). */
    public static List<String> keysFor(final Zone zone) {
        final List<String> out = new ArrayList<>();
        for (final String k : ZoneKeyTable.TABLE.keySet()) {
            final boolean shapeKey = k.equals("center") || k.equals("radius") || k.startsWith("corner") || k.equals("points");
            if (!shapeKey
                    || (zone.shape() instanceof CircleShape && (k.equals("center") || k.equals("radius")))
                    || (zone.shape() instanceof RectangleShape && k.startsWith("corner"))
                    || (zone.shape() instanceof PolygonShape && k.equals("points"))) {
                out.add(k);
            }
        }
        return out;
    }
}
