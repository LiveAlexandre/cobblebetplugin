package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CobbleBetCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("cobblebet.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUsage: /cobblebet reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {

            long start = System.currentTimeMillis();

            try {
                Main.loadConfigValues(); // basic reload

                long time = System.currentTimeMillis() - start;
                sender.sendMessage("§aCobbleBet config reloaded successfully in §f" + time + "ms§a.");

            } catch (Exception e) {
                sender.sendMessage("§cFailed to reload CobbleBet.");
                e.printStackTrace();
            }

            return true;
        }

        sender.sendMessage("§cUnknown subcommand. Use: reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("cobblebet.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.add("reload");
        }

        return completions;
    }
}