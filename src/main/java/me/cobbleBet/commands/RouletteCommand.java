package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class RouletteCommand implements CommandExecutor {
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cRoulette is available in-game only."); return true; }
        if (Main.isPermissionRequired("gamble") && !player.hasPermission("cobblebet.gamble")) { player.sendMessage("§cYou do not have permission to play Roulette."); return true; }
        Main.getInstance().rouletteController.open(player);
        return true;
    }
}
