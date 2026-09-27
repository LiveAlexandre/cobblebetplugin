package me.cobbleBet.connections;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class CobbleSocketClient extends WebSocketClient {

    private final SocketMessageHandler socketMessageHandler;
    private volatile boolean approved;
    private volatile boolean approvalRejected;
    private volatile boolean stopping;

    public CobbleSocketClient() throws URISyntaxException {
        super(Main.testMode ? new URI("ws://localhost:8908/mc") : new URI("wss://cobblebet.com/mc"));
        this.socketMessageHandler = new SocketMessageHandler(this);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "connect");
        hello.addProperty("pluginKey", Main.cobblebetToken == null ? "" : Main.cobblebetToken.trim());
        hello.addProperty("message", "CobbleBet plugin connection");
        hello.addProperty("serverPort", Bukkit.getPort());
        hello.addProperty("serverName", Main.serverDisplayName == null || Main.serverDisplayName.isBlank() ? Bukkit.getMotd() : Main.serverDisplayName);
        hello.addProperty("pluginName", "CobbleBet");
        hello.addProperty("pluginVersion", Main.getInstance().getDescription().getVersion());
        hello.addProperty("economyType", Main.economyType);
        hello.addProperty("economyItem", Main.economyItem == null ? "" : Main.economyItem.name());
        String currencyName = Main.economyType.equalsIgnoreCase("vault") ? Main.vaultCurrencyName : Main.economyItem.toString();
        hello.addProperty("currencyName", currencyName == null || currencyName.isBlank() ? "Coins" : currencyName);
        hello.addProperty("bigWinThreshold", Main.bigWinThreshold);
        hello.addProperty("broadcastingEnabled", Main.broadcastingEnabled && Main.broadcastEvents.getOrDefault("bigWin", false));
        hello.add("permissionRequirements", Main.getPermissionRequirements());
        hello.addProperty("supportsWebsiteSettings", true);
        hello.addProperty("websiteSettingsInitialized", me.cobbleBet.storage.WebsiteSettingsStore.isInitialized());
        hello.add("websiteSettings", me.cobbleBet.storage.WebsiteSettingsStore.getSettings());
        String icon = Main.getInstance().readServerIconDataUrl();
        if (!icon.isBlank()) hello.addProperty("serverIcon", icon);
        this.send(hello.toString());
    }

    @Override
    public void onMessage(String message) {
        socketMessageHandler.handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        approved = false;
        if (stopping) return;
        if (approvalRejected) {
            Main.getInstance().getLogger().warning("CobbleBet rejected this plugin connection. Check cobblebetToken in config.yml; reconnecting is paused until the server restarts.");
            return;
        }
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        Main.getInstance().getLogger().fine("CobbleBet connection issue: " + ex.getMessage());
    }

    public boolean isApproved() {
        return approved && isOpen();
    }

    public void markApproved() {
        approved = true;
        Main.getInstance().getLogger().info("This server is approved and connected to CobbleBet.");
    }

    public void shutdown() {
        stopping = true;
        approved = false;
        close();
    }

    public void markApprovalRejected(String message) {
        approved = false;
        approvalRejected = true;
        Main.getInstance().getLogger().warning(message == null || message.isBlank() ? "This plugin key is invalid or revoked." : message);
    }

    public void sendPlayerBalance(OfflinePlayer player, double balance) {
        if (player == null || !isApproved()) return;
        JsonObject res = new JsonObject();
        res.addProperty("type", "receivePlayerBalance");
        res.addProperty("balance", balance);
        res.addProperty("playerUUID", player.getUniqueId().toString());
        this.send(res.toString());
    }

    public void scheduleReconnect() {
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (approvalRejected || !Main.getInstance().isEnabled()) return;
            try {
                CobbleSocketClient newClient = new CobbleSocketClient();
                Main.getInstance().cobbleSocketClient = newClient;
                newClient.connect();
            } catch (Exception e) {
                scheduleReconnect();
            }
        }, 20L * 5);
    }
}
