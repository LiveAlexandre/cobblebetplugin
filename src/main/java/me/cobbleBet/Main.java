package me.cobbleBet;

import me.cobbleBet.commands.CobbleBetCommand;
import me.cobbleBet.commands.GambleCommand;
import me.cobbleBet.commands.GameShortcutCommand;
import me.cobbleBet.commands.WalletCommand;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.players.PlayerWallet;
import me.cobbleBet.storage.PlayerWalletStorage;
import me.cobbleBet.visuals.GamblingIndicatorManager;
import me.cobbleBet.listeners.PluginUpdateListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    public static String serverDisplayName;

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
    public final ConcurrentHashMap<UUID, Long> pendingAdminPanelRequests = new ConcurrentHashMap<>();
    public GamblingIndicatorManager gamblingIndicatorManager;
    private volatile String officialPluginVersion = "";
    private volatile String officialPluginJarName = "CobbleBet.jar";

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadConfigValues();
        me.cobbleBet.storage.WebsiteSettingsStore.load(this);

        loadStorage();
        economyManager = new EconomyManager(this);
        gamblingIndicatorManager = new GamblingIndicatorManager(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(new PluginUpdateListener(this), this);
        getServer().getPluginManager().registerEvents(new me.cobbleBet.listeners.GamblingPageActivityListener(gamblingIndicatorManager), this);
        connectSocket();
        startStatusUpdates();

        getLogger().info("CobbleBet loaded successfully.");
    }

    @Override
    public void saveConfig() {
        me.cobbleBet.storage.WebsiteSettingsStore.moveToBottom(getConfig());
        super.saveConfig();
    }

    @Override
    public void onDisable() {
        if (cobbleSocketClient != null) cobbleSocketClient.shutdown();
        if (playerWalletStorage != null) {
            playerWalletStorage.saveAll();
        }
        if (gamblingIndicatorManager != null) {
            gamblingIndicatorManager.clearAll();
        }
    }

    // =========================
    // CONFIG LOADER
    // =========================
    public static void loadConfigValues() {

        // ECONOMY
        economyType = Main.getInstance().getConfig().getString("economyType", "vault");

        String itemName = Main.getInstance().getConfig().getString("economyItem", "DIAMOND");
        try {
            economyItem = Material.valueOf(itemName.toUpperCase());
        } catch (Exception e) {
            economyItem = Material.DIAMOND;
            Bukkit.getLogger().warning("Invalid economyItem, defaulting to DIAMOND");
        }

        gambleCommandNeedsPermission = Main.getInstance().getConfig().getBoolean("gambleCommandNeedsPermission", false);

        vaultCurrencyName = Main.getInstance().getConfig().getString("vaultCurrencyName", "");
        maximumBalance = Main.getInstance().getConfig().getLong("maximumBalance", 1000000000L);

        // PREMIUM
        cobblebetToken = Main.getInstance().getConfig().getString("cobblebetToken", "");
        serverDisplayName = Main.getInstance().getConfig().getString("serverName", "").trim();
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

        if (Main.getInstance().gamblingIndicatorManager != null) {
            Main.getInstance().gamblingIndicatorManager.reloadSettings();
        }

        if (Main.getInstance().economyManager != null) {
            Main.getInstance().economyManager.refreshVaultCurrencyName();
        }

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

    public void reconnectSocket() {
        if (cobbleSocketClient != null) cobbleSocketClient.shutdown();
        connectSocket();
    }

    public void setOfficialPluginRelease(String version, String jarName) {
        if (version != null && !version.isBlank()) officialPluginVersion = version.trim();
        if (jarName != null && !jarName.isBlank()) officialPluginJarName = jarName.trim();
    }

    public void notifyAdminAboutPluginUpdate(Player player) {
        if (player == null || !player.isOnline() || !player.hasPermission("cobblebet.admin")) return;
        String installedVersion = getDescription().getVersion();
        String latestVersion = officialPluginVersion;
        if (latestVersion.isBlank() || !isOlderVersion(installedVersion, latestVersion)) return;
        player.sendMessage("§5[CobbleBet] §dPlugin update available: §f" + installedVersion + " §7→ §f" + latestVersion
                + "§d. Download §f" + officialPluginJarName + " §dfrom §fhttps://cobblebet.com/api/download-plugin§d, replace the JAR, then restart.");
    }

    private static boolean isOlderVersion(String installed, String latest) {
        String[] installedParts = installed.split("[.+-]", 4);
        String[] latestParts = latest.split("[.+-]", 4);
        for (int index = 0; index < 3; index++) {
            int installedPart = parseVersionPart(installedParts, index);
            int latestPart = parseVersionPart(latestParts, index);
            if (installedPart != latestPart) return installedPart < latestPart;
        }
        return installed.contains("-") && !latest.contains("-");
    }

    private static int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try { return Integer.parseInt(parts[index].replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    // =========================
    private void startStatusUpdates() {
        Bukkit.getScheduler().runTaskTimer(this, this::sendServerStatus, 20L, 600L);
    }

    public void sendServerStatus() {
        if (cobbleSocketClient == null || !cobbleSocketClient.isApproved()) return;
        JsonObject status = new JsonObject();
        status.addProperty("type", "serverStatus");
        status.addProperty("serverName", serverDisplayName == null || serverDisplayName.isBlank() ? Bukkit.getMotd() : serverDisplayName);
        status.addProperty("pluginName", "CobbleBet");
        status.addProperty("pluginVersion", getDescription().getVersion());
        status.addProperty("economyType", economyType);
        status.addProperty("economyItem", economyItem == null ? "" : economyItem.name());
        status.addProperty("currencyName", economyType.equalsIgnoreCase("vault") ? vaultCurrencyName : (economyItem == null ? "Coins" : economyItem.name()));
        status.add("permissionRequirements", getPermissionRequirements());
        status.addProperty("broadcastingEnabled", broadcastingEnabled && broadcastEvents.getOrDefault("bigWin", false));
        status.addProperty("bigWinThreshold", bigWinThreshold);
        JsonArray players = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.getUniqueId().toString());
            entry.addProperty("name", player.getName());
            players.add(entry);
        }
        status.add("onlinePlayers", players);
        cobbleSocketClient.send(status.toString());
    }

    public String readServerIconDataUrl() {
        try {
            File icon = new File("server-icon.png");
            if (!icon.isFile() || icon.length() > 70_000) return "";
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(icon.toPath()));
        } catch (Exception ignored) {
            return "";
        }
    }
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

        GameShortcutCommand gameShortcut = new GameShortcutCommand();
        getCommand("mines").setExecutor(gameShortcut);
        getCommand("blackjack").setExecutor(gameShortcut);
        getCommand("roulette").setExecutor(gameShortcut);
        getCommand("coinflip").setExecutor(gameShortcut);

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

    public static boolean isPermissionRequired(String key) {
        if (instance == null) return true;
        boolean fallback = "gamble".equals(key)
                ? instance.getConfig().getBoolean("gambleCommandNeedsPermission", false)
                : true;
        return instance.getConfig().getBoolean("permissions." + key, fallback);
    }

    public static JsonObject getPermissionRequirements() {
        JsonObject requirements = new JsonObject();
        requirements.addProperty("gamble", isPermissionRequired("gamble"));
        requirements.addProperty("admin", isPermissionRequired("admin"));
        requirements.addProperty("walletAdmin", isPermissionRequired("walletAdmin"));
        return requirements;
    }

    public static Main getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
