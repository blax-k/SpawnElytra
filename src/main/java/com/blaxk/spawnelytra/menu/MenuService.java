/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.menu;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.common.SpawnElytraCore;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.screen.Action;
import com.blaxk.spawnelytra.common.screen.ActionIds;
import com.blaxk.spawnelytra.common.screen.Screen;
import com.blaxk.spawnelytra.common.screen.Screens;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.config.BukkitConfigView;
import com.blaxk.spawnelytra.editor.EditorManager;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.Texts;
import com.blaxk.spawnelytra.zone.ZoneService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the core screens either as Paper dialogs (when the Dialog API exists at runtime and {@code menus.mode}
 * allows it) or as clickable chat, and executes dialog actions.
 */
public final class MenuService {
    private static final String BRIDGE_CLASS = "com.blaxk.spawnelytra.dialog.PaperDialogBridge";

    private final Main plugin;
    private final ChatMenuRenderer chat = new ChatMenuRenderer();
    private final DialogBridge bridge;

    public MenuService(final Main plugin) {
        this.plugin = plugin;
        this.bridge = loadBridge(plugin);
    }

    private static DialogBridge loadBridge(final Main plugin) {
        try {
            // Detect by class, never by version string (spec §7.1).
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
            Class.forName("io.papermc.paper.event.player.PlayerCustomClickEvent");
            Class.forName("io.papermc.paper.connection.PlayerGameConnection");
        } catch (final Throwable absent) {
            plugin.getLogger().info("Paper Dialog API not available: menus use the chat fallback.");
            return null;
        }
        try {
            final DialogBridge bridge = (DialogBridge) Class.forName(BRIDGE_CLASS).getConstructor(Main.class).newInstance(plugin);
            plugin.getLogger().info("Paper Dialog API detected: menus use dialogs (menus.mode: auto).");
            return bridge;
        } catch (final Throwable t) {
            plugin.getLogger().warning("Paper Dialog API present but unusable (" + t + "); menus use the chat fallback.");
            return null;
        }
    }

    public boolean dialogsSupported() {
        return this.bridge != null;
    }

    public boolean usesDialogs(final CommandSender sender) {
        return sender instanceof Player && this.plugin.getZoneService().globals().menusMode().useDialogs(this.bridge != null);
    }

    /** Shows a screen. For players this must run on the player's thread. */
    public void show(final CommandSender sender, final Screen screen) {
        if (sender instanceof final Player player && this.usesDialogs(player)) {
            try {
                if (this.bridge.show(player, screen)) {
                    return;
                }
            } catch (final Throwable t) {
                this.plugin.getLogger().warning("Could not show dialog " + screen.id() + ": " + t);
            }
        }
        this.chat.render(sender, screen);
    }

    public void openOverview(final CommandSender sender) {
        this.show(sender, Screens.overview(this.plugin.getZoneService().registry().all()));
    }

    public void openZoneSettings(final CommandSender sender, final Zone zone, final Screens.Context context) {
        final ZoneService zones = this.plugin.getZoneService();
        this.show(sender, Screens.zoneSettings(zone, context, zones.keyContext()));
    }

    public void openGlobalSettings(final CommandSender sender) {
        final Screen screen;
        synchronized (this.plugin) {
            screen = Screens.globalSettings(new BukkitConfigView(this.plugin.getConfig()));
        }
        this.show(sender, screen);
    }

    public void openNewZone(final CommandSender sender) {
        this.show(sender, Screens.newZone());
    }

    public void openDeleteConfirm(final CommandSender sender, final Zone zone) {
        this.show(sender, Screens.deleteConfirm(zone));
    }

    public void openStats(final CommandSender sender, final String playerName, final PlayerStats stats) {
        this.show(sender, Screens.stats(playerName, stats));
    }

    /**
     * Executes a dialog action (already validated as coming from a dialog this plugin showed to that player).
     * Runs on the player's thread. Permissions and zone existence are re-checked here / by the commands.
     */
    public void handleDialogAction(final Player player, final Action action, final Map<String, String> inputs) {
        if (!ActionIds.isKnown(action.id())) {
            Texts.send(player, Msg.of("menu_action_invalid"));
            return;
        }
        if (ActionIds.requiresAdmin(action) && !player.hasPermission(SpawnElytraCore.PERM_ADMIN)) {
            MessageUtil.send(player, "no_permission");
            return;
        }
        switch (action.id()) {
            case ActionIds.CLOSE -> {
            }
            case ActionIds.ZONE_CREATE_FORM -> this.openNewZone(player);
            case ActionIds.ZONE_CREATE -> {
                final String name = inputs.getOrDefault("name", "").trim();
                final String shape = inputs.getOrDefault("shape", "circle").trim();
                if (name.isEmpty()) {
                    Texts.send(player, Msg.of("zone_usage_create"));
                    this.openNewZone(player);
                    return;
                }
                this.plugin.getCommandHandler().dispatch(player, new String[]{"zone", "create", name, shape}, true);
            }
            case ActionIds.ZONE_SETTINGS_SAVE -> this.saveZoneForm(player, action, inputs);
            case ActionIds.GLOBAL_SETTINGS_SAVE -> this.saveGlobalForm(player, inputs);
            default -> {
                final String command = action.command();
                if (command == null) {
                    return;
                }
                final String trimmed = command.trim();
                final String args = trimmed.startsWith(ActionIds.COMMAND) ? trimmed.substring(ActionIds.COMMAND.length()).trim() : trimmed;
                this.plugin.getCommandHandler().dispatch(player, args.isEmpty() ? new String[0] : args.split(" "), true);
            }
        }
    }

    private void saveZoneForm(final Player player, final Action action, final Map<String, String> inputs) {
        final String zoneName = action.get("zone");
        final Screens.Context context = Screens.Context.fromId(action.get("context"));
        final EditorManager editors = this.plugin.getEditorManager();
        if (context == Screens.Context.EDITOR) {
            editors.applySettingsForm(player, zoneName, inputs);
            return;
        }
        final ZoneService zones = this.plugin.getZoneService();
        final Zone zone = zones.zone(zoneName).orElse(null);
        if (zone == null) {
            Texts.send(player, Msg.of("zone_not_found", "zone", String.valueOf(zoneName)));
            return;
        }
        final UUID editor = editors.editorOf(zone.name());
        if (editor != null && !editor.equals(player.getUniqueId())) {
            Texts.send(player, Msg.of("zone_error_being_edited", "zone", zone.name(), "player", EditorManager.playerName(editor)));
            return;
        }
        final SetResult<Zone> r = Screens.applyZoneForm(zone, inputs, zones.keyContext());
        if (!r.ok()) {
            Texts.send(player, r.error());
            this.openZoneSettings(player, zone, Screens.Context.OVERVIEW);
            return;
        }
        if (!r.value().equals(zone)) {
            zones.saveZone(r.value(), zone.name());
        }
        Texts.send(player, Msg.of("menu_saved"));
        this.openOverview(player);
    }

    private void saveGlobalForm(final Player player, final Map<String, String> inputs) {
        final List<Msg> errors = new ArrayList<>();
        this.plugin.getZoneService().mutateConfig(root -> errors.addAll(Screens.applyGlobalForm(root, inputs)));
        this.plugin.applyGlobalSettings();
        for (final Msg error : errors) {
            Texts.send(player, error);
        }
        if (errors.isEmpty()) {
            Texts.send(player, Msg.of("menu_global_saved"));
        }
        this.openOverview(player);
    }

    public void shutdown() {
        if (this.bridge != null) {
            this.bridge.shutdown();
        }
    }
}
