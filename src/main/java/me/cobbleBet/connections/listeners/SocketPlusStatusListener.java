package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public final class SocketPlusStatusListener extends SocketMessageListener {
    public SocketPlusStatusListener(String type, CobbleSocketClient client) { super(type, client); }

    @Override public void trigger(JsonObject json) {
        if (!json.has("active")) return;
        if (json.has("serverId") && !Main.serverId.equalsIgnoreCase(json.get("serverId").getAsString())) {
            Main.getInstance().getLogger().warning("Ignored a CobbleBet Plus update for a different server UUID.");
            return;
        }
        boolean active = json.get("active").getAsBoolean();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> Main.getInstance().applyPlusStatus(active));
    }
}
