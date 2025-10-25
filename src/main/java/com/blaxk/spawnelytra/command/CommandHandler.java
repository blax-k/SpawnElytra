package com.blaxk.spawnelytra.command;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.listener.SpawnElytra;
import com.blaxk.spawnelytra.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final Main plugin;

    public CommandHandler(final Main plugin) {
        this.plugin = plugin;
    }

    private String prettyActivation(final String mode) {
        if (mode == null) return "-";
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "double_jump" -> "Double Jump";
            case "auto" -> "Auto";
            case "sneak_jump" -> "Sneak Jump";
            case "f_key" -> "F Key";
            default -> mode;
        };
    }

    private String prettySpawnMode(final String mode) {
        if (mode == null) return "-";
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "auto" -> "Auto";
            case "advanced" -> "Advanced";
            default -> mode;
        };
    }

    private String prettyLanguage(final String lang) {
        if (lang == null) return "-";
        return switch (lang.toLowerCase(Locale.ROOT)) {
            case "de" -> "Deutsch";
            case "en" -> "English";
            case "es" -> "Español";
            case "fr" -> "Français";
            default -> lang;
        };
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            this.sendHelpMessage(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                this.plugin.reload();
                MessageUtil.send(sender, "reload_success");
                return true;

            case "info":
                this.sendInfoMessage(sender);
                return true;
            
            case "update":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                this.plugin.performAutoUpdate(sender);
                return true;

            case "visualize":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "command_player_only");
                    return true;
                }
                final SpawnElytra elytraInstance = this.plugin.getSpawnElytraInstance();
                if (elytraInstance == null) {
                    MessageUtil.send(sender, "spawnelytra_not_available");
                    return true;
                }
                int seconds = 30;
                if (args.length >= 2) {
                    try {
                        final int parsed = Integer.parseInt(args[1]);
                        if (parsed > 0) {
                            seconds = Math.min(parsed, 600);
                        }
                    } catch (final NumberFormatException ignored) {
                    }
                }
                elytraInstance.visualizeArea((Player) sender, seconds);
                return true;

case "set":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (args.length >= 2 && sender instanceof final Player p) {
                    final String sub = args[1].toLowerCase(Locale.ROOT);
                    if ("pos1".equals(sub)) {
                        this.plugin.getSetupManager().setPosition(p, 1, p.getLocation());
                        return true;
                    } else if ("pos2".equals(sub)) {
                        this.plugin.getSetupManager().setPosition(p, 2, p.getLocation());
                        return true;
                    }
                }
                if (args.length < 3) {
                    return true;
                }
                final String what = args[1].toLowerCase(Locale.ROOT);
                final String value = args[2];
                if ("language".equals(what)) {
                    this.plugin.applyLanguageSetting(sender, value);
                } else if ("style".equals(what)) {
                    this.plugin.applyStyleSetting(sender, value);
                }
                return true;

            case "settings":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "command_player_only");
                    return true;
                }
                this.plugin.sendSettingsMenu((Player) sender);
                return true;

            case "options":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "command_player_only");
                    return true;
                }
                this.plugin.sendOptionsMenu((Player) sender);
                return true;

