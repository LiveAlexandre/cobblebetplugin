package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;

public final class SocketMinesEventListener extends SocketMessageListener {
    public SocketMinesEventListener(String type,CobbleSocketClient client){super(type,client);}
    @Override public void trigger(JsonObject json){Main.getInstance().minesController.accept(json);}
}
