package me.cobbleBet;

import me.cobbleBet.players.PlayerWallet;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public final class Main extends JavaPlugin {

    public static boolean testMode = true;

    public static boolean itemEconomy = true;
    public static Material economyItem = Material.DIAMOND;

    public static HashMap<UUID, PlayerWallet> playerWalletHashMap = new HashMap<>();


    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
