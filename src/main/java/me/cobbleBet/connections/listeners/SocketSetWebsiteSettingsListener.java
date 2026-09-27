package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import me.cobbleBet.storage.WebsiteSettingsStore;
import org.bukkit.Bukkit;

public class SocketSetWebsiteSettingsListener extends SocketMessageListener {
    public SocketSetWebsiteSettingsListener(String type, CobbleSocketClient client) { super(type, client); }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("settings") || !json.get("settings").isJsonObject()) return;
        String requestId = json.get("requestId").getAsString();
        JsonObject settings = json.getAsJsonObject("settings").deepCopy();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            JsonObject result = new JsonObject();
            result.addProperty("type", "websiteSettingsResult");
            result.addProperty("requestId", requestId);
            try {
                WebsiteSettingsStore.save(Main.getInstance(), settings);
                result.addProperty("success", true);
                result.add("settings", WebsiteSettingsStore.getSettings());
            } catch (Exception error) {
                result.addProperty("success", false);
                result.addProperty("message", "Could not save customization to config.yml. Check the Minecraft server console.");
                Main.getInstance().getLogger().warning("Could not save website customization: " + error.getMessage());
            }
            if (client.isOpen()) client.send(result.toString());
        });
    }
}
