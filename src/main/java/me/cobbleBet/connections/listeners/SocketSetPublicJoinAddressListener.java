package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.config.PublicJoinAddress;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public final class SocketSetPublicJoinAddressListener extends SocketMessageListener {
    public SocketSetPublicJoinAddressListener(String type, CobbleSocketClient client) { super(type, client); }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("publicJoinAddress")
                || !json.get("publicJoinAddress").isJsonPrimitive()
                || !json.get("publicJoinAddress").getAsJsonPrimitive().isString()) return;
        String requestId = json.get("requestId").getAsString();
        String requested = json.get("publicJoinAddress").getAsString();
        String normalized = PublicJoinAddress.normalize(requested);
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Main plugin = Main.getInstance();
            JsonObject result = new JsonObject();
            result.addProperty("type", "joinAddressSettingsResult");
            result.addProperty("requestId", requestId);
            if (normalized == null) {
                result.addProperty("success", false);
                result.addProperty("message", "Use a Minecraft address such as play.example.com or 203.0.113.10:25565.");
                client.send(result.toString());
                return;
            }
            try {
                plugin.getConfig().set("publicAddress", normalized);
                me.cobbleBet.storage.WebsiteSettingsStore.saveConfirmed(plugin);
                Main.loadConfigValues();
                result.addProperty("success", true);
                result.addProperty("publicJoinAddress", Main.publicJoinAddress);
                result.addProperty("publicJoinAddressSource", Main.publicJoinAddressSource);
                result.addProperty("message", normalized.isBlank()
                        ? "Manual address cleared; CobbleBet is using the detected host allocation."
                        : "Player join address saved to plugins/CobbleBet/config.yml.");
            } catch (Exception error) {
                plugin.reloadConfig();
                Main.loadConfigValues();
                result.addProperty("success", false);
                result.addProperty("message", "Could not save the player join address. Check the server console.");
                plugin.getLogger().warning("Could not save public join address: " + error.getMessage());
            }
            client.send(result.toString());
            plugin.sendServerStatus();
        });
    }
}
