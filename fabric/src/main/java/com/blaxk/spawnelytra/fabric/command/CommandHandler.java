/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.command;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.editor.EditResult;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.keys.GlobalKeyTable;
import com.blaxk.spawnelytra.common.keys.KeySpec;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.keys.ZoneKeyTable;
import com.blaxk.spawnelytra.common.screen.Screens;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.tier.EffectiveSettings;
import com.blaxk.spawnelytra.common.tier.PermissionTier;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.ShapeOps;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.common.zone.ZoneNames;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.data.PlayerDataManager;
import com.blaxk.spawnelytra.fabric.editor.EditorManager;
import com.blaxk.spawnelytra.fabric.editor.EditorSession;
import com.blaxk.spawnelytra.fabric.integration.Perms;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.DisplayNames;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.WorldNames;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * {@code /spawnelytra} ({@code /se}) as Brigadier commands; a port of the Paper 1.6 command
 * handler. Every subcommand checks its own permission (spec 10); the command itself has no
 * permission so non-admins can use info / toggle / stats. Arguments are parsed exactly like the
 * Bukkit command (space separated) and tab completion returns the same suggestions.
 */
public class CommandHandler {
    private static final String ARGS = "args";
    private static final String ADMIN = Perms.ADMIN;

    private final Main plugin;

