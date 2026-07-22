/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.keys.GlobalKeyTable;
import com.blaxk.spawnelytra.common.keys.KeyContext;
import com.blaxk.spawnelytra.common.keys.KeySpec;
import com.blaxk.spawnelytra.common.keys.KeyType;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.keys.ZoneKeyTable;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.stats.StatsFormat;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the screen models of spec §7.2. Pure functions of the current state; rebuild after every change.
 * Also contains the form-submit appliers used by the dialog handlers.
 */
public enum Screens {
    ;

    /** Where the zone settings screen was opened from (spec §7.2 #2: overview saves, editor updates the draft). */
    public enum Context {
        OVERVIEW("overview"), EDITOR("editor");

        private final String id;

        Context(final String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Context fromId(final String id) {
            return "editor".equals(id) ? EDITOR : OVERVIEW;
        }
    }

    /** Zone keys shown on the zone settings screen, in order (spec §7.2 #2). */
    public static final List<String> ZONE_SETTINGS_KEYS = List.of(
            "name", "world", "enabled", "priority", "activation_mode",
            "boost.enabled", "boost.strength", "boost.direction", "boost.max_boosts", "boost.boost_cooldown", "boost.sound",
            "f_key.launch_strength", "boost_display", "fireworks.disable_in_spawn_elytra",
            "hunger_consumption", "hunger_consumption.minimum_food_level", "hunger_consumption.activation.hunger_cost",
            "hunger_consumption.distance.blocks_per_point", "hunger_consumption.distance.hunger_cost",
            "hunger_consumption.time.seconds_per_point", "hunger_consumption.time.hunger_cost");

    // ---- overview -------------------------------------------------------------------------------------------------

    /** 1. Overview: one row per zone + global buttons. */
    public static Screen overview(final List<Zone> zones) {
        final List<Element> body = new ArrayList<>();
        if (zones.isEmpty()) {
            body.add(new Element.Text(Msg.of("menu_overview_empty")));
        }
        for (final Zone z : zones) {
            final String n = z.name();
            final List<Button> buttons = new ArrayList<>();
            buttons.add(Button.of("menu_button_settings", "menu_hover_settings", Action.of(ActionIds.ZONE_SETTINGS, "zone", n)));
            buttons.add(Button.of("menu_button_edit", "menu_hover_edit", Action.of(ActionIds.ZONE_EDIT, "zone", n)));
            buttons.add(Button.of("menu_button_teleport", "menu_hover_teleport", Action.of(ActionIds.ZONE_TP, "zone", n)));
            buttons.add(z.enabled()
                    ? Button.of("menu_button_disable", "menu_hover_disable", Action.of(ActionIds.ZONE_DISABLE, "zone", n))
                    : Button.of("menu_button_enable", "menu_hover_enable", Action.of(ActionIds.ZONE_ENABLE, "zone", n)));
            buttons.add(Button.of("menu_button_delete", "menu_hover_delete", Action.of(ActionIds.ZONE_DELETE, "zone", n)));
            body.add(new Element.Row(n, Screens.rowText(z), buttons));
        }
        final List<Button> footer = new ArrayList<>();
        footer.add(Button.of("menu_button_new_zone", "menu_hover_new_zone", Action.of(ActionIds.ZONE_CREATE_FORM)).dialogOnly());
        footer.add(Button.of("menu_button_new_zone", "menu_hover_new_zone", Action.of(ActionIds.ZONE_CREATE_FORM).suggesting()).chatOnly());
        footer.add(Button.of("menu_button_global_settings", "menu_hover_global_settings", Action.of(ActionIds.GLOBAL_SETTINGS)));
        footer.add(Button.of("menu_button_language_style", "menu_hover_language_style", Action.of(ActionIds.LANGUAGE_STYLE)));
        final Button close = Button.of("menu_button_close", null, Action.of(ActionIds.CLOSE)).dialogOnly();
        footer.add(close);
        return new Screen(Screen.ScreenId.OVERVIEW, Msg.of("menu_overview_title", "count", String.valueOf(zones.size())),
                body, footer, close);
    }

    /** {@code menu_overview_row}: name, world, shape + dims, activation mode, enabled state. */
    public static Msg rowText(final Zone z) {
        return Msg.of("menu_overview_row", "zone", z.name(), "world", z.world(), "shape", Msg.of(z.shape().type().langKey()),
                "dims", z.shape().dims(), "mode", Msg.of(z.activationMode().langKey()),
                "state", Msg.of(z.enabled() ? "menu_state_enabled" : "menu_state_disabled"),
                "priority", String.valueOf(z.priority()));
    }

    // ---- zone settings ----------------------------------------------------------------------------------------------

