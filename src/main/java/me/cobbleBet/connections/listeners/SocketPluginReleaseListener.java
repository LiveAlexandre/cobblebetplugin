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
        String sha256 = json.has("pluginSha256") ? json.get("pluginSha256").getAsString() : "";
        boolean autoUpdateAllowed = json.has("pluginAutoUpdateAllowed") && json.get("pluginAutoUpdateAllowed").getAsBoolean();
        Main.getInstance().setOfficialPluginRelease(version, jarName, sha256, autoUpdateAllowed);
    }
}
