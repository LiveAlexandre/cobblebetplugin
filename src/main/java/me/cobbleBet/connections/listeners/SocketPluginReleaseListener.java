package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;

public final class SocketPluginReleaseListener extends SocketMessageListener {
    public SocketPluginReleaseListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        String version = json.has("latestPluginVersion") ? json.get("latestPluginVersion").getAsString() : "";
        String jarName = json.has("pluginJarName") ? json.get("pluginJarName").getAsString() : "";
        Main.getInstance().setOfficialPluginRelease(version, jarName);
    }
}
