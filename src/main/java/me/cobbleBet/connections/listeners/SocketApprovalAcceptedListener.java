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
        plugin.setOfficialPluginRelease(version, jarName);
        client.markApproved();
        Bukkit.getScheduler().runTask(plugin, plugin::sendServerStatus);
    }
}