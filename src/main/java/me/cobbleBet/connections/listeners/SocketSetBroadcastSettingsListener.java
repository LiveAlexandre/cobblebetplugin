package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public class SocketSetBroadcastSettingsListener extends SocketMessageListener {
    public SocketSetBroadcastSettingsListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("broadcastingEnabled")
                || !json.get("broadcastingEnabled").isJsonPrimitive()
                || !json.get("broadcastingEnabled").getAsJsonPrimitive().isBoolean()) return;
        String requestId = json.get("requestId").getAsString();
        boolean enabled = json.get("broadcastingEnabled").getAsBoolean();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Main plugin = Main.getInstance();
            JsonObject result = new JsonObject();
            result.addProperty("type", "broadcastSettingsResult");
            result.addProperty("requestId", requestId);
            try {
                plugin.getConfig().set("broadcastingEnabled", enabled);
                plugin.getConfig().set("broadcastEvents.bigWin", enabled);
                me.cobbleBet.storage.WebsiteSettingsStore.saveConfirmed(plugin);
                Main.loadConfigValues();
                result.addProperty("success", true);
                result.addProperty("broadcastingEnabled", Main.broadcastingEnabled && Main.broadcastEvents.getOrDefault("bigWin", false));
                result.addProperty("message", "Big-win broadcasts saved to plugins/CobbleBet/config.yml.");
            } catch (Exception error) {
                plugin.reloadConfig();
                Main.loadConfigValues();
                result.addProperty("success", false);
                result.addProperty("message", "Could not save broadcast settings. Check the Minecraft server console.");
                plugin.getLogger().warning("Could not save broadcast settings: " + error.getMessage());
            }
            client.send(result.toString());
            plugin.sendServerStatus();
        });
    }
}
