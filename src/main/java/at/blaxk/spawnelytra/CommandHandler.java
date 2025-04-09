package at.blaxk.spawnelytra;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final main plugin;

    public CommandHandler(main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("spawnelytra.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "SpawnElytra configuration reloaded.");
                return true;

            case "info":
                sendInfoMessage(sender);
                return true;

            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Spawn Elytra Help");
        sender.sendMessage(ChatColor.YELLOW + "/spawnelytra reload " + ChatColor.WHITE + "- Reload the plugin configuration");
        sender.sendMessage(ChatColor.YELLOW + "/spawnelytra info " + ChatColor.WHITE + "- Show plugin information");
    }

    private void sendInfoMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Spawn Elytra Config");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + plugin.getDescription().getAuthors().get(0));
        sender.sendMessage(ChatColor.YELLOW + "Website: " + ChatColor.WHITE + plugin.getDescription().getWebsite());

        sender.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + plugin.getConfig().getString("world"));
        sender.sendMessage(ChatColor.YELLOW + "Radius: " + ChatColor.WHITE + plugin.getConfig().getInt("radius"));
        sender.sendMessage(ChatColor.YELLOW + "Boost Strength: " + ChatColor.WHITE + plugin.getConfig().getInt("strength"));
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + plugin.getConfig().getString("mode"));
        sender.sendMessage(ChatColor.YELLOW + "Language: " + ChatColor.WHITE + plugin.getConfig().getString("language"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("reload", "info"));

            return completions.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}