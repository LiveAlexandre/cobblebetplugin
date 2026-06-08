package me.cobbleBet.connections;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class CobbleSocketClient extends WebSocketClient {

    private SocketMessageHandler socketMessageHandler;
    private int reconnectAttempts= 0;


    public CobbleSocketClient() throws URISyntaxException {
        super(Main.testMode? new URI("ws://localhost:8908/mc") : new URI("ws://cobblebet.com:8908/mc"));

        this.socketMessageHandler = new SocketMessageHandler(this);
    }



    @Override
    public void onOpen(ServerHandshake handshakedata) {
        JsonObject res = new JsonObject();
        res.addProperty("type", "connected");
        res.addProperty("message", "Successfully connected to CobbleBet Plugin");
        res.addProperty("serverPort", Bukkit.getPort());
        res.addProperty("economyType", Main.economyType);
        res.addProperty("economyItem", Main.economyItem.toString());

        this.send(res.toString());
    }

    @Override
    public void onMessage(String message) {
        socketMessageHandler.handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {

    }


    public void scheduleReconnect() {

        reconnectAttempts++;

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            try {
                CobbleSocketClient newClient =
                        new CobbleSocketClient();

                newClient.connect();

                Main.getInstance().cobbleSocketClient = newClient;

            } catch (Exception e) {
                scheduleReconnect();
            }
        }, 20L * 5);
    }
}
