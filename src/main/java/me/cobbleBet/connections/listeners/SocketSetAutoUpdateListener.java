package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public final class SocketSetAutoUpdateListener extends SocketMessageListener {
    public SocketSetAutoUpdateListener(String type, CobbleSocketClient client) { super(type, client); }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("autoUpdateEnabled")
                || !json.get("autoUpdateEnabled").isJsonPrimitive()
                || !json.get("autoUpdateEnabled").getAsJsonPrimitive().isBoolean()) return;
        String requestId = json.get("requestId").getAsString();
        boolean enabled = json.get("autoUpdateEnabled").getAsBoolean();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Main plugin = Main.getInstance();
            JsonObject result = new JsonObject();
            result.addProperty("type", "autoUpdateSettingsResult");
            result.addProperty("requestId", requestId);
            try {
                plugin.getConfig().set("autoUpdate", enabled);
                me.cobbleBet.storage.WebsiteSettingsStore.saveConfirmed(plugin);
                Main.loadConfigValues();
                plugin.applyAutoUpdateSetting();
                result.addProperty("success", true);
                result.addProperty("autoUpdateEnabled", Main.autoUpdateEnabled);
                result.addProperty("message", enabled
                        ? "Automatic updates are enabled. New releases will be staged for the next restart."
                        : "Automatic updates are disabled. Admins will be reminded when an update is required.");
            } catch (Exception error) {
                plugin.reloadConfig();
                Main.loadConfigValues();
                result.addProperty("success", false);
                result.addProperty("message", "Could not save the automatic update setting. Check the server console.");
                plugin.getLogger().warning("Could not save automatic update setting: " + error.getMessage());
            }
            client.send(result.toString());
            plugin.sendServerStatus();
        });
    }
}
