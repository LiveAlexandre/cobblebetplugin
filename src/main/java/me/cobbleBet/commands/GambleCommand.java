package me.cobbleBet.commands;

import me.cobbleBet.Main;
import me.cobbleBet.api.API;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GambleCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (Main.isPermissionRequired("gamble") && !sender.hasPermission("cobblebet.gamble")) {
            sender.sendMessage("You do not have permission (cobblebet.gamble).");
            return true;
        }
        if (!(sender instanceof Player player)) return true;

        Main main = Main.getInstance();
        if (main.cobbleSocketClient == null || !main.cobbleSocketClient.isApproved()) {
            player.sendMessage(Component.text("CobbleBet Plugin is not connected to the CobbleBet server.", NamedTextColor.RED));
            return true;
        }

        try {
            API.generateAndRegisterPlayerToken(player);
            player.sendMessage(Component.text("Generating login token...", NamedTextColor.BLUE));
        } catch (Exception ignored) {
            if (main.gamblingIndicatorManager != null) {
                main.gamblingIndicatorManager.stopGambling(player);
            }
            player.sendMessage(Component.text("Could not connect to CobbleBet. Please try again.", NamedTextColor.RED));
        }
        return true;
    }
}
