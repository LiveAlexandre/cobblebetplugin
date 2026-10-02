package me.cobbleBet.services;

import me.cobbleBet.Main;
import me.cobbleBet.commands.CommandRegistrar;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.events.mysteriousGambler.WanderingTraderTimer;
import me.cobbleBet.events.mysteriousGambler.WanderingGamblerService;
import me.cobbleBet.gui.BlackjackController;
import me.cobbleBet.gui.CobbleMenuController;
import me.cobbleBet.gui.CoinflipController;
import me.cobbleBet.gui.MinesController;
import me.cobbleBet.gui.PlinkoController;
import me.cobbleBet.gui.RouletteController;
import me.cobbleBet.listeners.GamblingPageActivityListener;
import me.cobbleBet.listeners.GambleServerLinkListener;
import me.cobbleBet.visuals.BlackjackTableManager;
import me.cobbleBet.visuals.CoinflipBoardManager;
import me.cobbleBet.visuals.GamblingIndicatorManager;
import me.cobbleBet.visuals.MinesFieldManager;
import me.cobbleBet.visuals.PlinkoBoardManager;
import me.cobbleBet.visuals.RouletteTableManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public final class PluginRuntime {
    private PluginRuntime() {}

    public static EconomyManager initialize(Main plugin) {
        EconomyManager economyManager = new EconomyManager(plugin);
        plugin.gamblingIndicatorManager = new GamblingIndicatorManager(plugin);
        plugin.menuController = new CobbleMenuController(plugin);
        plugin.coinflipController = new CoinflipController(plugin);
        plugin.coinflipBoardManager = new CoinflipBoardManager(plugin);
        plugin.blackjackController = new BlackjackController(plugin);
        plugin.blackjackTableManager = new BlackjackTableManager(plugin);
        plugin.minesController = new MinesController(plugin);
        plugin.minesFieldManager = new MinesFieldManager(plugin);
        plugin.plinkoController = new PlinkoController(plugin);
        plugin.plinkoBoardManager = new PlinkoBoardManager(plugin);
        plugin.rouletteController = new RouletteController(plugin);
        plugin.rouletteTableManager = new RouletteTableManager(plugin);

        CommandRegistrar.register(plugin);
        register(plugin, plugin.menuController);
        register(plugin, plugin.coinflipController);
        register(plugin, plugin.coinflipBoardManager);
        register(plugin, plugin.blackjackController);
        register(plugin, plugin.blackjackTableManager);
        register(plugin, plugin.minesController);
        register(plugin, plugin.minesFieldManager);
        register(plugin, plugin.plinkoController);
        register(plugin, plugin.plinkoBoardManager);
        register(plugin, plugin.rouletteController);
        register(plugin, plugin.rouletteTableManager);
        register(plugin, new GamblingPageActivityListener(plugin.gamblingIndicatorManager));
        register(plugin, new GambleServerLinkListener());

        plugin.coinflipBoardManager.load();
        plugin.blackjackTableManager.load();
        plugin.minesFieldManager.load();
        plugin.plinkoBoardManager.load();
        plugin.rouletteTableManager.load();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.coinflipController.requestLobby(), 20L * 15L, 20L * 15L);
        new WanderingTraderTimer(plugin).runTaskTimer(plugin, 600L, 600L);
        return economyManager;
    }

    public static void shutdown(Main plugin) {
        WanderingGamblerService.stopAll();
        if (plugin.gamblingIndicatorManager != null) plugin.gamblingIndicatorManager.clearAll();
        if (plugin.coinflipBoardManager != null) plugin.coinflipBoardManager.clearAll();
        if (plugin.blackjackTableManager != null) plugin.blackjackTableManager.clearAll();
        if (plugin.minesFieldManager != null) plugin.minesFieldManager.clearAll();
        if (plugin.plinkoBoardManager != null) plugin.plinkoBoardManager.clearAll();
        if (plugin.rouletteTableManager != null) plugin.rouletteTableManager.clearAll();
    }

    private static void register(Main plugin, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
