/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.command;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.common.SpawnElytraCore;
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
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.editor.EditorManager;
import com.blaxk.spawnelytra.editor.EditorSession;
import com.blaxk.spawnelytra.util.DisplayNames;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.Texts;
import com.blaxk.spawnelytra.zone.ZoneService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /spawnelytra} ({@code /se}). Every subcommand checks its own permission (spec §10); the command itself has
 * no permission so non-admins can use info / toggle / stats.
 */
public class CommandHandler implements CommandExecutor, TabCompleter {
    private static final String ADMIN = SpawnElytraCore.PERM_ADMIN;

    private final Main plugin;

    public CommandHandler(final Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        this.dispatch(sender, args, false);
        return true;
    }

    private ZoneService zones() {
        return this.plugin.getZoneService();
    }

    private boolean requireAdmin(final CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            MessageUtil.send(sender, "no_permission");
            return false;
        }
        return true;
    }

    private Player requirePlayer(final CommandSender sender) {
        if (sender instanceof final Player p) {
            return p;
        }
        MessageUtil.send(sender, "command_player_only");
        return null;
    }

    /**
     * Executes a subcommand. {@code fromMenu} marks clicks from a dialog: the menu is re-opened afterwards (chat menus
     * are always re-rendered after a change).
     */
    public void dispatch(final CommandSender sender, final String[] args, final boolean fromMenu) {
        if (args.length == 0) {
            if (sender.hasPermission(ADMIN)) {
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
                if (!sender.hasPermission(SpawnElytraCore.PERM_INFO)) {
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
                final Player p = this.requirePlayer(sender);
                if (p != null) {
                    this.plugin.sendSettingsMenu(p);
                }
            }
            case "options" -> {
                if (!this.requireAdmin(sender)) {
                    return;
                }
                final Player p = this.requirePlayer(sender);
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
                final Player p = this.requirePlayer(sender);
                if (p != null && args.length >= 2 && "firstinstall".equalsIgnoreCase(args[1])) {
                    synchronized (this.plugin) {
                        this.plugin.getConfig().set("first_install_completed", false);
                        this.plugin.saveConfig();
                    }
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

    // ---- help / info ------------------------------------------------------------------------------------------------

    private void sendHelpMessage(final CommandSender sender) {
        MessageUtil.send(sender, "help_header");
        final boolean admin = sender.hasPermission(ADMIN);
        if (admin) {
            MessageUtil.send(sender, "help_overview");
            MessageUtil.send(sender, "help_reload");
        }
        if (sender.hasPermission(SpawnElytraCore.PERM_INFO)) {
            MessageUtil.send(sender, "help_info");
        }
        if (sender.hasPermission(SpawnElytraCore.PERM_TOGGLE)) {
            MessageUtil.send(sender, "help_toggle");
        }
        if (sender.hasPermission(SpawnElytraCore.PERM_STATS)) {
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

    private void sendInfoMessage(final CommandSender sender) {
        MessageUtil.send(sender, "info_header");
        final String version = this.plugin.getDescription().getVersion();
        final String author = this.plugin.getDescription().getAuthors().isEmpty()
                ? "Unknown" : this.plugin.getDescription().getAuthors().getFirst();
        final String website = this.plugin.getDescription().getWebsite() != null ? this.plugin.getDescription().getWebsite() : "-";
        final String language = this.plugin.getConfig().getString("language", "en");

        MessageUtil.send(sender, "info_version", Placeholder.unparsed("value", version));
        MessageUtil.sendRaw(sender, this.getAuthorMessage(language.toLowerCase(Locale.ROOT), author));
        MessageUtil.send(sender, "info_website", Placeholder.unparsed("value", website));
        MessageUtil.send(sender, "info_language", Placeholder.unparsed("value", DisplayNames.language(language)));

        if (!(sender instanceof final Player player)) {
            MessageUtil.send(sender, "info_zones_header");
            this.listZones(sender, false);
            return;
        }

        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        final boolean enabled = data.getPlayerData(player.getUniqueId()).isEnabled();
        MessageUtil.send(sender, "info_toggle_state", Placeholder.component("value", MessageUtil.component(enabled ? "state_on" : "state_off")));
        final PermissionTier tier = this.zones().tierOf(player);
        if (tier != null) {
            MessageUtil.send(sender, "info_tier", Placeholder.unparsed("value", tier.name()));
        } else {
            MessageUtil.send(sender, "info_tier", Placeholder.component("value", MessageUtil.component("tier_none")));
        }

        final Zone zone = this.zones().zoneAt(player.getLocation());
        if (zone == null) {
            MessageUtil.send(sender, "info_zone_none");
            return;
        }
        final EffectiveSettings eff = this.zones().effective(zone, player);
        MessageUtil.send(sender, "info_zone", Placeholder.unparsed("value", zone.name()));
        MessageUtil.send(sender, "info_world", Placeholder.unparsed("value", zone.world()));
        MessageUtil.send(sender, "info_shape", Placeholder.component("value",
                Texts.render(Msg.of(zone.shape().type().langKey())).append(Component.text(" ")).append(Texts.render(zone.shape().dims()))));
        MessageUtil.send(sender, "info_priority", Placeholder.unparsed("value", String.valueOf(zone.priority())));
        if (zone.hasHeightLimit()) {
            MessageUtil.send(sender, "info_height", Placeholder.unparsed("value",
                    (zone.minHeight() == null ? "-∞" : com.blaxk.spawnelytra.common.config.ConfigNumbers.format(zone.minHeight()))
                            + " .. " + (zone.maxHeight() == null ? "∞" : com.blaxk.spawnelytra.common.config.ConfigNumbers.format(zone.maxHeight()))));
        } else {
            MessageUtil.send(sender, "info_height", Placeholder.component("value", MessageUtil.component("info_height_unlimited")));
        }
        MessageUtil.send(sender, "info_activation_mode", Placeholder.component("value", Texts.render(Msg.of(eff.activationMode().langKey()))));
        MessageUtil.send(sender, "info_boost_enabled", Placeholder.unparsed("value", String.valueOf(eff.boostEnabled())));
        MessageUtil.send(sender, "info_strength", Placeholder.unparsed("value", com.blaxk.spawnelytra.common.config.ConfigNumbers.format(eff.strength())));
        MessageUtil.send(sender, "info_max_boosts", Placeholder.unparsed("value", String.valueOf(eff.maxBoosts())));
        MessageUtil.send(sender, "info_boost_cooldown", Placeholder.unparsed("value",
                com.blaxk.spawnelytra.common.config.ConfigNumbers.format(eff.cooldownMillis() / 1000.0)));
        MessageUtil.send(sender, "info_boost_display", Placeholder.component("value", Texts.render(Msg.of(eff.boostDisplay().langKey()))));
        if (eff.activationMode() == ActivationMode.F_KEY) {
            MessageUtil.send(sender, "info_offhand_key");
            MessageUtil.send(sender, "info_f_key_launch_strength", Placeholder.unparsed("value",
                    com.blaxk.spawnelytra.common.config.ConfigNumbers.format(eff.launchStrength())));
        }
    }

    private void listZones(final CommandSender sender, final boolean header) {
        final List<Zone> all = this.zones().registry().all();
        if (header) {
            Texts.send(sender, Msg.of("zone_list_header", "count", String.valueOf(all.size())));
        }
        if (all.isEmpty()) {
            Texts.send(sender, Msg.of("zone_list_empty"));
            return;
        }
        for (final Zone z : all) {
            Texts.send(sender, Msg.of("zone_list_entry", "zone", z.name(), "world", z.world(),
                    "shape", Msg.of(z.shape().type().langKey()), "dims", z.shape().dims(),
                    "mode", Msg.of(z.activationMode().langKey()), "priority", String.valueOf(z.priority()),
                    "state", Msg.of(z.enabled() ? "menu_state_enabled" : "menu_state_disabled")));
        }
    }

    // ---- simple subcommands -----------------------------------------------------------------------------------------

    private void visualize(final CommandSender sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        final Player player = this.requirePlayer(sender);
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
                    Texts.send(sender, Msg.of("zone_not_found", "zone", args[i]));
                    return;
                }
                targets = List.of(zone);
            }
        }
        if (targets == null) {
            targets = this.zones().registry().inWorld(player.getWorld().getName());
        }
        if (targets.isEmpty()) {
            MessageUtil.send(sender, "visualize_no_area");
            return;
        }
        if (!targets.getFirst().world().equals(player.getWorld().getName())) {
            Texts.send(sender, Msg.of("zone_error_world_unknown", "world", targets.getFirst().world()));
            return;
        }
        this.plugin.getVisualizer().start(player, targets, seconds);
    }

    private void set(final CommandSender sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (args.length >= 2 && sender instanceof final Player p) {
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

    private void setup(final CommandSender sender, final String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        final Player player = this.requirePlayer(sender);
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
            this.plugin.getMenuService().openOverview(player);
        }
    }

    private void toggle(final CommandSender sender) {
        if (!sender.hasPermission(SpawnElytraCore.PERM_TOGGLE)) {
            MessageUtil.send(sender, "no_permission");
            return;
        }
        final Player player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        final PlayerDataManager.PlayerData pd = data.getPlayerData(player.getUniqueId());
        final boolean enabled = !pd.isEnabled();
        pd.setEnabled(enabled);
        data.markDirty(player.getUniqueId());
        if (!enabled) {
            this.plugin.getSpawnElytra().endFlightSafely(player);
        }
        Texts.send(player, Msg.of(enabled ? "toggle_enabled" : "toggle_disabled"));
    }

    private void stats(final CommandSender sender, final String[] args) {
        final PlayerDataManager data = this.plugin.getPlayerDataManager();
        if (args.length >= 2 && !(sender instanceof final Player self && self.getName().equalsIgnoreCase(args[1]))) {
            if (!sender.hasPermission(SpawnElytraCore.PERM_STATS_OTHERS)) {
                MessageUtil.send(sender, "no_permission");
                return;
            }
            UUID id = null;
            String name = args[1];
            final Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                id = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer cached = null;
                try {
                    cached = Bukkit.getOfflinePlayerIfCached(args[1]);
                } catch (final Throwable noPaperApi) {
                }
                if (cached != null && cached.hasPlayedBefore()) {
                    id = cached.getUniqueId();
                    name = cached.getName() != null ? cached.getName() : args[1];
                }
            }
            if (id == null) {
                Texts.send(sender, Msg.of("stats_player_not_found", "player", args[1]));
                return;
            }
            this.plugin.getMenuService().openStats(sender, name, data.getPlayerData(id).stats());
            return;
        }
        if (!sender.hasPermission(SpawnElytraCore.PERM_STATS)) {
            MessageUtil.send(sender, "no_permission");
            return;
        }
        final Player player = this.requirePlayer(sender);
        if (player == null) {
            return;
        }
        final PlayerStats stats = data.getPlayerData(player.getUniqueId()).stats();
        this.plugin.getMenuService().openStats(sender, player.getName(), stats);
    }

    // ---- /se global -----------------------------------------------------------------------------------------------

    private void global(final CommandSender sender, final String[] args, final boolean fromMenu) {
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
            Texts.send(sender, r.error());
            return;
        }
        this.plugin.applyGlobalSettings();
        Texts.send(sender, Msg.of("menu_global_saved"));
        if (sender instanceof final Player p && (fromMenu || !this.plugin.getMenuService().usesDialogs(p))) {
            this.plugin.getMenuService().openGlobalSettings(p);
        }
    }

    // ---- /se zone ---------------------------------------------------------------------------------------------------

    private void zone(final CommandSender sender, final String[] args, final boolean fromMenu) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Texts.send(sender, Msg.of("zone_usage"));
            return;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> this.listZones(sender, true);
            case "create" -> {
                final Player player = this.requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    Texts.send(sender, Msg.of("zone_usage_create"));
                    return;
                }
                ShapeType type = ShapeType.CIRCLE;
                if (args.length >= 4) {
                    type = ShapeType.fromId(args[3]);
                    if (type == null) {
                        Texts.send(sender, Msg.of("zone_usage_create"));
                        return;
                    }
                }
                this.createZone(player, args[2], type);
            }
            case "edit" -> {
                final Player player = this.requirePlayer(sender);
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
                if (sender instanceof final Player p) {
                    final EditorSession session = this.plugin.getEditorManager().session(p);
                    if (session != null && EditorManager.edits(session, zone.name())) {
                        this.plugin.getMenuService().openZoneSettings(p, session.draft().zone(), Screens.Context.EDITOR);
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
                    Texts.send(sender, Msg.of("zone_deleted", "zone", zone.name()));
                    this.reopenOverview(sender, fromMenu);
                } else {
                    this.plugin.getMenuService().openDeleteConfirm(sender, zone);
                }
            }
            case "tp", "teleport" -> {
                final Player player = this.requirePlayer(sender);
                final Zone zone = this.zoneArg(sender, args, "zone_usage_tp");
                if (player == null || zone == null) {
                    return;
                }
                this.teleport(player, zone, () -> Texts.send(player, Msg.of("zone_teleported", "zone", zone.name())));
            }
            case "enable", "disable" -> {
                final Zone zone = this.zoneArg(sender, args, "zone_usage_toggle");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final boolean enable = "enable".equals(action);
                this.zones().saveZone(zone.withEnabled(enable), zone.name());
                Texts.send(sender, Msg.of(enable ? "zone_enabled" : "zone_disabled", "zone", zone.name()));
                this.reopenOverview(sender, fromMenu);
            }
            case "rename" -> {
                if (args.length < 4) {
                    Texts.send(sender, Msg.of("zone_usage_rename"));
                    return;
                }
                final Zone zone = this.zoneArg(sender, args, "zone_usage_rename");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final SetResult<Zone> r = ZoneKeyTable.apply(zone, "name", args[3], this.zones().keyContext());
                if (!r.ok()) {
                    Texts.send(sender, r.error());
                    return;
                }
                this.zones().saveZone(r.value(), zone.name());
                Texts.send(sender, Msg.of("zone_renamed", "old", zone.name(), "zone", r.value().name()));
            }
            case "priority" -> {
                if (args.length < 4) {
                    Texts.send(sender, Msg.of("zone_usage_priority"));
                    return;
                }
                final Zone zone = this.zoneArg(sender, args, "zone_usage_priority");
                if (zone == null || this.lockedByOther(sender, zone.name())) {
                    return;
                }
                final SetResult<Zone> r = ZoneKeyTable.apply(zone, "priority", args[3], this.zones().keyContext());
                if (!r.ok()) {
                    Texts.send(sender, r.error());
                    return;
                }
                this.zones().saveZone(r.value(), zone.name());
                Texts.send(sender, Msg.of("zone_priority_set", "zone", zone.name(), "value", String.valueOf(r.value().priority())));
            }
            case "set" -> this.zoneSet(sender, args, fromMenu);
            default -> Texts.send(sender, Msg.of("zone_usage"));
        }
    }

    private void reopenOverview(final CommandSender sender, final boolean fromMenu) {
        if (sender instanceof final Player p && (fromMenu || !this.plugin.getMenuService().usesDialogs(p))) {
            this.plugin.getMenuService().openOverview(p);
        }
    }

    private Zone zoneArg(final CommandSender sender, final String[] args, final String usageKey) {
        if (args.length < 3) {
            Texts.send(sender, Msg.of(usageKey));
            return null;
        }
        final Zone zone = this.zones().zone(args[2]).orElse(null);
        if (zone == null) {
            Texts.send(sender, Msg.of("zone_not_found", "zone", args[2]));
        }
        return zone;
    }

    private boolean lockedByOther(final CommandSender sender, final String zoneName) {
        final UUID editor = this.plugin.getEditorManager().editorOf(zoneName);
        if (editor == null || sender instanceof final Player p && p.getUniqueId().equals(editor)) {
            return false;
        }
        Texts.send(sender, Msg.of("zone_error_being_edited", "zone", zoneName, "player", EditorManager.playerName(editor)));
        return true;
    }

    private void zoneSet(final CommandSender sender, final String[] args, final boolean fromMenu) {
        if (args.length < 5) {
            Texts.send(sender, Msg.of("zone_usage_set"));
            return;
        }
        final String zoneName = args[2];
        final String key = args[3].toLowerCase(Locale.ROOT);
        final String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        if (sender instanceof final Player p) {
            final EditorSession session = this.plugin.getEditorManager().session(p);
            if (session != null && EditorManager.edits(session, zoneName)) {
                final EditResult r = this.plugin.getEditorManager().applyKey(p, key, value);
                if (r.success()) {
                    final Zone draft = session.draft().zone();
                    Texts.send(p, Msg.of("zone_set_draft", "zone", draft.name(), "key", key, "value", ZoneKeyTable.currentValue(draft, key)));
                    if (fromMenu || !this.plugin.getMenuService().usesDialogs(p)) {
                        this.plugin.getMenuService().openZoneSettings(p, draft, Screens.Context.EDITOR);
                    }
                } else if (r.error() != null) {
                    Texts.send(p, r.error());
                }
                return;
            }
        }
        final Zone zone = this.zones().zone(zoneName).orElse(null);
        if (zone == null) {
            Texts.send(sender, Msg.of("zone_not_found", "zone", zoneName));
            return;
        }
        if (this.lockedByOther(sender, zone.name())) {
            return;
        }
        final SetResult<Zone> r = ZoneKeyTable.apply(zone, key, value, this.zones().keyContext());
        if (!r.ok()) {
            Texts.send(sender, r.error());
            return;
        }
        this.zones().saveZone(r.value(), zone.name());
        Texts.send(sender, Msg.of("zone_set_success", "zone", r.value().name(), "key", key,
                "value", ZoneKeyTable.currentValue(r.value(), key)));
        if (sender instanceof final Player p && (fromMenu || !this.plugin.getMenuService().usesDialogs(p))) {
            this.plugin.getMenuService().openZoneSettings(p, r.value(), Screens.Context.OVERVIEW);
        }
    }

    private void createZone(final Player player, final String rawName, final ShapeType type) {
        final String name = ZoneNames.normalize(rawName);
        final String problem = ZoneNames.check(rawName, this.zones().registry().names(), null);
        if (problem != null) {
            Texts.send(player, Msg.of(problem, "name", rawName));
            return;
        }
        final UUID editor = this.plugin.getEditorManager().editorOf(name);
        if (editor != null) {
            Texts.send(player, Msg.of("zone_error_being_edited", "zone", name, "player", EditorManager.playerName(editor)));
            return;
        }
        final Location l = player.getLocation();
        final Zone zone = Zone.createDefault(name, player.getWorld().getName(),
                ShapeOps.createAround(type, Vec2.blockCenter(l.getBlockX(), l.getBlockZ())));
        if (this.plugin.getEditorManager().start(player, zone, null)) {
            Texts.send(player, Msg.of("zone_created", "zone", name));
        }
    }

    private void editZone(final Player player, final Zone zone) {
        if (this.plugin.getEditorManager().isEditing(player)) {
            Texts.send(player, Msg.of("zone_error_already_editing"));
            return;
        }
        if (this.lockedByOther(player, zone.name())) {
            return;
        }
        if (!player.getWorld().getName().equals(zone.world())) {
            this.teleport(player, zone, () -> this.plugin.getEditorManager().start(player, zone, zone.name()));
            return;
        }
        this.plugin.getEditorManager().start(player, zone, zone.name());
    }

    /**
     * Teleports to the zone center on the highest block. Folia: the height is read on the region owning the target,
     * the teleport itself is async; {@code after} runs on the player's thread.
     */
    private void teleport(final Player player, final Zone zone, final Runnable after) {
        final World world = Bukkit.getWorld(zone.world());
        final Vec2 center = world == null ? null : zone.shape().center(this.zones().worldSpawn(zone.world()));
        if (world == null || center == null) {
            Texts.send(player, Msg.of("zone_error_teleport_failed", "zone", zone.name()));
            return;
        }
        final Location probe = new Location(world, center.x(), 64, center.z());
        SchedulerUtil.runAtLocation(this.plugin, probe, () -> {
            int y = world.getHighestBlockYAt(probe.getBlockX(), probe.getBlockZ()) + 1;
            if (zone.minHeight() != null && y < zone.minHeight()) {
                y = (int) Math.ceil(zone.minHeight());
            }
            if (zone.maxHeight() != null && y > zone.maxHeight()) {
                y = (int) Math.floor(zone.maxHeight());
            }
            final Location target = new Location(world, center.x(), y, center.z(), player.getLocation().getYaw(), player.getLocation().getPitch());
            SchedulerUtil.runForEntity(this.plugin, player, () -> player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenAccept(ok -> SchedulerUtil.runForEntity(this.plugin, player, () -> {
                        if (Boolean.TRUE.equals(ok)) {
                            after.run();
                        } else {
                            Texts.send(player, Msg.of("zone_error_teleport_failed", "zone", zone.name()));
                        }
                    })));
        });
    }

    // ---- tab completion -----------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>(List.of("help"));
            if (sender.hasPermission(SpawnElytraCore.PERM_INFO)) {
                completions.add("info");
            }
            if (sender.hasPermission(SpawnElytraCore.PERM_TOGGLE)) {
                completions.add("toggle");
            }
            if (sender.hasPermission(SpawnElytraCore.PERM_STATS)) {
                completions.add("stats");
            }
            if (admin) {
                completions.addAll(List.of("reload", "zone", "global", "visualize", "settings", "options", "setup", "set", "update"));
            }
            return filter(completions, args[0]);
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        if ("stats".equals(sub) && args.length == 2 && sender.hasPermission(SpawnElytraCore.PERM_STATS_OTHERS)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
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
