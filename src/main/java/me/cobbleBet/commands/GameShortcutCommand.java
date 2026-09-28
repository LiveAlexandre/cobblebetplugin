package me.cobbleBet.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class GameShortcutCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String gameName = switch (label.toLowerCase(Locale.ROOT)) {
            case "cf", "coinflip" -> "Coinflip";
            case "bj", "blackjack" -> "Blackjack";
            case "roulette" -> "Roulette";
            case "plinko" -> "Plinko";
            default -> "Mines";
        };

        Component gambleButton = Component.text("/gamble", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/gamble"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open CobbleBet!", NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("Ready for " + gameName + "? ", NamedTextColor.WHITE))
                .append(Component.text("Use ", NamedTextColor.GRAY))
                .append(gambleButton)
                .append(Component.text(" to play! ♡", NamedTextColor.GRAY)));
        return true;
    }
}
