package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public class SocketSetPermissionRequirementsListener extends SocketMessageListener {
    public SocketSetPermissionRequirementsListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("permissionRequirements") || !json.get("permissionRequirements").isJsonObject()) return;
        String requestId = json.get("requestId").getAsString();
        JsonObject requested = json.getAsJsonObject("permissionRequirements");
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Main plugin = Main.getInstance();
            try {
                plugin.getConfig().set("permissions.gamble", readRequired(requested, "gamble"));
                plugin.getConfig().set("permissions.admin", readRequired(requested, "admin"));
                plugin.getConfig().set("permissions.walletAdmin", readRequired(requested, "walletAdmin"));
                me.cobbleBet.storage.WebsiteSettingsStore.saveConfirmed(plugin);
                JsonObject result = new JsonObject();
                result.addProperty("type", "permissionSettingsResult");
                result.addProperty("requestId", requestId);
                result.addProperty("success", true);
                result.addProperty("message", "Permission requirements saved to plugins/CobbleBet/config.yml.");
                result.add("permissionRequirements", Main.getPermissionRequirements());
                client.send(result.toString());
            } catch (Exception error) {
                plugin.reloadConfig();
                Main.loadConfigValues();
                JsonObject result = new JsonObject();
                result.addProperty("type", "permissionSettingsResult");
                result.addProperty("requestId", requestId);
                result.addProperty("success", false);
                result.addProperty("message", "Could not save permission settings. Check the Minecraft server console.");
                client.send(result.toString());
                plugin.getLogger().warning("Could not save permission settings: " + error.getMessage());
            }
        });
    }

    private boolean readRequired(JsonObject requested, String key) {
        return !requested.has(key) || !requested.get(key).isJsonPrimitive() || !requested.get(key).getAsJsonPrimitive().isBoolean()
                || requested.get(key).getAsBoolean();
    }
}
