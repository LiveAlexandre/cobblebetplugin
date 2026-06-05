package me.cobbleBet.connections;

import gime.cobbleBet.Main;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class CobbleSocketClient extends WebSocketClient {
    public CobbleSocketClient() throws URISyntaxException {
        super(Main.testMode? new URI("2") : new URI(""));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {

    }

    @Override
    public void onMessage(String message) {

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

    }

    @Override
    public void onError(Exception ex) {

    }
}
