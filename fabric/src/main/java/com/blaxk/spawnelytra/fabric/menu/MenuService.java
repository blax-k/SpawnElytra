/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.menu;

import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.screen.Action;
import com.blaxk.spawnelytra.common.screen.ActionIds;
import com.blaxk.spawnelytra.common.screen.Screen;
import com.blaxk.spawnelytra.common.screen.Screens;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.config.SectionView;
import com.blaxk.spawnelytra.fabric.editor.EditorManager;
import com.blaxk.spawnelytra.fabric.integration.Perms;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the core screens either as vanilla dialogs (Minecraft 1.21.6+ nodes, when
 * {@code menus.mode} allows it) or as clickable chat, and executes dialog actions (port of the
 * Paper MenuService).
 */
public final class MenuService {
    private final Main plugin;
    private final ChatMenuRenderer chat = new ChatMenuRenderer();
    private final DialogRenderer dialogs;

    public MenuService(final Main plugin) {
        this.plugin = plugin;
        this.dialogs = DialogRenderer.SUPPORTED ? new DialogRenderer(plugin) : null;
        plugin.getLogger().info(DialogRenderer.SUPPORTED
                ? "Vanilla dialogs available: menus use dialogs (menus.mode: auto)."
                : "Vanilla dialogs not available (Minecraft < 1.21.6): menus use the chat fallback.");
    }

    public boolean dialogsSupported() {
        return this.dialogs != null;
    }

    public boolean usesDialogs(final CommandSourceStack sender) {
        return sender.getPlayer() != null && this.plugin.getZoneService().globals().menusMode().useDialogs(this.dialogs != null);
    }

    public void show(final CommandSourceStack sender, final Screen screen) {
        final ServerPlayer player = sender.getPlayer();
        if (player != null && this.usesDialogs(sender)) {
            try {
                if (this.dialogs.show(player, screen)) {
                    return;
                }
            } catch (final Throwable t) {
                this.plugin.getLogger().warn("Could not show dialog {}: {}", screen.id(), t.toString());
            }
        }
        this.chat.render(sender, screen);
    }

    public void openOverview(final CommandSourceStack sender) {
        this.show(sender, Screens.overview(this.plugin.getZoneService().registry().all()));
    }

    public void openZoneSettings(final CommandSourceStack sender, final Zone zone, final Screens.Context context) {
        final ZoneService zones = this.plugin.getZoneService();
        this.show(sender, Screens.zoneSettings(zone, context, zones.keyContext()));
    }

    public void openGlobalSettings(final CommandSourceStack sender) {
        this.show(sender, Screens.globalSettings(new SectionView(this.plugin.getConfig())));
    }

    public void openNewZone(final CommandSourceStack sender) {
        this.show(sender, Screens.newZone());
    }

    public void openDeleteConfirm(final CommandSourceStack sender, final Zone zone) {
        this.show(sender, Screens.deleteConfirm(zone));
    }

    public void openStats(final CommandSourceStack sender, final String playerName, final PlayerStats stats) {
        this.show(sender, Screens.stats(playerName, stats));
    }

    /** Custom click action ({@code spawnelytra:<path>}) from the packet hook. */
    public void onCustomClick(final ServerPlayer player, final String path, final Tag payload) {
        if (this.dialogs != null) {
            this.dialogs.onCustomClick(player, ActionIds.NAMESPACE + ":" + path, payload);
        }
    }

    public void onQuit(final ServerPlayer player) {
        if (this.dialogs != null) {
            this.dialogs.forget(player.getUUID());
        }
    }

    /**
     * Executes a dialog action (already validated as coming from a dialog shown to that player).
     * Permissions and zone existence are re-checked here / by the commands.
     */
    public void handleDialogAction(final ServerPlayer player, final Action action, final Map<String, String> inputs) {
        if (!ActionIds.isKnown(action.id())) {
            MessageUtil.send(player, Msg.of("menu_action_invalid"));
            return;
        }
        if (ActionIds.requiresAdmin(action) && !Perms.has(player, Perms.ADMIN)) {
            MessageUtil.send(player, "no_permission");
            return;
        }
        final CommandSourceStack source = player.createCommandSourceStack();
        switch (action.id()) {
            case ActionIds.CLOSE -> {
            }
            case ActionIds.ZONE_CREATE_FORM -> this.openNewZone(source);
            case ActionIds.ZONE_CREATE -> {
                final String name = inputs.getOrDefault("name", "").trim();
                final String shape = inputs.getOrDefault("shape", "circle").trim();
                if (name.isEmpty()) {
                    MessageUtil.send(player, Msg.of("zone_usage_create"));
                    this.openNewZone(source);
                    return;
                }
                this.plugin.getCommandHandler().dispatch(source, new String[]{"zone", "create", name, shape}, true);
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
                this.plugin.getCommandHandler().dispatch(source, args.isEmpty() ? new String[0] : args.split(" "), true);
            }
        }
    }

    private void saveZoneForm(final ServerPlayer player, final Action action, final Map<String, String> inputs) {
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
            MessageUtil.send(player, Msg.of("zone_not_found", "zone", String.valueOf(zoneName)));
            return;
        }
        final UUID editor = editors.editorOf(zone.name());
        if (editor != null && !editor.equals(player.getUUID())) {
            MessageUtil.send(player, Msg.of("zone_error_being_edited", "zone", zone.name(), "player", editors.playerName(editor)));
            return;
        }
        final SetResult<Zone> r = Screens.applyZoneForm(zone, inputs, zones.keyContext());
        if (!r.ok()) {
            MessageUtil.send(player, r.error());
            this.openZoneSettings(player.createCommandSourceStack(), zone, Screens.Context.OVERVIEW);
            return;
        }
        if (!r.value().equals(zone)) {
            zones.saveZone(r.value(), zone.name());
        }
        MessageUtil.send(player, Msg.of("menu_saved"));
        this.openOverview(player.createCommandSourceStack());
    }

    private void saveGlobalForm(final ServerPlayer player, final Map<String, String> inputs) {
        final List<Msg> errors = new ArrayList<>();
        this.plugin.getZoneService().mutateConfig(root -> errors.addAll(Screens.applyGlobalForm(root, inputs)));
        this.plugin.applyGlobalSettings();
        for (final Msg error : errors) {
            MessageUtil.send(player, error);
        }
        if (errors.isEmpty()) {
            MessageUtil.send(player, Msg.of("menu_global_saved"));
        }
        this.openOverview(player.createCommandSourceStack());
    }
}
