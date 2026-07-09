package me.cobbleBet.commands;

import me.cobbleBet.api.API;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GambleCommand implements CommandExecutor {


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if(sender.hasPermission("Cobblebet.gamble")) {
            if(sender instanceof Player player) {
                try {
                    API.generateAndRegisterPlayerToken(player);
                    player.sendMessage(Component.text("Generating login token...", NamedTextColor.BLUE));
                }
                catch (Exception ignored) {
                    player.sendMessage(Component.text("CobbleBet Plugin is not connected to the CobbleBet server.", NamedTextColor.RED));
                }
            }

        }


        return false;
    }
}
