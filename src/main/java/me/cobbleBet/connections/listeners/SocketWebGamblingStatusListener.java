package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

import java.util.UUID;

public final class SocketWebGamblingStatusListener extends SocketMessageListener {
    public SocketWebGamblingStatusListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!client.isApproved() || !json.has("playerUUID") || !json.has("active")) return;
        try {
            UUID playerId = UUID.fromString(json.get("playerUUID").getAsString());
            boolean active = json.get("active").getAsBoolean();
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (Main.getInstance().gamblingIndicatorManager != null) {
                    Main.getInstance().gamblingIndicatorManager.setWebGamePage(playerId, active);
                }
            });
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // Ignore malformed browser activity messages.
        }
    }
}