    public CommandHandler(final Main plugin) {
        this.plugin = plugin;
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("spawnelytra"));
        dispatcher.register(build("se"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(final String label) {
        return Commands.literal(label)
                .executes(ctx -> execute(ctx.getSource(), new String[0]))
                .then(Commands.argument(ARGS, StringArgumentType.greedyString())
                        .suggests(CommandHandler::suggest)
                        .executes(ctx -> execute(ctx.getSource(), StringArgumentType.getString(ctx, ARGS).split(" "))));
    }

    private static int execute(final CommandSourceStack sender, final String[] args) {
        final Main plugin = Main.get();
        if (plugin == null || plugin.getCommandHandler() == null) {
            return 0;
        }
        plugin.getCommandHandler().dispatch(sender, args, false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(final CommandContext<CommandSourceStack> ctx, final SuggestionsBuilder builder) {
        final Main plugin = Main.get();
        final String input = builder.getRemaining();
        final String[] args = input.split(" ", -1);
        final SuggestionsBuilder offset = builder.createOffset(builder.getStart() + input.lastIndexOf(' ') + 1);
        if (plugin != null && plugin.getCommandHandler() != null) {
            for (final String completion : plugin.getCommandHandler().onTabComplete(ctx.getSource(), args)) {
                offset.suggest(completion);
            }
        }
        return offset.buildFuture();
    }

    private ZoneService zones() {
        return this.plugin.getZoneService();
    }

    private boolean requireAdmin(final CommandSourceStack sender) {
        if (!Perms.has(sender, ADMIN)) {
            MessageUtil.send(sender, "no_permission");
            return false;
        }
        return true;
    }

    private ServerPlayer requirePlayer(final CommandSourceStack sender) {
        final ServerPlayer p = sender.getPlayer();
        if (p != null) {
            return p;
        }
        MessageUtil.send(sender, "command_player_only");
        return null;
    }

    /**
     * Executes a subcommand. {@code fromMenu} marks clicks from a dialog: the menu is re-opened
     * afterwards (chat menus are always re-rendered after a change).
     */
    public void dispatch(final CommandSourceStack sender, final String[] args, final boolean fromMenu) {
        if (args.length == 0) {
            if (Perms.has(sender, ADMIN)) {
                this.plugin.getMenuService().openOverview(sender);
            } else {
                this.sendHelpMessage(sender);
            }
            return;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> this.sendHelpMessage(sender);
            case "reload" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                this.plugin.reload();
                MessageUtil.send(sender, "reload_success");
            }
            case "info" -> {
                if (!Perms.has(sender, Perms.INFO)) {
                    MessageUtil.send(sender, "no_permission");
                    return;
                }
                this.sendInfoMessage(sender);
            }
            case "update" -> {
                if (this.requireAdmin(sender)) {
                    this.plugin.performAutoUpdate(sender);
                }
            }
            case "visualize" -> this.visualize(sender, args);
            case "set" -> this.set(sender, args);
            case "settings" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                final ServerPlayer p = this.requirePlayer(sender);
                if (p != null) {
                    this.plugin.sendSettingsMenu(p);
                }
            }
            case "options" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                final ServerPlayer p = this.requirePlayer(sender);
                if (p != null) {
                    this.plugin.sendOptionsMenu(p);
                }
            }
            case "dismiss" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                this.plugin.markFirstInstallCompleted();
                MessageUtil.sendRaw(sender, MiniMessage.miniMessage().deserialize("<#91f251>The first install message will no longer be shown."));
            }
            case "setup" -> this.setup(sender, args);
            case "debug" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                final ServerPlayer p = this.requirePlayer(sender);
                if (p != null && args.length >= 2 && "firstinstall".equalsIgnoreCase(args[1])) {
                    this.plugin.getConfig().set("first_install_completed", false);
                    this.plugin.saveConfig();
                    this.plugin.sendFirstInstallWelcome(p);
                }
            }
            case "toggle" -> this.toggle(sender);
            case "stats" -> this.stats(sender, args);
            case "zone", "zones" -> this.zone(sender, args, fromMenu);
            case "global" -> this.global(sender, args, fromMenu);
            default -> this.sendHelpMessage(sender);
        }
    }

    // ---- help / info -------------------------------------------------------------------------------------------

    private void sendHelpMessage(final CommandSourceStack sender) {
        MessageUtil.send(sender, "help_header");
        final boolean admin = Perms.has(sender, ADMIN);
        if (admin) {
            MessageUtil.send(sender, "help_overview");
            MessageUtil.send(sender, "help_reload");
        }
        if (Perms.has(sender, Perms.INFO)) {
            MessageUtil.send(sender, "help_info");
        }
        if (Perms.has(sender, Perms.TOGGLE)) {
            MessageUtil.send(sender, "help_toggle");
        }
        if (Perms.has(sender, Perms.STATS)) {
            MessageUtil.send(sender, "help_stats");
        }
        if (admin) {
            for (final String key : List.of("help_zone_create", "help_zone_edit", "help_zone_delete", "help_zone_list",
                    "help_zone_tp", "help_zone_toggle", "help_zone_rename", "help_zone_priority", "help_zone_set",
                    "help_zone_settings", "help_global", "help_set_pos", "help_visualize", "help_settings", "help_setup",
                    "help_update")) {
                MessageUtil.send(sender, key);
            }
        }
    }

    private static String height(final Double value, final String unbounded) {
        return value == null ? unbounded : ConfigNumbers.format(value);
    }

    private void sendInfoMessage(final CommandSourceStack sender) {
        MessageUtil.send(sender, "info_header");
        final String version = this.plugin.getVersion();
        final String author = this.plugin.getAuthor();
        final String website = this.plugin.getWebsite() != null ? this.plugin.getWebsite() : "-";
        final String language = this.plugin.getConfig().getString("language", "en");

        MessageUtil.send(sender, "info_version", Placeholder.unparsed("value", version));
        MessageUtil.sendRaw(sender, this.getAuthorMessage(language.toLowerCase(Locale.ROOT), author));
        MessageUtil.send(sender, "info_website", Placeholder.unparsed("value", website));
        MessageUtil.send(sender, "info_language", Placeholder.unparsed("value", DisplayNames.language(language)));

        final ServerPlayer player = sender.getPlayer();
        if (player == null) {
            MessageUtil.send(sender, "info_zones_header");
            this.listZones(sender, false);
            return;
        }

        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        final boolean enabled = data.getPlayerData(player.getUUID()).isEnabled();
        MessageUtil.send(sender, "info_toggle_state", Placeholder.component("value", MessageUtil.component(enabled ? "state_on" : "state_off")));
        final PermissionTier tier = this.zones().tierOf(player);
        if (tier != null) {
            MessageUtil.send(sender, "info_tier", Placeholder.unparsed("value", tier.name()));
        } else {
            MessageUtil.send(sender, "info_tier", Placeholder.component("value", MessageUtil.component("tier_none")));
        }

        final Zone zone = this.zones().zoneAt(player);
        if (zone == null) {
            MessageUtil.send(sender, "info_zone_none");
            return;
        }
        final EffectiveSettings eff = this.zones().effective(zone, player);
        MessageUtil.send(sender, "info_zone", Placeholder.unparsed("value", zone.name()));
        MessageUtil.send(sender, "info_world", Placeholder.unparsed("value", zone.world()));
        MessageUtil.send(sender, "info_shape", Placeholder.component("value",
                MessageUtil.render(Msg.of(zone.shape().type().langKey())).append(Component.text(" ")).append(MessageUtil.render(zone.shape().dims()))));
        MessageUtil.send(sender, "info_priority", Placeholder.unparsed("value", String.valueOf(zone.priority())));
        if (zone.minHeight() != null || zone.maxHeight() != null) {
            MessageUtil.send(sender, "info_height", Placeholder.unparsed("value",
                    height(zone.minHeight(), "-∞") + " .. " + height(zone.maxHeight(), "∞")));
        } else {
            MessageUtil.send(sender, "info_height", Placeholder.component("value", MessageUtil.component("info_height_unlimited")));
        }
        MessageUtil.send(sender, "info_activation_mode", Placeholder.component("value", MessageUtil.render(Msg.of(eff.activationMode().langKey()))));
        MessageUtil.send(sender, "info_boost_enabled", Placeholder.unparsed("value", String.valueOf(eff.boostEnabled())));
        MessageUtil.send(sender, "info_strength", Placeholder.unparsed("value", ConfigNumbers.format(eff.strength())));
        MessageUtil.send(sender, "info_max_boosts", Placeholder.unparsed("value", String.valueOf(eff.maxBoosts())));
        MessageUtil.send(sender, "info_boost_cooldown", Placeholder.unparsed("value", ConfigNumbers.format(eff.cooldownMillis() / 1000.0)));
        MessageUtil.send(sender, "info_boost_display", Placeholder.component("value", MessageUtil.render(Msg.of(eff.boostDisplay().langKey()))));
        if (eff.activationMode() == ActivationMode.F_KEY) {
            MessageUtil.send(sender, "info_offhand_key");
            MessageUtil.send(sender, "info_f_key_launch_strength", Placeholder.unparsed("value", ConfigNumbers.format(eff.launchStrength())));
        }
    }

    private void listZones(final CommandSourceStack sender, final boolean header) {
        final List<Zone> all = this.zones().registry().all();
        if (header) {
            MessageUtil.send(sender, Msg.of("zone_list_header", "count", String.valueOf(all.size())));
        }
        if (all.isEmpty()) {
            MessageUtil.send(sender, Msg.of("zone_list_empty"));
            return;
        }
        for (final Zone z : all) {
            MessageUtil.send(sender, Msg.of("zone_list_entry", "zone", z.name(), "world", z.world(),
                    "shape", Msg.of(z.shape().type().langKey()), "dims", z.shape().dims(),
                    "mode", Msg.of(z.activationMode().langKey()), "priority", String.valueOf(z.priority()),
                    "state", Msg.of(z.enabled() ? "menu_state_enabled" : "menu_state_disabled")));
        }
    }

    // ---- simple subcommands ------------------------------------------------------------------------------------

    private void visualize(final CommandSourceStack sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        final ServerPlayer player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        int seconds = 30;
        List<Zone> targets = null;
        for (int i = 1; i < args.length; i++) {
            try {
                final int parsed = Integer.parseInt(args[i]);
                if (parsed > 0) {
                    seconds = Math.min(parsed, 600);
                }
            } catch (final NumberFormatException notANumber) {
                final Zone zone = this.zones().zone(args[i]).orElse(null);
                if (zone == null) {
                    MessageUtil.send(sender, Msg.of("zone_not_found", "zone", args[i]));
                    return;
                }
                targets = List.of(zone);
            }
        }
        final String world = ZoneService.worldOf(Compat.level(player));
        if (targets == null) {
            targets = this.zones().registry().inWorld(world);
        }
        if (targets.isEmpty()) {
            MessageUtil.send(sender, "visualize_no_area");
            return;
        }
        if (!this.zones().worldKey(targets.get(0).world()).equals(world)) {
            MessageUtil.send(sender, Msg.of("zone_error_world_unknown", "world", targets.get(0).world()));
            return;
        }
        this.plugin.getVisualizer().start(player, targets, seconds);
    }

    private void set(final CommandSourceStack sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        final ServerPlayer p = sender.getPlayer();
        if (args.length >= 2 && p != null) {
            final String what = args[1].toLowerCase(Locale.ROOT);
            if ("pos1".equals(what) || "pos2".equals(what)) {
                this.plugin.getEditorManager().setCorner(p, "pos1".equals(what) ? 1 : 2);
                return;
            }
        }
        if (args.length < 3) {
            MessageUtil.send(sender, "help_set_pos");
            return;
        }
        final String what = args[1].toLowerCase(Locale.ROOT);
        if ("language".equals(what)) {
            this.plugin.applyLanguageSetting(sender, args[2]);
        } else if ("style".equals(what)) {
            this.plugin.applyStyleSetting(sender, args[2]);
        }
    }

    private void setup(final CommandSourceStack sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        final ServerPlayer player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        this.plugin.markFirstInstallCompleted();
        if (args.length >= 2) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "exit", "cancel", "off" -> {
                    this.plugin.getEditorManager().cancel(player, true);
                    return;
                }
                case "save" -> {
                    this.plugin.getEditorManager().save(player);
                    return;
                }
                default -> {
                }
            }
        }
        if (this.zones().registry().isEmpty()) {
            this.createZone(player, "spawn", ShapeType.CIRCLE);
        } else {
            this.plugin.getMenuService().openOverview(sender);
        }
    }

    private void toggle(final CommandSourceStack sender) {
        if (!Perms.has(sender, Perms.TOGGLE)) {
            MessageUtil.send(sender, "no_permission");
            return;
        }
        final ServerPlayer player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        final PlayerDataManager.PlayerData pd = data.getPlayerData(player.getUUID());
        final boolean enabled = !pd.isEnabled();
        pd.setEnabled(enabled);
        data.markDirty(player.getUUID());
        if (!enabled) {
            this.plugin.getController().endFlightSafely(player);
        }
        MessageUtil.send(player, Msg.of(enabled ? "toggle_enabled" : "toggle_disabled"));
    }

    private void stats(final CommandSourceStack sender, final String[] args) {
        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        final ServerPlayer self = sender.getPlayer();
        if (args.length >= 2 && !(self != null && self.getName().getString().equalsIgnoreCase(args[1]))) {
            if (!Perms.has(sender, Perms.STATS_OTHERS)) {
                MessageUtil.send(sender, "no_permission");
                return;
            }
            UUID id = null;
            String name = args[1];
            final ServerPlayer online = this.plugin.getServer().getPlayerList().getPlayerByName(args[1]);
            if (online != null) {
                id = online.getUUID();
                name = online.getName().getString();
            } else {
                final Compat.KnownPlayer cached = Compat.knownPlayer(this.plugin.getServer(), args[1]);
                if (cached != null) {
                    id = cached.id();
                    name = cached.name() != null ? cached.name() : args[1];
                }
            }
            if (id == null) {
                MessageUtil.send(sender, Msg.of("stats_player_not_found", "player", args[1]));
                return;
            }
            this.plugin.getMenuService().openStats(sender, name, data.getPlayerData(id).stats());
            return;
        }
        if (!Perms.has(sender, Perms.STATS)) {
            MessageUtil.send(sender, "no_permission");
            return;
        }
        final ServerPlayer player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        final PlayerStats stats = data.getPlayerData(player.getUUID()).stats();
        this.plugin.getMenuService().openStats(sender, player.getName().getString(), stats);
    }

    // ---- /se global --------------------------------------------------------------------------------------------

    private void global(final CommandSourceStack sender, final String[] args, final boolean fromMenu) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMenuService().openGlobalSettings(sender);
            return;
        }
        if (!"set".equalsIgnoreCase(args[1]) || args.length < 4) {
            MessageUtil.send(sender, "help_global");
            return;
        }
        final String key = args[2].toLowerCase(Locale.ROOT);
        final String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        final Object[] result = new Object[1];
        this.zones().mutateConfig(root -> result[0] = GlobalKeyTable.apply(root, key, value));
        final SetResult<?> r = (SetResult<?>) result[0];
        if (!r.ok()) {
            MessageUtil.send(sender, r.error());
            return;
        }
        this.plugin.applyGlobalSettings();
        MessageUtil.send(sender, Msg.of("menu_global_saved"));
        final ServerPlayer p = sender.getPlayer();
        if (p != null && (fromMenu || !this.plugin.getMenuService().usesDialogs(sender))) {
            this.plugin.getMenuService().openGlobalSettings(sender);
        }
    }

    // ---- /se zone ----------------------------------------------------------------------------------------------

    private void zone(final CommandSourceStack sender, final String[] args, final boolean fromMenu) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, Msg.of("zone_usage"));
            return;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> this.listZones(sender, true);
            case "create" -> {
                final ServerPlayer player = this.requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    MessageUtil.send(sender, Msg.of("zone_usage_create"));
                    return;
                }
                ShapeType type = ShapeType.CIRCLE;
                if (args.length >= 4) {
                    type = ShapeType.fromId(args[3]);
                    if (type == null) {
                        MessageUtil.send(sender, Msg.of("zone_usage_create"));
                        return;
                    }
                }
                this.createZone(player, args[2], type);
            }
            case "edit" -> {
                final ServerPlayer player = this.requirePlayer(sender);
                final Zone zone = this.zoneArg(sender, args, "zone_usage_edit");
                if (player == null || zone == null) {
                    return;
                }
                this.editZone(player, zone);
            }
            case "settings" -> {
                final Zone zone = this.zoneArg(sender, args, "zone_usage_settings");
                if (zone == null) {
                    return;
                }
                final ServerPlayer p = sender.getPlayer();
                if (p != null) {
                    final EditorSession session = this.plugin.getEditorManager().session(p);
                    if (session != null && EditorManager.edits(session, zone.name())) {
                        this.plugin.getMenuService().openZoneSettings(sender, session.draft().zone(), Screens.Context.EDITOR);
                        return;
                    }
                }
                this.plugin.getMenuService().openZoneSettings(sender, zone, Screens.Context.OVERVIEW);
            }
            case "delete" -> {
                final Zone zone = this.zoneArg(sender, args, "zone_usage_delete");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                if (args.length >= 4 && "confirm".equalsIgnoreCase(args[3])) {
                    this.zones().deleteZone(zone.name());
                    MessageUtil.send(sender, Msg.of("zone_deleted", "zone", zone.name()));
                    this.reopenOverview(sender, fromMenu);
                } else {
                    this.plugin.getMenuService().openDeleteConfirm(sender, zone);
                }
            }
            case "tp", "teleport" -> {
                final ServerPlayer player = this.requirePlayer(sender);
                final Zone zone = this.zoneArg(sender, args, "zone_usage_tp");
                if (player == null || zone == null) {
                    return;
                }
                if (this.teleport(player, zone)) {
                    MessageUtil.send(player, Msg.of("zone_teleported", "zone", zone.name()));
                }
            }
            case "enable", "disable" -> {
                final Zone zone = this.zoneArg(sender, args, "zone_usage_toggle");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final boolean enable = "enable".equals(action);
                this.zones().saveZone(zone.withEnabled(enable), zone.name());
                MessageUtil.send(sender, Msg.of(enable ? "zone_enabled" : "zone_disabled", "zone", zone.name()));
                this.reopenOverview(sender, fromMenu);
            }
            case "rename" -> {
                if (args.length < 4) {
                    MessageUtil.send(sender, Msg.of("zone_usage_rename"));
                    return;
                }
                final Zone zone = this.zoneArg(sender, args, "zone_usage_rename");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final SetResult<Zone> r = ZoneKeyTable.apply(zone, "name", args[3], this.zones().keyContext());
                if (!r.ok()) {
                    MessageUtil.send(sender, r.error());
                    return;
                }
                this.zones().saveZone(r.value(), zone.name());
                MessageUtil.send(sender, Msg.of("zone_renamed", "old", zone.name(), "zone", r.value().name()));
            }
            case "priority" -> {
                if (args.length < 4) {
                    MessageUtil.send(sender, Msg.of("zone_usage_priority"));
                    return;
                }
                final Zone zone = this.zoneArg(sender, args, "zone_usage_priority");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final SetResult<Zone> r = ZoneKeyTable.apply(zone, "priority", args[3], this.zones().keyContext());
                if (!r.ok()) {
                    MessageUtil.send(sender, r.error());
                    return;
                }
                this.zones().saveZone(r.value(), zone.name());
                MessageUtil.send(sender, Msg.of("zone_priority_set", "zone", zone.name(), "value", String.valueOf(r.value().priority())));
            }
            case "set" -> this.zoneSet(sender, args, fromMenu);
            default -> MessageUtil.send(sender, Msg.of("zone_usage"));
        }
    }

    private void reopenOverview(final CommandSourceStack sender, final boolean fromMenu) {
        if (sender.getPlayer() != null && (fromMenu || !this.plugin.getMenuService().usesDialogs(sender))) {
            this.plugin.getMenuService().openOverview(sender);
        }
    }

    private Zone zoneArg(final CommandSourceStack sender, final String[] args, final String usageKey) {
        if (args.length < 3) {
            MessageUtil.send(sender, Msg.of(usageKey));
            return null;
        }
        final Zone zone = this.zones().zone(args[2]).orElse(null);
        if (zone == null) {
            MessageUtil.send(sender, Msg.of("zone_not_found", "zone", args[2]));
        }
        return zone;
    }

    private boolean lockedByOther(final CommandSourceStack sender, final String zoneName) {
        final UUID editor = this.plugin.getEditorManager().editorOf(zoneName);
        final ServerPlayer p = sender.getPlayer();
        if (editor == null || p != null && p.getUUID().equals(editor)) {
            return false;
        }
        MessageUtil.send(sender, Msg.of("zone_error_being_edited", "zone", zoneName, "player", this.plugin.getEditorManager().playerName(editor)));
        return true;
    }

    private void zoneSet(final CommandSourceStack sender, final String[] args, final boolean fromMenu) {
        if (args.length < 5) {
            MessageUtil.send(sender, Msg.of("zone_usage_set"));
            return;
        }
        final String zoneName = args[2];
        final String key = args[3].toLowerCase(Locale.ROOT);
        final String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        final ServerPlayer p = sender.getPlayer();
        if (p != null) {
            final EditorSession session = this.plugin.getEditorManager().session(p);
            if (session != null && EditorManager.edits(session, zoneName)) {
                final EditResult r = this.plugin.getEditorManager().applyKey(p, key, value);
                if (r.success()) {
                    final Zone draft = session.draft().zone();
                    MessageUtil.send(p, Msg.of("zone_set_draft", "zone", draft.name(), "key", key, "value", ZoneKeyTable.currentValue(draft, key)));
                    if (fromMenu || !this.plugin.getMenuService().usesDialogs(sender)) {
                        this.plugin.getMenuService().openZoneSettings(sender, draft, Screens.Context.EDITOR);
                    }
                } else if (r.error() != null) {
                    MessageUtil.send(p, r.error());
                }
                return;
            }
        }
        final Zone zone = this.zones().zone(zoneName).orElse(null);
        if (zone == null) {
            MessageUtil.send(sender, Msg.of("zone_not_found", "zone", zoneName));
            return;
        }
        if (this.lockedByOther(sender, zone.name())) {
            return;
        }
        final SetResult<Zone> r = ZoneKeyTable.apply(zone, key, value, this.zones().keyContext());
        if (!r.ok()) {
            MessageUtil.send(sender, r.error());
            return;
        }
        this.zones().saveZone(r.value(), zone.name());
        MessageUtil.send(sender, Msg.of("zone_set_success", "zone", r.value().name(), "key", key,
                "value", ZoneKeyTable.currentValue(r.value(), key)));
        if (p != null && (fromMenu || !this.plugin.getMenuService().usesDialogs(sender))) {
            this.plugin.getMenuService().openZoneSettings(sender, r.value(), Screens.Context.OVERVIEW);
        }
    }

    private void createZone(final ServerPlayer player, final String rawName, final ShapeType type) {
        final String name = ZoneNames.normalize(rawName);
        final String problem = ZoneNames.check(rawName, this.zones().registry().names(), null);
        if (problem != null) {
            MessageUtil.send(player, Msg.of(problem, "name", rawName));
            return;
        }
        final UUID editor = this.plugin.getEditorManager().editorOf(name);
        if (editor != null) {
            MessageUtil.send(player, Msg.of("zone_error_being_edited", "zone", name, "player", this.plugin.getEditorManager().playerName(editor)));
            return;
        }
        final ServerLevel level = Compat.level(player);
        final Zone zone = Zone.createDefault(name, WorldNames.bukkitName(this.plugin.getServer(), level),
                ShapeOps.createAround(type, Vec2.blockCenter((int) Math.floor(player.getX()), (int) Math.floor(player.getZ()))));
        if (this.plugin.getEditorManager().start(player, zone, null)) {
            MessageUtil.send(player, Msg.of("zone_created", "zone", name));
        }
    }

    private void editZone(final ServerPlayer player, final Zone zone) {
        if (this.plugin.getEditorManager().isEditing(player)) {
            MessageUtil.send(player, Msg.of("zone_error_already_editing"));
            return;
        }
        if (this.lockedByOther(player.createCommandSourceStack(), zone.name())) {
            return;
        }
        if (!this.zones().worldKey(zone.world()).equals(ZoneService.worldOf(Compat.level(player)))) {
            if (!this.teleport(player, zone)) {
                return;
            }
        }
        this.plugin.getEditorManager().start(player, zone, zone.name());
    }

    /** Teleports to the zone center on the highest block (clamped into the height bounds). */
    private boolean teleport(final ServerPlayer player, final Zone zone) {
        final ServerLevel level = this.zones().level(zone.world());
        final Vec2 center = level == null ? null : zone.shape().center(this.zones().worldSpawn(zone.world()));
        if (level == null || center == null) {
            MessageUtil.send(player, Msg.of("zone_error_teleport_failed", "zone", zone.name()));
            return false;
        }
        final int bx = (int) Math.floor(center.x());
        final int bz = (int) Math.floor(center.z());
        // Bukkit getHighestBlockYAt + 1 = the first free block above the surface.
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (zone.minY() != null && y < zone.minY()) {
            y = zone.minY();
        }
        if (zone.maxY() != null && y > zone.maxY()) {
            y = zone.maxY();
        }
        Compat.teleport(player, level, center.x(), y, center.z());
        return true;
    }

    // ---- tab completion ----------------------------------------------------------------------------------------

    public List<String> onTabComplete(final CommandSourceStack sender, final String[] args) {
        final boolean admin = Perms.has(sender, ADMIN);
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>(List.of("help"));
            if (Perms.has(sender, Perms.INFO)) {
                completions.add("info");
            }
            if (Perms.has(sender, Perms.TOGGLE)) {
                completions.add("toggle");
            }
            if (Perms.has(sender, Perms.STATS)) {
                completions.add("stats");
            }
            if (admin) {
                completions.addAll(List.of("reload", "zone", "global", "visualize", "settings", "options", "setup", "set", "update"));
            }
            return filter(completions, args[0]);
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        if ("stats".equals(sub) && args.length == 2 && Perms.has(sender, Perms.STATS_OTHERS)) {
            return filter(this.plugin.getOnlinePlayers().stream().map(p -> p.getName().getString()).collect(Collectors.toList()), args[1]);
        }
        if (!admin) {
            return Collections.emptyList();
        }
        switch (sub) {
            case "zone", "zones" -> {
                return this.completeZone(args);
            }
            case "global" -> {
                if (args.length == 2) {
                    return filter(List.of("set"), args[1]);
                }
                if (args.length == 3 && "set".equalsIgnoreCase(args[1])) {
                    return filter(GlobalKeyTable.keys(), args[2]);
                }
                if (args.length == 4 && "set".equalsIgnoreCase(args[1])) {
                    return filter(GlobalKeyTable.spec(args[2].toLowerCase(Locale.ROOT)).map(KeySpec::suggestions).orElse(List.of()), args[3]);
                }
            }
            case "visualize" -> {
                if (args.length == 2) {
                    return filter(this.zones().registry().names(), args[1]);
                }
            }
            case "set" -> {
                if (args.length == 2) {
                    return filter(List.of("pos1", "pos2", "language", "style"), args[1]);
                }
                if (args.length == 3 && "language".equalsIgnoreCase(args[1])) {
                    return filter(List.of("en", "de", "es", "fr", "pl"), args[2]);
                }
                if (args.length == 3 && "style".equalsIgnoreCase(args[1])) {
                    return filter(List.of("classic", "small_caps"), args[2]);
                }
            }
            case "setup" -> {
                if (args.length == 2) {
                    return filter(List.of("save", "exit"), args[1]);
                }
            }
            default -> {
            }
        }
        return Collections.emptyList();
    }

    private List<String> completeZone(final String[] args) {
        final List<String> actions = List.of("create", "edit", "delete", "list", "tp", "enable", "disable", "rename",
                "priority", "set", "settings");
        if (args.length == 2) {
            return filter(actions, args[1]);
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if ("create".equals(action) || "list".equals(action)) {
                return Collections.emptyList();
            }
            return filter(this.zones().registry().names(), args[2]);
        }
        if (args.length == 4) {
            switch (action) {
                case "create" -> {
                    return filter(Arrays.stream(ShapeType.values()).map(ShapeType::id).collect(Collectors.toList()), args[3]);
                }
                case "delete" -> {
                    return filter(List.of("confirm"), args[3]);
                }
                case "set" -> {
                    final Zone zone = this.zones().zone(args[2]).orElse(null);
                    return filter(zone != null ? ZoneKeyTable.keysFor(zone) : ZoneKeyTable.keys(), args[3]);
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }
        if (args.length == 5 && "set".equals(action)) {
            return filter(ZoneKeyTable.spec(args[3].toLowerCase(Locale.ROOT)).map(KeySpec::suggestions).orElse(List.of()), args[4]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(final List<String> options, final String prefix) {
        final String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    private Component getAuthorMessage(final String language, final String author) {
        final String rawStyle = this.plugin.getConfig().getString("messages.style", "classic");
        final String style = (rawStyle == null ? "classic" : rawStyle).toLowerCase(Locale.ROOT);
        String text = switch (language) {
            case "de", "es", "pl" -> "<#fdba5e>Autor: <#91f251>" + author + "</#91f251>";
            case "fr" -> "<#fdba5e>Auteur: <#91f251>" + author + "</#91f251>";
            default -> "<#fdba5e>Author: <#91f251>" + author + "</#91f251>";
        };
        if ("small_caps".equals(style) && ("en".equals(language) || "de".equals(language))) {
            text = MessageUtil.toSmallCapsPreservingTags(text);
        }
        return MiniMessage.miniMessage().deserialize(text);
    }
}
