package me.cobbleBet;

import com.google.gson.JsonObject;
import me.cobbleBet.config.CobbleBetSettings;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.events.CobbleEvent;
import me.cobbleBet.players.PlayerWallet;
import me.cobbleBet.services.PluginUpdateManager;
import me.cobbleBet.services.PluginRuntime;
import me.cobbleBet.services.ServerStatusService;
import me.cobbleBet.storage.PlayerWalletStorage;
import me.cobbleBet.storage.PluginDataStore;
import me.cobbleBet.visuals.GamblingIndicatorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin {

    private static Main instance;
    public static double cobblebetPluginVersion = 1.1;


    // =========================
    // EVENTS
    // =========================


    public static final HashMap<UUID, CobbleEvent> activeEvents = new HashMap<>();

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
    public static String serverId;

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
    public static boolean autoUpdateEnabled;

    // =========================
    // RUNTIME
    // =========================
    public static HashMap<UUID, PlayerWallet> playerWalletHashMap = new HashMap<>();

    private PlayerWalletStorage playerWalletStorage;
    private PluginDataStore dataStore;
    private EconomyManager economyManager;
    public CobbleSocketClient cobbleSocketClient;
    public final ConcurrentHashMap<UUID, Long> pendingAdminPanelRequests = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<UUID, Long> pendingAccountLinkRequests = new ConcurrentHashMap<>();
    public GamblingIndicatorManager gamblingIndicatorManager;
    public me.cobbleBet.gui.CobbleMenuController menuController;
    public me.cobbleBet.gui.CoinflipController coinflipController;
    public me.cobbleBet.visuals.CoinflipBoardManager coinflipBoardManager;
    public me.cobbleBet.gui.BlackjackController blackjackController;
    public me.cobbleBet.visuals.BlackjackTableManager blackjackTableManager;
    public me.cobbleBet.gui.MinesController minesController;
    public me.cobbleBet.visuals.MinesFieldManager minesFieldManager;
    public me.cobbleBet.gui.PlinkoController plinkoController;
    public me.cobbleBet.visuals.PlinkoBoardManager plinkoBoardManager;
    public me.cobbleBet.gui.RouletteController rouletteController;
    public me.cobbleBet.visuals.RouletteTableManager rouletteTableManager;
    private final long startedAt = System.currentTimeMillis();
    private PluginUpdateManager updateManager;
    private ServerStatusService statusService;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        dataStore = new PluginDataStore(this);
        dataStore.migrateFromConfig(getConfig());
        updateManager = new PluginUpdateManager(this, dataStore);
        statusService = new ServerStatusService(this);
        ensureServerId();
        loadConfigValues();
        me.cobbleBet.storage.WebsiteSettingsStore.load(this);

        loadStorage();
        economyManager = PluginRuntime.initialize(this);
        connectSocket();
        statusService.start();
        updateManager.start();

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
        PluginRuntime.shutdown(this);
    }

    // =========================
    // CONFIG LOADER
    // =========================
    public static void loadConfigValues() {
        if (instance != null) CobbleBetSettings.load(instance);
    }

    private void ensureServerId() {
        String configured = dataStore.getString("serverId", "").trim();
        try {
            UUID.fromString(configured);
        } catch (IllegalArgumentException ignored) {
            configured = UUID.randomUUID().toString();
            dataStore.set("serverId", configured);
            getLogger().info("Created this server's permanent CobbleBet ID.");
        }
        serverId = configured;
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

    public void setOfficialPluginRelease(String version, String jarName, String sha256, boolean autoUpdateAllowed) {
        updateManager.setOfficialRelease(version, jarName, sha256, autoUpdateAllowed);
    }

    public String getOfficialPluginVersion() {
        return updateManager.officialVersion();
    }

    public String getPluginUpdateStatus() {
        return updateManager.status();
    }

    public void requestPluginUpdateCheck() {
        updateManager.requestCheck();
    }

    public void checkForPluginUpdate() {
        updateManager.checkForUpdate();
    }

    public void applyAutoUpdateSetting() {
        updateManager.applyAutoUpdateSetting();
    }

    public void notifyAdminAboutPluginUpdate(Player player) {
        updateManager.notifyAdmin(player);
    }

    public void sendServerStatus() {
        statusService.send();
    }

    public String readServerIconDataUrl() {
        return statusService.readServerIconDataUrl();
    }

    // STORAGE
    // =========================
    private void loadStorage() {
        playerWalletStorage = new PlayerWalletStorage(getDataFolder());
        playerWalletStorage.loadAll();
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

    public boolean isGameEnabled(String game) {
        return !getConfig().getBoolean("websiteCustomization.maintenance.enabled", false)
                && getConfig().getBoolean("websiteCustomization.enabledGames." + game, true);
    }

    public boolean requireGameEnabled(Player player, String game) {
        if (isGameEnabled(game)) return true;
        String message = getConfig().getBoolean("websiteCustomization.maintenance.enabled", false)
                ? getConfig().getString("websiteCustomization.maintenance.message", "Games are temporarily unavailable.")
                : game.substring(0, 1).toUpperCase(Locale.ROOT) + game.substring(1) + " is disabled on this server.";
        player.sendMessage(Component.text(message == null ? "This game is currently unavailable." : message, NamedTextColor.RED));
        return false;
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
    public PluginDataStore getDataStore() { return dataStore; }
    public long getStartedAt() { return startedAt; }
    public long getLastStatusAt() { return statusService == null ? 0L : statusService.lastStatusAt(); }
    public String getPluginFileName() { return getFile().getName(); }
}
