package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;

public class SocketApprovalRejectedListener extends SocketMessageListener {
    public SocketApprovalRejectedListener(String type, CobbleSocketClient client) { super(type, client); }
    @Override public void trigger(JsonObject json) {
        String message = json.has("message") ? json.get("message").getAsString() : "CobbleBet approval rejected.";
        client.markApprovalRejected(message);
    }
}