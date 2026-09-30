package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;

public final class SocketPlinkoEventListener extends SocketMessageListener {
    public SocketPlinkoEventListener(String type, CobbleSocketClient client) { super(type, client); }
    @Override public void trigger(JsonObject json) { if (Main.getInstance().plinkoController != null) Main.getInstance().plinkoController.accept(json); }
}
