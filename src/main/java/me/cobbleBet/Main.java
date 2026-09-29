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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URISyntaxException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Main extends JavaPlugin {

    private static Main instance;
    private static final String PLUGIN_DOWNLOAD_URL = "https://cobblebet.com/api/download-plugin";
    private final ConcurrentHashMap<UUID, String> notifiedUpdateVersions = new ConcurrentHashMap<>();

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
    private EconomyManager economyManager;
    public CobbleSocketClient cobbleSocketClient;
    public final ConcurrentHashMap<UUID, Long> pendingAdminPanelRequests = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<UUID, Long> pendingAccountLinkRequests = new ConcurrentHashMap<>();
    public GamblingIndicatorManager gamblingIndicatorManager;
    public me.cobbleBet.gui.CobbleMenuController menuController;
    private final long startedAt = System.currentTimeMillis();
    private volatile long lastStatusAt;
    private volatile String officialPluginVersion = "";
    private volatile String officialPluginJarName = "CobbleBet.jar";
    private volatile String officialPluginSha256 = "";
    private final AtomicBoolean updateDownloadRunning = new AtomicBoolean(false);
    private volatile String stagedPluginVersion = "";
    private volatile String autoUpdateFailure = "";
    private volatile String installedUpdateNoticeVersion = "";

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        ensureServerId();
        loadConfigValues();
        me.cobbleBet.storage.WebsiteSettingsStore.load(this);

        loadStorage();
        economyManager = new EconomyManager(this);
        gamblingIndicatorManager = new GamblingIndicatorManager(this);
        menuController = new me.cobbleBet.gui.CobbleMenuController(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(menuController, this);
        getServer().getPluginManager().registerEvents(new me.cobbleBet.listeners.GamblingPageActivityListener(gamblingIndicatorManager), this);
        connectSocket();
        startStatusUpdates();
        prepareInstalledUpdateNotice();
        startUpdateReminders();

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
        serverId = Main.getInstance().getConfig().getString("serverId", "").trim();
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
        autoUpdateEnabled = Main.getInstance().getConfig().getBoolean("autoUpdate", true);

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
        Bukkit.getLogger().info("Auto Update: " + autoUpdateEnabled);
    }

    private void ensureServerId() {
        String configured = getConfig().getString("serverId", "").trim();
        try {
            UUID.fromString(configured);
        } catch (IllegalArgumentException ignored) {
            configured = UUID.randomUUID().toString();
            getConfig().set("serverId", configured);
            saveConfig();
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

    public void setOfficialPluginRelease(String version, String jarName, String sha256) {
        if (version != null && !version.isBlank()) officialPluginVersion = version.trim();
        if (jarName != null && !jarName.isBlank()) officialPluginJarName = jarName.trim();
        if (sha256 != null && sha256.matches("[a-fA-F0-9]{64}")) officialPluginSha256 = sha256.toLowerCase();
        checkForPluginUpdate();
    }

    public void checkForPluginUpdate() {
        String installedVersion = getDescription().getVersion();
        String latestVersion = officialPluginVersion;
        if (!autoUpdateEnabled || latestVersion.isBlank() || !isOlderVersion(installedVersion, latestVersion)) return;
        if (latestVersion.equals(stagedPluginVersion) || !updateDownloadRunning.compareAndSet(false, true)) return;
        autoUpdateFailure = "";

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File temporary = null;
            try {
                File updateFolder = getServer().getUpdateFolderFile();
                Files.createDirectories(updateFolder.toPath());
                temporary = File.createTempFile("cobblebet-update-", ".jar", updateFolder);
                HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(12)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(PLUGIN_DOWNLOAD_URL))
                        .timeout(Duration.ofSeconds(45)).header("User-Agent", "CobbleBet/" + installedVersion).build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IllegalStateException("download returned HTTP " + response.statusCode());
                }
                try (InputStream input = response.body()) {
                    Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                if (temporary.length() < 1024 || temporary.length() > 50_000_000) throw new IllegalStateException("downloaded JAR size is invalid");
                if (!officialPluginSha256.isBlank()) {
                    String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(temporary.toPath())));
                    if (!actualHash.equalsIgnoreCase(officialPluginSha256)) throw new IllegalStateException("downloaded JAR checksum does not match the published release");
                }
                try (JarFile jar = new JarFile(temporary)) {
                    var descriptorEntry = jar.getJarEntry("plugin.yml");
                    if (descriptorEntry == null) throw new IllegalStateException("downloaded file is not a Bukkit plugin JAR");
                    String descriptor;
                    try (InputStream descriptorInput = jar.getInputStream(descriptorEntry)) {
                        descriptor = new String(descriptorInput.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    if (!descriptor.matches("(?ms).*^\\s*name\\s*:\\s*['\"]?CobbleBet['\"]?\\s*$.*")) {
                        throw new IllegalStateException("downloaded JAR is not the CobbleBet plugin");
                    }
                    String expectedVersion = java.util.regex.Pattern.quote(latestVersion);
                    if (!descriptor.matches("(?ms).*^\\s*version\\s*:\\s*['\"]?" + expectedVersion + "['\"]?\\s*$.*")) {
                        throw new IllegalStateException("downloaded JAR version does not match " + latestVersion);
                    }
                }
                if (!autoUpdateEnabled) {
                    getLogger().info("Automatic CobbleBet update was disabled before the download completed; the staged update was cancelled.");
                    return;
                }
                File target = new File(updateFolder, getFile().getName());
                try {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                temporary = null;
                stagedPluginVersion = latestVersion;
                autoUpdateFailure = "";
                getLogger().info("CobbleBet " + latestVersion + " downloaded to " + target + ". It will install on the next server restart.");
                Bukkit.getScheduler().runTask(this, () -> {
                    getConfig().set("updater.pendingVersion", latestVersion);
                    saveConfig();
                });
            } catch (Exception error) {
                autoUpdateFailure = error.getMessage() == null ? "unknown download error" : error.getMessage();
                getLogger().warning("Automatic CobbleBet update failed: " + error.getMessage());
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary.toPath()); } catch (Exception ignored) {}
                updateDownloadRunning.set(false);
            }
        });
    }

    public void applyAutoUpdateSetting() {
        if (autoUpdateEnabled) {
            checkForPluginUpdate();
            return;
        }
        stagedPluginVersion = "";
        getConfig().set("updater.pendingVersion", null);
        try {
            Files.deleteIfExists(new File(getServer().getUpdateFolderFile(), getFile().getName()).toPath());
        } catch (Exception error) {
            getLogger().warning("Could not remove the staged CobbleBet update: " + error.getMessage());
        }
        saveConfig();
    }

    private void prepareInstalledUpdateNotice() {
        String pendingVersion = getConfig().getString("updater.pendingVersion", "").trim();
        if (!pendingVersion.isBlank() && pendingVersion.equals(getDescription().getVersion())) {
            installedUpdateNoticeVersion = pendingVersion;
            getConfig().set("updater.pendingVersion", null);
            saveConfig();
        }
    }

    private void startUpdateReminders() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            notifiedUpdateVersions.entrySet().removeIf(entry -> entry.getValue().startsWith("needed:"));
            Bukkit.getOnlinePlayers().forEach(this::notifyAdminAboutPluginUpdate);
        }, 20L * 180L, 20L * 60L * 30L);
    }

    public void notifyAdminAboutPluginUpdate(Player player) {
        if (player == null || !player.isOnline() || !player.hasPermission("cobblebet.admin")) return;
        String installedVersion = getDescription().getVersion();
        if (!installedUpdateNoticeVersion.isBlank()) {
            String noticeKey = "installed:" + installedUpdateNoticeVersion;
            if (noticeKey.equals(notifiedUpdateVersions.put(player.getUniqueId(), noticeKey))) return;
            player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                    .append(Component.text("Automatically updated to " + installedUpdateNoticeVersion + ".", NamedTextColor.GREEN)));
            return;
        }
        String latestVersion = officialPluginVersion;
        if (latestVersion.isBlank() || !isOlderVersion(installedVersion, latestVersion)) return;
        if (autoUpdateEnabled && autoUpdateFailure.isBlank()) return;
        String noticeKey = "needed:" + latestVersion;
        if (noticeKey.equals(notifiedUpdateVersions.put(player.getUniqueId(), noticeKey))) return;

        Component download = Component.text("Download " + officialPluginJarName, NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(PLUGIN_DOWNLOAD_URL))
                .hoverEvent(HoverEvent.showText(Component.text("Open the official CobbleBet download", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("[CobbleBet] ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.text("A plugin update is available!", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("Installed ", NamedTextColor.GRAY)
                .append(Component.text(installedVersion, NamedTextColor.WHITE))
                .append(Component.text("  →  Latest ", NamedTextColor.GRAY))
                .append(Component.text(latestVersion, NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Click to update: ", NamedTextColor.GRAY).append(download));
        String reason = autoUpdateEnabled
                ? "Automatic update failed (" + autoUpdateFailure + "). Download the JAR manually and restart your server."
                : "Automatic updates are disabled. Replace the old JAR and restart your server.";
        player.sendMessage(Component.text(reason, NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
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
        status.addProperty("protocolVersion", 2);
        status.addProperty("serverUptimeMillis", System.currentTimeMillis() - startedAt);
        status.addProperty("tps", Math.min(20.0, Bukkit.getTPS()[0]));
        Runtime runtime = Runtime.getRuntime();
        status.addProperty("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0);
        status.addProperty("memoryMaxMb", runtime.maxMemory() / 1048576.0);
        status.addProperty("autoUpdateEnabled", autoUpdateEnabled);
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
        lastStatusAt = System.currentTimeMillis();
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
        getCommand("plinko").setExecutor(gameShortcut);
        getCommand("dice").setExecutor(gameShortcut);
        getCommand("crash").setExecutor(gameShortcut);

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
    public long getStartedAt() { return startedAt; }
    public long getLastStatusAt() { return lastStatusAt; }
}
