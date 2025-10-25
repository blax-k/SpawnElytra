package com.blaxk.spawnelytra.setup;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SetupManager implements Listener {
    private final Main plugin;
    private final Map<UUID, SetupSession> sessions = new HashMap<>();

    public SetupManager(final Main plugin) {
        this.plugin = plugin;
    }

    public boolean isInSetup(final Player player) {
        return this.sessions.containsKey(player.getUniqueId());
    }

    public void start(final Player player) {
        if (this.isInSetup(player)) {
            MessageUtil.send(player, "setup_already_running");
            return;
        }
        final SetupSession session = new SetupSession(this.plugin, player);
        this.sessions.put(player.getUniqueId(), session);
        session.begin();
    }

    public void exit(final Player player, final boolean silent) {
        final SetupSession session = this.sessions.remove(player.getUniqueId());
        if (session != null) {
            session.end();
            if (!silent) {
                MessageUtil.send(player, "setup_cancelled");
            }
        } else if (!silent) {
            MessageUtil.send(player, "setup_not_running");
        }
    }

    public void setPosition(final Player player, final int index, final Location location) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "setup_not_running");
            return;
        }
        if (index == 1) {
            session.setPos1(location);
        } else if (index == 2) {
            session.setPos2(location);
        }
    }

    public void selectActivationMode(final Player player, final String mode) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "setup_not_running");
            return;
        }
        final String normalized = mode.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "double_jump":
            case "auto":
            case "sneak_jump":
            case "f_key":
                session.setActivationMode(normalized);
                MessageUtil.send(player, "setup_activation_mode_set", Placeholder.unparsed("value", this.prettyActivation(normalized)));
                break;
            default:
                MessageUtil.send(player, "setup_invalid_mode");
        }
    }

    public void toggleBoostActivatedMessage(final Player player) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "setup_not_running");
            return;
        }
        final boolean newValue = !session.isShowBoostActivated();
        session.setShowBoostActivated(newValue);
        final String valueLocalized = MessageUtil.plain(newValue ? "state_on" : "state_off");
        MessageUtil.send(player, "setup_toggled_boost_activated", Placeholder.unparsed("value", valueLocalized));
    }

    public void togglePressToBoostMessage(final Player player) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "setup_not_running");
            return;
        }
        final boolean newValue = !session.isShowPressToBoost();
        session.setShowPressToBoost(newValue);
        final String valueLocalized = MessageUtil.plain(newValue ? "state_on" : "state_off");
        MessageUtil.send(player, "setup_toggled_press_to_boost", Placeholder.unparsed("value", valueLocalized));
    }

    public void save(final Player player) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "setup_not_running");
            return;
        }
        if (!session.hasBothPositions()) {
            MessageUtil.send(player, "setup_missing_positions");
            return;
        }

        final Location p1 = session.getPos1();
        final Location p2 = session.getPos2();
        final String worldName = Objects.requireNonNull(p1.getWorld()).getName();
        final String path = "worlds." + worldName;

        
        final ConfigurationSection worldSec = this.plugin.getConfig().getConfigurationSection(path);
        if (worldSec == null) {
            this.plugin.getConfig().createSection(path);
        }

        this.plugin.getConfig().set(path + ".enabled", true);
        this.plugin.getConfig().set(path + ".spawn_area.mode", "advanced");
        this.plugin.getConfig().set(path + ".spawn_area.area_type", "rectangular");
        this.plugin.getConfig().set(path + ".spawn_area.x", p1.getX());
        this.plugin.getConfig().set(path + ".spawn_area.y", p1.getY());
        this.plugin.getConfig().set(path + ".spawn_area.z", p1.getZ());
        this.plugin.getConfig().set(path + ".spawn_area.x2", p2.getX());
        this.plugin.getConfig().set(path + ".spawn_area.y2", p2.getY());
        this.plugin.getConfig().set(path + ".spawn_area.z2", p2.getZ());

        
        if (session.getActivationMode() != null) {
            this.plugin.getConfig().set(path + ".activation_mode", session.getActivationMode());
        }

        this.plugin.getConfig().set("messages.show_boost_activated", session.isShowBoostActivated());
        this.plugin.getConfig().set("messages.show_press_to_boost", session.isShowPressToBoost());

        this.plugin.saveConfig();

        
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        MessageUtil.send(player, "setup_saved");
        SchedulerUtil.runAtEntityLater(this.plugin, player, 2L, this.plugin::reload);
    }

    public void showOptions(final Player player) {
        final SetupSession session = this.sessions.get(player.getUniqueId());
        if (session == null || !session.hasBothPositions()) {
            return;
        }
        
        MessageUtil.send(player, "setup_options_header");

        
        String current = session.getActivationMode();
        if (current == null) {
            final String worldName = player.getWorld().getName();
            current = this.plugin.getConfig().getString("worlds." + worldName + ".activation_mode", "double_jump");
        }
        final String text = "<#91f251>" +
                this.option("/spawnelytra setup mode double_jump", this.prettyActivation("double_jump"), "activation_mode_hover_double_jump", "double_jump".equalsIgnoreCase(current)) + " " +
                this.option("/spawnelytra setup mode auto", this.prettyActivation("auto"), "activation_mode_hover_auto", "auto".equalsIgnoreCase(current)) + " " +
                this.option("/spawnelytra setup mode sneak_jump", this.prettyActivation("sneak_jump"), "activation_mode_hover_sneak_jump", "sneak_jump".equalsIgnoreCase(current)) + " " +
                this.option("/spawnelytra setup mode f_key", this.prettyActivation("f_key"), "activation_mode_hover_f_key", "f_key".equalsIgnoreCase(current));
        MessageUtil.sendRaw(player, MiniMessage.miniMessage().deserialize(text));

        
        final boolean showBoost = session.isShowBoostActivated();
        final boolean showPress = session.isShowPressToBoost();
        final String boostLabel = MessageUtil.plain("setup_toggle_boost_label");
        final String pressLabel = MessageUtil.plain("setup_toggle_press_label");
        final String toggles = "<#5db3ff>" +
                this.labeledToggle(boostLabel, "/spawnelytra setup toggle boost", showBoost) + " " +
                this.labeledToggle(pressLabel, "/spawnelytra setup toggle press", showPress);
        MessageUtil.sendRaw(player, MiniMessage.miniMessage().deserialize(toggles));

        
        final String saveLabel = MessageUtil.plain("setup_actions_save_label");
        final String saveHover = MessageUtil.plain("setup_actions_save_hover");
        final String exitLabel = MessageUtil.plain("setup_actions_exit_label");
        final String exitHover = MessageUtil.plain("setup_actions_exit_hover");
        final String actions = "<#ffd166>[<click:run_command:'/spawnelytra setup save'><hover:show_text:'<#ffd166>" + saveHover + "'>" + saveLabel + "</hover></click>] " +
                "<#aaa8a8>[<click:run_command:'/spawnelytra setup exit'><hover:show_text:'<#fd5e5e>" + exitHover + "'>" + exitLabel + "</hover></click>]";
        MessageUtil.sendRaw(player, MiniMessage.miniMessage().deserialize(actions));
    }

    private String option(final String cmd, final String label, final String hoverKey, final boolean selected) {
        final String hoverText = MessageUtil.plain(hoverKey);
        final String open = "[<click:run_command:'" + cmd + "'><hover:show_text:'" + hoverText + "'>";
        final String core = selected ? ("<underlined>" + label + "</underlined>") : label;
        return open + core + "</hover></click>]";
    }

    private String labeledToggle(final String label, final String cmd, final boolean on) {
        final String onLabel = MessageUtil.plain("state_on");
        final String offLabel = MessageUtil.plain("state_off");
        final String toggleHover = MessageUtil.plain("toggle_hover");
        final String state = on ? ("<#91f251>" + onLabel) : ("<#fd5e5e>" + offLabel);
        return label + ": [<click:run_command:'" + cmd + "'><hover:show_text:'<#5db3ff>" + toggleHover + "'>" + state + "</hover></click>]";
    }

    private String prettyActivation(final String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "double_jump" -> "Double Jump";
            case "auto" -> "Auto";
            case "sneak_jump" -> "Sneak Jump";
            case "f_key" -> "F Key";
            default -> mode;
        };
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        final Player p = e.getPlayer();
        final SetupSession session = this.sessions.remove(p.getUniqueId());
        if (session != null) {
            session.end();
        }
    }

    public void stopAll() {
        for (final Player p : Bukkit.getOnlinePlayers()) {
            final SetupSession session = this.sessions.remove(p.getUniqueId());
            if (session != null) session.end();
        }
    }
}

