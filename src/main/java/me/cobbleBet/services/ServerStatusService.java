package me.cobbleBet.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.storage.WebsiteSettingsStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

public final class ServerStatusService {
    private final Main plugin;
    private volatile long lastStatusAt;

    public ServerStatusService(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::send, 20L, 600L);
        startGambleReminders();
    }

    public void send() {
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) return;

        JsonObject status = new JsonObject();
        status.addProperty("type", "serverStatus");
        status.addProperty("serverName", Main.serverDisplayName == null || Main.serverDisplayName.isBlank()
                ? Bukkit.getMotd() : Main.serverDisplayName);
        status.addProperty("pluginName", "CobbleBet");
        status.addProperty("pluginVersion", plugin.getDescription().getVersion());
        status.addProperty("protocolVersion", 2);
        status.addProperty("serverUptimeMillis", System.currentTimeMillis() - plugin.getStartedAt());
        status.addProperty("tps", Math.min(20.0, Bukkit.getTPS()[0]));

        Runtime runtime = Runtime.getRuntime();
        status.addProperty("memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0);
        status.addProperty("memoryMaxMb", runtime.maxMemory() / 1048576.0);
        status.addProperty("autoUpdateEnabled", Main.autoUpdateEnabled);
        status.addProperty("economyType", Main.economyType);
        status.addProperty("economyItem", Main.economyItem == null ? "" : Main.economyItem.name());
        status.addProperty("currencyName", Main.economyType.equalsIgnoreCase("vault")
                ? Main.vaultCurrencyName : Main.economyItem == null ? "Coins" : Main.economyItem.name());
        status.add("permissionRequirements", Main.getPermissionRequirements());
        status.addProperty("broadcastingEnabled",
                Main.broadcastingEnabled && Main.broadcastEvents.getOrDefault("bigWin", false));
        status.addProperty("bigWinThreshold", Main.bigWinThreshold);
        status.addProperty("websiteSettingsInitialized", WebsiteSettingsStore.isInitialized());
        status.add("websiteSettings", WebsiteSettingsStore.getSettings());

        JsonArray players = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.getUniqueId().toString());
            entry.addProperty("name", player.getName());
            players.add(entry);
        }
        status.add("onlinePlayers", players);
        plugin.cobbleSocketClient.send(status.toString());
        lastStatusAt = System.currentTimeMillis();
    }

    public long lastStatusAt() {
        return lastStatusAt;
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

    private void startGambleReminders() {
        long minutes = Math.max(1L, plugin.getConfig().getLong("broadcastReminder.intervalMinutes", 15L));
        long period = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!Main.broadcastEvents.getOrDefault("gambleReminder", false)
                    || Bukkit.getOnlinePlayers().isEmpty()) return;
            String message = plugin.getConfig().getString("broadcastReminder.message",
                    "<aqua>Feeling lucky?</aqua> <yellow>Use <bold>/gamble</bold> to play CobbleBet!</yellow>");
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(Main.broadcastPrefix + " " + message));
        }, period, period);
    }
}
