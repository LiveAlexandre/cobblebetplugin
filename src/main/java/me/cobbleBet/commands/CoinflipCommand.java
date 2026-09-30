package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CoinflipCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCoinflip is available in-game only.");
            return true;
        }
        if (Main.isPermissionRequired("gamble") && !player.hasPermission("cobblebet.gamble")) {
            player.sendMessage("§cYou do not have permission to play Coinflip.");
            return true;
        }
        var controller = Main.getInstance().coinflipController;
        if (args.length == 0) {
            controller.openLobby(player, 0);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length == 1) controller.openCreateMenu(player);
                else controller.create(player, args[1]);
            }
            case "join" -> {
                if (args.length < 2) player.sendMessage("§eUsage: /coinflip join <id>");
                else controller.confirm(player, args[1], false);
            }
            case "cancel" -> {
                if (args.length < 2) player.sendMessage("§eUsage: /coinflip cancel <id>");
                else controller.confirm(player, args[1], true);
            }
            case "refresh" -> controller.openLobby(player, 0);
            default -> player.sendMessage("§eUsage: /coinflip [create <amount>|join <id>|cancel <id>|refresh]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("create", "join", "cancel", "refresh");
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) return List.of("10", "100", "1000");
        return new ArrayList<>();
    }
}
