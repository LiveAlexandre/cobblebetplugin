package me.cobbleBet.connections;

import com.google.gson.JsonObject;

public abstract class SocketMessageListener {

    public String type;
    public CobbleSocketClient client;

    public SocketMessageListener(String type, CobbleSocketClient client) {
        this.type=type;
        this.client=client;
    }

    public abstract void trigger(JsonObject jsonObject);


}
