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

    protected void acknowledgeResponse(JsonObject response) {
        if (!response.has("requestId") || !client.isOpen()) return;
        JsonObject acknowledgment = new JsonObject();
        acknowledgment.addProperty("type", "linkResponseAck");
        acknowledgment.addProperty("requestId", response.get("requestId").getAsString());
        acknowledgment.addProperty("responseType", type);
        try {
            client.send(acknowledgment.toString());
        } catch (RuntimeException error) {
            me.cobbleBet.Main.getInstance().getLogger().warning("Delivered " + type
                    + " but could not acknowledge it: " + error.getMessage());
        }
    }


}
