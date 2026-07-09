package me.cobbleBet;

import me.cobbleBet.commands.CobbleBetCommand;
import me.cobbleBet.commands.GambleCommand;
import me.cobbleBet.commands.WalletCommand;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.players.PlayerWallet;
import me.cobbleBet.storage.PlayerWalletStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.UUID;

public final class Main extends JavaPlugin {

    private static Main instance;

    public static double cobblebetPluginVersion = 1.1;


    // =========================
    // ECONOMY
    // =========================
    public static String economyType;
    public static Material economyItem;
    public static String vaultCurrencyName;

    public static long maximumBalance;

    // =========================
    // PERMISSIONS
    // =========================

    public static boolean gambleCommandNeedsPermission;

    // =========================
    // PREMIUM
    // =========================
    public static String cobblebetToken;
    public static boolean premiumEnabled;

    // =========================
    // BROADCAST
    // =========================
    public static boolean broadcastingEnabled;
    public static String broadcastPrefix;

    public static HashMap<String, Boolean> broadcastEvents = new HashMap<>();

    public static long bigWinThreshold;

    // =========================
    // DEBUG
    // =========================
    public static boolean testMode;

    // =========================
    // RUNTIME
    // =========================
    public static HashMap<UUID, PlayerWallet> playerWalletHashMap = new HashMap<>();

    private PlayerWalletStorage playerWalletStorage;
    private EconomyManager economyManager;
    public CobbleSocketClient cobbleSocketClient;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfigValues();

        loadStorage();
        economyManager = new EconomyManager(this);
        registerCommands();
        connectSocket();

        getLogger().info("CobbleBet loaded successfully.");
    }

    @Override
    public void onDisable() {
        if (playerWalletStorage != null) {
            playerWalletStorage.saveAll();
        }
    }

    // =========================
    // CONFIG LOADER
    // =========================
    public static void loadConfigValues() {

        // ECONOMY
        economyType = Main.getInstance().getConfig().getString("economyType", "item");

        String itemName = Main.getInstance().getConfig().getString("economyItem", "DIAMOND");
        try {
            economyItem = Material.valueOf(itemName.toUpperCase());
        } catch (Exception e) {
            economyItem = Material.DIAMOND;
            Bukkit.getLogger().warning("Invalid economyItem, defaulting to DIAMOND");
        }

        gambleCommandNeedsPermission = Main.getInstance().getConfig().getBoolean("gambleCommandNeedsPermission", false);

        vaultCurrencyName = Main.getInstance().getConfig().getString("vaultCurrencyName", "Coins");
        maximumBalance = Main.getInstance().getConfig().getLong("maximumBalance", 1000000000L);

        // PREMIUM
        cobblebetToken = Main.getInstance().getConfig().getString("cobblebetToken", "");
        premiumEnabled = Main.getInstance().getConfig().getBoolean("premiumEnabled", false);

        // BROADCAST SETTINGS
        broadcastingEnabled = Main.getInstance().getConfig().getBoolean("broadcastingEnabled", true);
        broadcastPrefix = Main.getInstance().getConfig().getString(
                "broadcastPrefix",
                "<blue><bold>CobbleBet</bold> »"
        );

        bigWinThreshold = Main.getInstance().getConfig().getLong("bigWinThreshold", 500);

        // =========================
        // LOAD BROADCAST EVENTS MAP
        // =========================
        broadcastEvents.clear();

        if (Main.getInstance().getConfig().isConfigurationSection("broadcastEvents")) {
            for (String key : Main.getInstance().getConfig()
                    .getConfigurationSection("broadcastEvents")
                    .getKeys(false)) {

                boolean enabled = Main.getInstance().getConfig().getBoolean("broadcastEvents." + key);
                broadcastEvents.put(key, enabled);
            }
        }

        // DEBUG
        testMode = Main.getInstance().getConfig().getBoolean("debug.testMode", false);

        // LOG
        Bukkit.getLogger().info("=== CobbleBet Config Loaded ===");
        Bukkit.getLogger().info("Economy: " + economyType);
        Bukkit.getLogger().info("Item: " + economyItem);
        Bukkit.getLogger().info("Vault Currency: " + vaultCurrencyName);
        Bukkit.getLogger().info("Max Balance: " + maximumBalance);
        Bukkit.getLogger().info("Premium: " + premiumEnabled);
        Bukkit.getLogger().info("Broadcasting: " + broadcastingEnabled);
        Bukkit.getLogger().info("Broadcast Events: " + broadcastEvents);
        Bukkit.getLogger().info("Test Mode: " + testMode);
    }

    // =========================
    // SOCKET
    // =========================
    private void connectSocket() {
        try {
            cobbleSocketClient = new CobbleSocketClient();
            cobbleSocketClient.connect();
        } catch (URISyntaxException e) {
            getLogger().severe("Socket connection failed: " + e.getMessage());
        }
    }

    // =========================
    // STORAGE
    // =========================
    private void loadStorage() {
        playerWalletStorage = new PlayerWalletStorage(getDataFolder());
        playerWalletStorage.loadAll();
    }

    // =========================
    // COMMANDS
    // =========================
    private void registerCommands() {
        getCommand("gamble").setExecutor(new GambleCommand());

        WalletCommand wallet = new WalletCommand();
        getCommand("wallet").setExecutor(wallet);
        getCommand("wallet").setTabCompleter(wallet);

        CobbleBetCommand cobblebet = new CobbleBetCommand();
        getCommand("cobblebet").setExecutor(cobblebet);
        getCommand("cobblebet").setTabCompleter(cobblebet);
    }

    // =========================
    // WALLET
    // =========================
    public static PlayerWallet getWallet(UUID uuid) {
        return playerWalletHashMap.computeIfAbsent(uuid, PlayerWallet::new);
    }

    public static Main getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
