package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.connections.CobbleSocketClient;
import org.bukkit.Bukkit;
import me.cobbleBet.connections.SocketMessageListener;

public class SocketApprovalAcceptedListener extends SocketMessageListener {
    public SocketApprovalAcceptedListener(String type, CobbleSocketClient client) { super(type, client); }
    @Override public void trigger(JsonObject json) {
        me.cobbleBet.Main plugin = me.cobbleBet.Main.getInstance();
        String version = json.has("latestPluginVersion") ? json.get("latestPluginVersion").getAsString() : "";
        String jarName = json.has("pluginJarName") ? json.get("pluginJarName").getAsString() : "";
        String sha256 = json.has("pluginSha256") ? json.get("pluginSha256").getAsString() : "";
        boolean autoUpdateAllowed = json.has("pluginAutoUpdateAllowed") && json.get("pluginAutoUpdateAllowed").getAsBoolean();
        plugin.setOfficialPluginRelease(version, jarName, sha256, autoUpdateAllowed);
        client.markApproved();
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.sendServerStatus();
            if (plugin.coinflipController != null) plugin.coinflipController.requestLobby();
        });
    }
}
