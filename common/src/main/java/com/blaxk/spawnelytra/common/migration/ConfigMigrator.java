/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.migration;

import com.blaxk.spawnelytra.common.config.ConfigView;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostSettings;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.RectangleShape;
import com.blaxk.spawnelytra.common.zone.Shape;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneCodec;
import com.blaxk.spawnelytra.common.zone.ZoneComments;
import com.blaxk.spawnelytra.common.zone.ZoneNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 1.5 → 1.6 config migration on a {@link ConfigView} (spec §1.4). Pure and deterministic: the same input produces
 * the same sequence of {@code set}/{@code createSection}/{@code setComments} calls on both platforms, so the saved
 * files are byte-identical.
 *
 * <h2>Platform flow</h2>
 * <ol>
 *     <li>Run the existing 1.4→1.5 {@code ConfigUpdater} first (it must treat a config with {@code zones} as modern and
 *     must not re-create {@code worlds}).</li>
 *     <li>Load the file into a fresh YAML object (no defaults), wrap it as {@link ConfigView}, call {@link #migrate}.</li>
 *     <li>If {@link Result#changed()}: back up the file (BackupUtil, {@code config/config.yml}) <b>before</b> saving,
 *     save, then refresh the header label to {@code # Plugin Version: 1.6}. Log {@link Result#log()}.</li>
 * </ol>
 * Running it again on its own output changes nothing ({@code changed == false}).
 */
public enum ConfigMigrator {
    ;

    public static final String VERSION = "1.6";
    public static final String LEGACY_WORLDS = "worlds";

    /**
     * Outcome of a migration run.
     *
     * @param changed        anything was written (save + backup needed)
     * @param migratedWorlds the legacy {@code worlds} section was converted
     * @param createdZones   names of the zones created from worlds, in order
     * @param messages       lang messages ({@code migration_*} keys) for in-game/console output
     * @param log            the same messages as plain English lines (lang files may not be loaded yet)
     */
    public record Result(boolean changed, boolean migratedWorlds, List<String> createdZones, List<Msg> messages,
                         List<String> log) {
    }

    /** {@code true} if the config still uses the 1.5 {@code worlds} layout (and has no {@code zones}). */
    public static boolean isLegacy(final ConfigView root) {
        return root.section(ConfigMigrator.LEGACY_WORLDS) != null && root.section(ZoneCodec.ZONES) == null;
    }

    /** Migrates in place; see class docs. */
    public static Result migrate(final ConfigView root) {
        final List<Msg> messages = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final List<String> created = new ArrayList<>();
        boolean changed = false;
        final boolean legacy = ConfigMigrator.isLegacy(root);

        if (legacy) {
            messages.add(Msg.of("migration_started", "from", "1.5", "to", ConfigMigrator.VERSION));
            log.add("Migrating config from v1.5 (worlds) to v" + ConfigMigrator.VERSION + " (zones)...");
        }

        changed |= ConfigMigrator.ensureGlobals(root, messages, log);

        if (legacy) {
            final ConfigView worlds = root.section(ConfigMigrator.LEGACY_WORLDS);
            final List<String> order = ConfigMigrator.worldOrder(worlds.keys());
            root.createSection(ZoneCodec.ZONES);
            root.setComments(ZoneCodec.ZONES, ZoneComments.ZONES_SECTION);
            boolean first = true;
            for (final String worldName : order) {
                final ConfigView w = worlds.section(worldName);
                if (w == null) {
                    messages.add(Msg.of("migration_world_skipped", "world", worldName));
                    log.add("Skipped worlds." + worldName + " (not a section).");
                    continue;
                }
                final String zoneName = first ? "spawn"
                        : ZoneNames.unique("spawn_" + ZoneNames.sanitize(worldName, ZoneNames.MAX_LENGTH - 6), created);
                first = false;
                final Zone zone = ConfigMigrator.convertWorld(zoneName, worldName, w);
                ZoneCodec.write(root, zone, null, true);
                ConfigMigrator.copyOptional(w, root.section(ZoneCodec.ZONES).section(zoneName));
                created.add(zoneName);
                messages.add(Msg.of("migration_world_converted", "world", worldName, "zone", zoneName,
                        "shape", zone.shape().type().id()));
                log.add("Converted worlds." + worldName + " into zone '" + zoneName + "' (" + zone.shape().type().id() + ").");
            }
            root.set(ConfigMigrator.LEGACY_WORLDS, null);
            ConfigMigrator.refreshHungerComment(root);
            changed = true;
            messages.add(Msg.of("migration_complete", "count", String.valueOf(created.size()), "version", ConfigMigrator.VERSION));
            log.add("Config migrated to v" + ConfigMigrator.VERSION + ": " + created.size() + " zone(s) created.");
        } else if (root.section(ConfigMigrator.LEGACY_WORLDS) != null) {
            messages.add(Msg.of("migration_worlds_ignored"));
            log.add("Both 'zones' and the legacy 'worlds' section exist; 'worlds' is ignored. Remove it to silence this warning.");
        }
        return new Result(changed, legacy, Collections.unmodifiableList(created), Collections.unmodifiableList(messages),
                Collections.unmodifiableList(log));
    }

    /** {@code world} first, then {@code minecraft:overworld} (Fabric alias), then config order. */
    static List<String> worldOrder(final Iterable<String> keys) {
        final List<String> order = new ArrayList<>();
        for (final String k : keys) {
            order.add(k);
        }
        String firstKey = null;
        for (final String k : order) {
            if (k.equals("world")) {
                firstKey = k;
                break;
            }
        }
        if (firstKey == null) {
            for (final String k : order) {
                if (k.equalsIgnoreCase("minecraft:overworld")) {
                    firstKey = k;
                    break;
                }
            }
        }
        if (firstKey != null) {
            order.remove(firstKey);
            order.add(0, firstKey);
        }
        return order;
    }

    /**
     * Converts one 1.5 {@code worlds.<world>} section (spec §1.4):
     * advanced + rectangular + second corner not all-zero (x2/y2/z2, exactly the 1.5 test) → rectangle
     * (corner1 = x/z, corner2 = x2/z2) with {@code min_y = min(y, y2)}, {@code max_y = max(y, y2)} as exact doubles, so
     * containment equals the 1.5 box test {@code min <= coord <= max} on all three axes (if {@code spawn_area.y} is
     * missing — 1.5 then used the live world spawn Y — the height stays unlimited); otherwise a circle with
     * {@code center: world_spawn} (auto) or {@code center: {x, z}} (advanced), radius {@code radius}, no height limit.
     */
    public static Zone convertWorld(final String zoneName, final String worldName, final ConfigView w) {
        final String mode = w.getString("spawn_area.mode", "auto").toLowerCase(Locale.ROOT);
        final String areaType = w.getString("spawn_area.area_type", "circular");
        final double x2 = w.getDouble("spawn_area.x2", 0);
        final double y2 = w.getDouble("spawn_area.y2", 0);
        final double z2 = w.getDouble("spawn_area.z2", 0);
        final boolean allZero = x2 == 0 && y2 == 0 && z2 == 0;
        final boolean advanced = "advanced".equals(mode);
        final Shape shape;
        Double minY = null;
        Double maxY = null;
        if (advanced && !allZero && "rectangular".equalsIgnoreCase(areaType)) {
            shape = new RectangleShape(new Vec2(w.getDouble("spawn_area.x", 0), w.getDouble("spawn_area.z", 0)), new Vec2(x2, z2));
            final Double y = w.getDoubleOrNull("spawn_area.y");
            if (y != null) {
                minY = Math.min(y, y2);
                maxY = Math.max(y, y2);
            }
        } else {
            Vec2 center = null;
            if (advanced && (w.contains("spawn_area.x") || w.contains("spawn_area.z"))) {
                center = new Vec2(w.getDouble("spawn_area.x", 0), w.getDouble("spawn_area.z", 0));
            }
            shape = new CircleShape(center, w.getDouble("radius", 100));
        }
        return new Zone(zoneName, worldName, w.getBoolean("enabled", true), 0, shape, minY, maxY,
                ActivationMode.fromIdOrDefault(w.getString("activation_mode", "double_jump")),
                BoostSettings.read(w.section("boost")), w.getDouble("f_key.launch_strength", Zone.DEFAULT_LAUNCH_STRENGTH),
                null, null, null);
    }

    /** Copies per-world {@code hunger_consumption} (verbatim) and {@code fireworks} overrides if present. */
    private static void copyOptional(final ConfigView world, final ConfigView zone) {
        final ConfigView hunger = world.section("hunger_consumption");
        if (hunger != null) {
            zone.createSection("hunger_consumption");
            zone.copyFrom(hunger, "hunger_consumption");
            zone.setComments("hunger_consumption", ZoneComments.DEFAULTS.get("hunger_consumption"));
        }
        if (world.get("fireworks.disable_in_spawn_elytra") instanceof final Boolean b) {
            zone.createSection("fireworks");
            zone.set("fireworks.disable_in_spawn_elytra", b);
            zone.setComments("fireworks", ZoneComments.DEFAULTS.get("fireworks"));
        }
    }

    /** Adds missing 1.6 global keys with their shipped comments. Returns {@code true} if something was added. */
    static boolean ensureGlobals(final ConfigView root, final List<Msg> messages, final List<String> log) {
        boolean changed = false;
        if (!root.contains("boost_display")) {
            root.set("boost_display", "actionbar");
            root.setComments("boost_display", ConfigMigrator.BOOST_DISPLAY_COMMENTS);
            changed = true;
            ConfigMigrator.added("boost_display", messages, log);
        }
        if (!root.contains("menus.mode")) {
            if (root.section("menus") == null) {
                root.createSection("menus");
                root.setComments("menus", ConfigMigrator.MENUS_COMMENTS);
            }
            root.set("menus.mode", "auto");
            root.setComments("menus.mode", ConfigMigrator.MENUS_MODE_COMMENTS);
            changed = true;
            ConfigMigrator.added("menus.mode", messages, log);
        }
        if (!root.contains("permission_tiers")) {
            root.createSection("permission_tiers");
            root.setComments("permission_tiers", ConfigMigrator.TIERS_COMMENTS);
            changed = true;
            ConfigMigrator.added("permission_tiers", messages, log);
        }
        return changed;
    }

    private static void added(final String key, final List<Msg> messages, final List<String> log) {
        messages.add(Msg.of("migration_added_key", "key", key));
        log.add("Added missing config key '" + key + "'.");
    }

    private static void refreshHungerComment(final ConfigView root) {
        if (root.contains("hunger_consumption")) {
            final List<String> c = root.comments("hunger_consumption");
            final List<String> updated = new ArrayList<>(c.size());
            boolean touched = false;
            for (final String line : c) {
                if (line != null && line.contains("per-world")) {
                    updated.add(line.replace("per-world", "per-zone"));
                    touched = true;
                } else {
                    updated.add(line);
                }
            }
            if (touched) {
                root.setComments("hunger_consumption", updated);
            }
        }
    }

    private static List<String> lines(final String... l) {
        return Collections.unmodifiableList(Arrays.asList(l));
    }

    /** Comments of {@code boost_display} (also used in the shipped config.yml). */
    public static final List<String> BOOST_DISPLAY_COMMENTS = ConfigMigrator.lines(
            null,
            "Where boost hints are shown while gliding (can be overridden per zone):",
            "actionbar: \"Press F to boost\" messages in the actionbar (like 1.5)",
            "bossbar: a bossbar with the remaining boosts and the cooldown",
            "none: no boost hints");

    public static final List<String> MENUS_COMMENTS = ConfigMigrator.lines(null, "Admin menus (/se, zone settings, global settings)");

    public static final List<String> MENUS_MODE_COMMENTS = ConfigMigrator.lines(
            "auto: dialogs on servers that support them (1.21.6+), chat menus otherwise",
            "dialog: always use dialogs (falls back to chat if unsupported)",
            "chat: always use clickable chat menus");

    public static final List<String> TIERS_COMMENTS = ConfigMigrator.lines(
            null,
            "Permission tiers: give players better boosts with the permission spawnelytra.tier.<name>",
            "(not granted to anyone by default, not even OPs). The tier with the highest priority wins.",
            "Every value is optional and overrides the zone value. Example:",
            "permission_tiers:",
            "  vip:",
            "    priority: 10",
            "    max_boosts: 3",
            "    strength: 5.0",
            "    boost_cooldown: 1",
            "    launch_strength: 2.0");
}
