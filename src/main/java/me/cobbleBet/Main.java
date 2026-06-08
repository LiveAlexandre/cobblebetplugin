package me.cobbleBet;

import me.cobbleBet.commands.GambleCommand;
import me.cobbleBet.commands.WalletCommand;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.players.PlayerWallet;
import me.cobbleBet.storage.PlayerWalletStorage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.UUID;

public final class Main extends JavaPlugin {

    private static Main instance;
    public static boolean testMode = true;

    public static String economyType = "item";
    public static Material economyItem = Material.DIAMOND;
    public static HashMap<UUID, PlayerWallet> playerWalletHashMap = new HashMap<>();

    private PlayerWalletStorage playerWalletStorage;

    private EconomyManager economyManager = new EconomyManager(this);

    public CobbleSocketClient cobbleSocketClient;

    @Override
    public void onEnable() {
        instance=this;
        try {
            cobbleSocketClient = new CobbleSocketClient();
            cobbleSocketClient.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        if(this.getConfig().contains("economyType"))
            Main.economyType = this.getConfig().getString("economyType");
        if(this.getConfig().contains("economyItem"))
            Main.economyItem = Material.valueOf(this.getConfig().getString("economyItem"));



        playerWalletStorage = new PlayerWalletStorage(getDataFolder());
        playerWalletStorage.loadAll();

        this.getCommand("gamble").setExecutor(new GambleCommand());

        getCommand("wallet").setExecutor(new WalletCommand());
        getCommand("wallet").setTabCompleter(new WalletCommand());


    }

    @Override
    public void onDisable() {
        playerWalletStorage.saveAll();
    }

    public static Main getInstance() {
        return instance;
    }
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

}