case "dismiss":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
    this.plugin.markFirstInstallCompleted();
                final Component dismissed = MiniMessage.miniMessage().deserialize(
                        "<#91f251>The first install message will no longer be shown.");
                if (sender instanceof final Player p) {
                    MessageUtil.sendRaw(p, dismissed);
                } else {
                    MessageUtil.sendRaw(sender, dismissed);
                }
                return true;

            case "setup":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (!(sender instanceof final Player pl)) {
                    MessageUtil.send(sender, "command_player_only");
                    return true;
                }
                if (args.length == 1) {
                    
                    if (!this.plugin.getSetupManager().isInSetup(pl)) {
                        this.plugin.markFirstInstallCompleted();
                        this.plugin.getSetupManager().start(pl);
                    } else {
                        this.plugin.getSetupManager().showOptions(pl);
                    }
                    return true;
                }
                final String subSetup = args[1].toLowerCase(Locale.ROOT);
                switch (subSetup) {
                    case "on":
                    case "start":
                        this.plugin.markFirstInstallCompleted();
                        this.plugin.getSetupManager().start(pl);
                        return true;
                    case "off":
                    case "exit":
                    case "cancel":
                        this.plugin.getSetupManager().exit(pl, false);
                        return true;
                    case "save":
                        this.plugin.getSetupManager().save(pl);
                        return true;
                    case "mode":
                        if (args.length >= 3) {
                            this.plugin.getSetupManager().selectActivationMode(pl, args[2]);
                        }
                        return true;
                    case "toggle":
                        if (args.length >= 3) {
                            final String which = args[2].toLowerCase(Locale.ROOT);
                            if ("boost".equals(which)) {
                                this.plugin.getSetupManager().toggleBoostActivatedMessage(pl);
                            } else if ("press".equals(which)) {
                                this.plugin.getSetupManager().togglePressToBoostMessage(pl);
                            }
                        }
                        return true;
                    default:
                        MessageUtil.send(pl, "help_setup");
                        return true;
                }

            case "debug":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    MessageUtil.send(sender, "no_permission");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    MessageUtil.send(sender, "command_player_only");
                    return true;
                }
                if (args.length >= 2) {
                    final String dbgWhat = args[1].toLowerCase(Locale.ROOT);
                    if ("firstinstall".equals(dbgWhat)) {
                        this.plugin.getConfig().set("first_install_completed", false);
                        this.plugin.saveConfig();
                        this.plugin.sendFirstInstallWelcome((Player) sender);
                        return true;
                    }
                }
                return true;

            default:
                this.sendHelpMessage(sender);
                return true;
        }
    }

    private void sendHelpMessage(final CommandSender sender) {
        MessageUtil.send(sender, "help_header");
        MessageUtil.send(sender, "help_reload");
        MessageUtil.send(sender, "help_info");

        if (sender.hasPermission("spawnelytra.admin")) {
            MessageUtil.send(sender, "help_visualize");
            MessageUtil.send(sender, "help_settings");
            MessageUtil.send(sender, "help_setup");
        }
    }

    private void sendInfoMessage(final CommandSender sender) {
        MessageUtil.send(sender, "info_header");

        final String version = this.plugin.getDescription().getVersion();
        final String author = this.plugin.getDescription().getAuthors().isEmpty()
                ? "Unknown"
                : this.plugin.getDescription().getAuthors().get(0);
        final String website = plugin.getDescription().getWebsite() != null
                ? this.plugin.getDescription().getWebsite()
                : "-";
        final List<String> worlds = this.plugin.getConfig().getStringList("worlds");
        final String worldsDisplay = (worlds == null || worlds.isEmpty())
                ? "-"
                : String.join(", ", worlds);
        final int radius = this.plugin.getConfig().getInt("radius");
        final int strength = this.plugin.getConfig().getInt("strength");
        final boolean boostEnabled = this.plugin.getConfig().getBoolean("boost_enabled", true);
        final String activationMode = this.plugin.getConfig().getString("activation_mode", "double_jump");
        final String spawnMode = this.plugin.getConfig().getString("spawn.mode", "auto");
        final String language = this.plugin.getConfig().getString("language", "en");
        final double launchStrength = this.plugin.getConfig().getDouble("f_key_launch_strength", 1.5);

        MessageUtil.send(sender, "info_version", Placeholder.unparsed("value", version));
        
        final Component authorMsg = this.getAuthorMessage(language.toLowerCase(Locale.ROOT), author);
        MessageUtil.sendRaw(sender, authorMsg);
        MessageUtil.send(sender, "info_website", Placeholder.unparsed("value", website));
        MessageUtil.send(sender, "info_world", Placeholder.unparsed("value", worldsDisplay));
        MessageUtil.send(sender, "info_radius", Placeholder.unparsed("value", String.valueOf(radius)));
        MessageUtil.send(sender, "info_strength", Placeholder.unparsed("value", String.valueOf(strength)));
        MessageUtil.send(sender, "info_boost_enabled", Placeholder.unparsed("value", String.valueOf(boostEnabled)));
        MessageUtil.send(sender, "info_activation_mode", Placeholder.unparsed("value", this.prettyActivation(activationMode)));

        if ("f_key".equalsIgnoreCase(activationMode)) {
            MessageUtil.send(sender, "info_offhand_key");
            MessageUtil.send(sender, "info_f_key_launch_strength",
                    Placeholder.unparsed("value", String.valueOf(launchStrength)));
        }

        MessageUtil.send(sender, "info_spawn_mode", Placeholder.unparsed("value", this.prettySpawnMode(spawnMode)));
        MessageUtil.send(sender, "info_language", Placeholder.unparsed("value", this.prettyLanguage(language)));
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>(Arrays.asList("reload", "info"));
            if (sender.hasPermission("spawnelytra.admin")) {
                completions.add("visualize");
                completions.add("settings");
                completions.add("options");
                completions.add("setup");
            }
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return completions.stream()
                    .filter(c -> c.startsWith(prefix))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("setup".equals(sub)) {
                final List<String> second = List.of("exit");
                return second.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
            }
            if ("set".equals(sub) && sender.hasPermission("spawnelytra.admin")) {
                final List<String> second = Arrays.asList("pos1", "pos2");
                return second.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("setup".equals(sub)) {
                if ("mode".equals(args[1].toLowerCase(Locale.ROOT))) {
                    final List<String> modes = Arrays.asList("double_jump", "auto", "sneak_jump", "f_key");
                    return modes.stream().filter(m -> m.startsWith(prefix)).collect(Collectors.toList());
                }
                if ("toggle".equals(args[1].toLowerCase(Locale.ROOT))) {
                    final List<String> toggles = Arrays.asList("boost", "press");
                    return toggles.stream().filter(t -> t.startsWith(prefix)).collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
    
    private Component getAuthorMessage(final String language, final String author) {
        final String style = this.plugin.getConfig().getString("messages.style", "classic").toLowerCase(Locale.ROOT);
        
        String text = switch (language) {
            case "de" -> "<#fdba5e>Autor: <#91f251>" + author + "</#91f251>";
            case "es" -> "<#fdba5e>Autor: <#91f251>" + author + "</#91f251>";
            case "fr" -> "<#fdba5e>Auteur: <#91f251>" + author + "</#91f251>";
            default -> "<#fdba5e>Author: <#91f251>" + author + "</#91f251>";
        };
        
        if ("small_caps".equals(style) && ("en".equals(language) || "de".equals(language))) {
            text = this.applySmallCaps(text);
        }
        
        return MiniMessage.miniMessage().deserialize(text);
    }
    
    private String applySmallCaps(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        final StringBuilder out = new StringBuilder(input.length());
        boolean inTag = false;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            final char ch = input.charAt(i);
            if ('<' == ch) {
                inTag = true;
                depth++;
                out.append('<');
                continue;
            }
            if ('>' == ch && inTag) {
                out.append('>');
                depth--;
                if (depth <= 0) {
                    inTag = false;
                    depth = 0;
                }
                continue;
            }
            if (inTag) {
                out.append(ch);
            } else {
                final char lower = Character.toLowerCase(ch);
                final String mapped = this.getSmallCapsMapping(lower);
                if (mapped != null) {
                    out.append(mapped);
                } else {
                    out.append(ch);
                }
            }
        }
        return out.toString();
    }
    
    private String getSmallCapsMapping(final char ch) {
        return switch (ch) {
            case 'a' -> "ᴀ";
            case 'b' -> "ʙ";
            case 'c' -> "ᴄ";
            case 'd' -> "ᴅ";
            case 'e' -> "ᴇ";
            case 'f' -> "ꜰ";
            case 'g' -> "ɢ";
            case 'h' -> "ʜ";
            case 'i' -> "ɪ";
            case 'j' -> "ᴊ";
            case 'k' -> "ᴋ";
            case 'l' -> "ʟ";
            case 'm' -> "ᴍ";
            case 'n' -> "ɴ";
            case 'o' -> "ᴏ";
            case 'p' -> "ᴘ";
            case 'q' -> "ƫ";
            case 'r' -> "ʀ";
            case 's' -> "ꜱ";
            case 't' -> "ᴛ";
            case 'u' -> "ᴜ";
            case 'v' -> "ᴠ";
            case 'w' -> "ᴡ";
            case 'x' -> "ˣ";
            case 'y' -> "ʏ";
            case 'z' -> "ᴢ";
            case 'ä' -> "ä";
            case 'ö' -> "ö";
            case 'ü' -> "ü";
            case 'ß' -> "ꜱꜱ";
            default -> null;
        };
    }
}

