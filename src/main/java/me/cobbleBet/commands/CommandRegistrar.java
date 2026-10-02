package me.cobbleBet.commands;

import me.cobbleBet.Main;
import org.bukkit.command.PluginCommand;

public final class CommandRegistrar {
    private CommandRegistrar() {}

    public static void register(Main plugin) {
        require(plugin, "gamble").setExecutor(new GambleCommand());

        MinesCommand mines = new MinesCommand();
        require(plugin, "mines").setExecutor(mines);
        require(plugin, "mines").setTabCompleter(mines);

        BlackjackCommand blackjack = new BlackjackCommand();
        require(plugin, "blackjack").setExecutor(blackjack);
        require(plugin, "blackjack").setTabCompleter(blackjack);

        require(plugin, "roulette").setExecutor(new RouletteCommand());

        CoinflipCommand coinflip = new CoinflipCommand();
        require(plugin, "coinflip").setExecutor(coinflip);
        require(plugin, "coinflip").setTabCompleter(coinflip);

        require(plugin, "plinko").setExecutor(new PlinkoCommand());
        GameShortcutCommand gameShortcut = new GameShortcutCommand();
        require(plugin, "dice").setExecutor(gameShortcut);
        require(plugin, "crash").setExecutor(gameShortcut);

        WalletCommand wallet = new WalletCommand();
        require(plugin, "wallet").setExecutor(wallet);
        require(plugin, "wallet").setTabCompleter(wallet);

        CobbleBetCommand cobblebet = new CobbleBetCommand();
        require(plugin, "cobblebet").setExecutor(cobblebet);
        require(plugin, "cobblebet").setTabCompleter(cobblebet);
    }

    private static PluginCommand require(Main plugin, String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) throw new IllegalStateException("Command is missing from plugin.yml: " + name);
        return command;
    }
}