    /**
     * 2. Zone settings. In chat mode every field applies immediately ({@code /se zone set}, routed to the draft when
     * the sender is editing that zone). In dialog mode Save submits all inputs ({@link #applyZoneForm}).
     */
    public static Screen zoneSettings(final Zone zone, final Context context, final KeyContext ctx) {
        final Field.Target target = Field.Target.zone(zone.name());
        final List<Element> body = new ArrayList<>();
        for (final String key : Screens.ZONE_SETTINGS_KEYS) {
            final KeySpec spec = ZoneKeyTable.spec(key).orElseThrow();
            if (key.equals("world")) {
                body.add(new Field.ReadOnly(key, Msg.of(spec.labelKey()), Msg.literal(zone.world())));
                continue;
            }
            String value = ZoneKeyTable.currentValue(zone, key);
            if (key.startsWith("hunger_consumption.") && zone.hungerOverride() == null) {
                value = Screens.globalHungerValue(ctx, key);
            }
            body.add(Screens.field(spec, value, target, key.equals("name") ? ZoneNames.MAX_LENGTH : 64));
        }
        final List<Button> footer = new ArrayList<>();
        footer.add(Button.of("menu_button_save", "menu_hover_save_" + context.id(),
                Action.of(ActionIds.ZONE_SETTINGS_SAVE, "zone", zone.name(), "context", context.id())).dialogOnly());
        final Button cancel = context == Context.OVERVIEW
                ? Button.of("menu_button_cancel", null, Action.of(ActionIds.OVERVIEW)).dialogOnly()
                : Button.of("menu_button_cancel", null, Action.of(ActionIds.CLOSE)).dialogOnly();
        footer.add(cancel);
        if (context == Context.OVERVIEW) {
            footer.add(Button.of("menu_button_back", "menu_hover_back", Action.of(ActionIds.OVERVIEW)));
        }
        return new Screen(Screen.ScreenId.ZONE_SETTINGS, Msg.of("menu_zone_title", "zone", zone.name()), body, footer, cancel);
    }

    private static String globalHungerValue(final KeyContext ctx, final String key) {
        final var h = ctx.globalHunger();
        return switch (key) {
            case "hunger_consumption.minimum_food_level" -> String.valueOf(h.minimumFoodLevel());
            case "hunger_consumption.activation.hunger_cost" -> String.valueOf(h.activationCost());
            case "hunger_consumption.distance.blocks_per_point" -> String.valueOf(h.blocksPerPoint());
            case "hunger_consumption.distance.hunger_cost" -> String.valueOf(h.distanceCost());
            case "hunger_consumption.time.seconds_per_point" -> String.valueOf(h.secondsPerPoint());
            case "hunger_consumption.time.hunger_cost" -> String.valueOf(h.timeCost());
            default -> "";
        };
    }

    /** Builds a field from a key spec and its current value. */
    public static Field field(final KeySpec spec, final String value, final Field.Target target, final int maxLength) {
        final Msg label = Msg.of(spec.labelKey());
        return switch (spec.type()) {
            case BOOLEAN -> new Field.Bool(spec.key(), label, Boolean.parseBoolean(value), target);
            case INT, DOUBLE, OPTIONAL_INT, OPTIONAL_DOUBLE -> {
                double v;
                try {
                    v = Double.parseDouble(value);
                } catch (final NumberFormatException e) {
                    v = spec.min();
                }
                yield new Field.Number(spec.key(), label, v, spec.min(), spec.max(), spec.step(), spec.largeStep(),
                        spec.type() == KeyType.INT || spec.type() == KeyType.OPTIONAL_INT, target);
            }
            case OPTION -> {
                final List<Field.Option> options = new ArrayList<>();
                for (final String o : spec.options()) {
                    options.add(new Field.Option(o, OptionLabels.of(spec.key(), o)));
                }
                yield new Field.Choice(spec.key(), label, options, value, target);
            }
            default -> new Field.TextInput(spec.key(), label, value, maxLength, target);
        };
    }

    /**
     * Applies a zone-settings dialog submit (inputs keyed by zone key) to the zone. Unchanged values, read-only keys
     * and unknown keys are skipped; hunger numbers are skipped while the hunger option is {@code inherit}.
     * Returns the first error, or the updated zone (not yet saved; may carry a new name).
     */
    public static SetResult<Zone> applyZoneForm(final Zone zone, final Map<String, String> inputs, final KeyContext ctx) {
        Zone current = zone;
        final List<String> order = new ArrayList<>(Screens.ZONE_SETTINGS_KEYS);
        order.remove("name");
        order.add("name"); // rename last so other keys apply to the same zone
        for (final String key : order) {
            if (key.equals("world") || !inputs.containsKey(key)) {
                continue;
            }
            if (key.startsWith("hunger_consumption.") && current.hungerOverride() == null) {
                continue;
            }
            final String raw = inputs.get(key);
            if (Screens.sameValue(ZoneKeyTable.currentValue(current, key), raw)) {
                continue;
            }
            final SetResult<Zone> r = ZoneKeyTable.apply(current, key, raw, ctx);
            if (!r.ok()) {
                return r;
            }
            current = r.value();
        }
        return SetResult.ok(current);
    }

    /** {@code true} if both strings denote the same value (numerically equal numbers count as equal). */
    static boolean sameValue(final String a, final String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.equals(b)) {
            return true;
        }
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    // ---- global settings ----------------------------------------------------------------------------------------

    /** 3. Global settings: every {@link GlobalKeyTable} key. */
    public static Screen globalSettings(final ConfigView root) {
        final List<Element> body = new ArrayList<>();
        for (final KeySpec spec : GlobalKeyTable.specs()) {
            body.add(Screens.field(spec, GlobalKeyTable.currentValue(root, spec.key()), Field.Target.GLOBAL, 64));
        }
        final List<Button> footer = new ArrayList<>();
        footer.add(Button.of("menu_button_save", "menu_hover_save_overview", Action.of(ActionIds.GLOBAL_SETTINGS_SAVE)).dialogOnly());
        final Button cancel = Button.of("menu_button_cancel", null, Action.of(ActionIds.OVERVIEW)).dialogOnly();
        footer.add(cancel);
        footer.add(Button.of("menu_button_back", "menu_hover_back", Action.of(ActionIds.OVERVIEW)));
        return new Screen(Screen.ScreenId.GLOBAL_SETTINGS, Msg.of("menu_global_title"), body, footer, cancel);
    }

    /**
     * Applies a global-settings dialog submit into {@code root} (changed keys only). Returns the errors (empty =
     * all applied); valid keys are applied even if another key fails. The platform saves and reloads afterwards.
     */
    public static List<Msg> applyGlobalForm(final ConfigView root, final Map<String, String> inputs) {
        final List<Msg> errors = new ArrayList<>();
        for (final String key : GlobalKeyTable.keys()) {
            if (!inputs.containsKey(key)) {
                continue;
            }
            final String raw = inputs.get(key);
            if (Screens.sameValue(GlobalKeyTable.currentValue(root, key), raw)) {
                continue;
            }
            final SetResult<Object> r = GlobalKeyTable.apply(root, key, raw);
            if (!r.ok()) {
                errors.add(r.error());
            }
        }
        return errors;
    }

    // ---- new zone / delete / stats --------------------------------------------------------------------------------

    /** "New zone" form (dialog): name + shape; submit {@link ActionIds#ZONE_CREATE} with inputs {@code name}, {@code shape}. */
    public static Screen newZone() {
        final List<Element> body = new ArrayList<>();
        body.add(new Element.Text(Msg.of("menu_new_zone_text")));
        body.add(new Field.TextInput("name", Msg.of("menu_field_name"), "", ZoneNames.MAX_LENGTH, Field.Target.FORM));
        final List<Field.Option> shapes = new ArrayList<>();
        for (final ShapeType t : ShapeType.values()) {
            shapes.add(new Field.Option(t.id(), Msg.of(t.langKey())));
        }
        body.add(new Field.Choice("shape", Msg.of("menu_field_shape"), shapes, ShapeType.CIRCLE.id(), Field.Target.FORM));
        final List<Button> footer = new ArrayList<>();
        footer.add(Button.of("menu_button_create", "menu_hover_create", Action.of(ActionIds.ZONE_CREATE)).dialogOnly());
        final Button back = Button.of("menu_button_back", "menu_hover_back", Action.of(ActionIds.OVERVIEW));
        footer.add(back);
        return new Screen(Screen.ScreenId.NEW_ZONE, Msg.of("menu_new_zone_title"), body, footer, back);
    }

    /** 4. Delete confirmation. */
    public static Screen deleteConfirm(final Zone zone) {
        final List<Element> body = List.of(new Element.Text(Msg.of("menu_delete_text", "zone", zone.name(), "world", zone.world())));
        final Button cancel = Button.of("menu_button_cancel", null, Action.of(ActionIds.OVERVIEW));
        final List<Button> footer = List.of(
                Button.of("menu_button_confirm", "menu_hover_confirm_delete", Action.of(ActionIds.ZONE_DELETE_CONFIRM, "zone", zone.name())),
                cancel);
        return new Screen(Screen.ScreenId.DELETE_CONFIRM, Msg.of("menu_delete_title", "zone", zone.name()), body, footer, cancel);
    }

    /** 5. Stats (dialog notice / chat lines). */
    public static Screen stats(final String playerName, final PlayerStats s) {
        final List<Element> body = List.of(
                new Element.Text(Msg.of("stats_flights", "value", StatsFormat.placeholder(s, "flights"))),
                new Element.Text(Msg.of("stats_distance", "value", StatsFormat.blocks(s.distance()))),
                new Element.Text(Msg.of("stats_boosts", "value", StatsFormat.placeholder(s, "boosts"))),
                new Element.Text(Msg.of("stats_glide_time", "value", StatsFormat.duration(s.glideTimeSeconds()))),
                new Element.Text(Msg.of("stats_longest_flight", "value", StatsFormat.blocks(s.longestFlight()))));
        final Button close = Button.of("menu_button_close", null, Action.of(ActionIds.CLOSE)).dialogOnly();
        return new Screen(Screen.ScreenId.STATS, Msg.of("stats_header", "player", playerName), body, List.of(close), close);
    }
}
