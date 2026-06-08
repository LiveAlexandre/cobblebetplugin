package me.cobbleBet.connections;



import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cobbleBet.connections.listeners.SocketRequestBalanceListener;
import me.cobbleBet.connections.listeners.SocketTokenResponseListener;
import me.cobbleBet.connections.listeners.SocketUpdateBalanceListener;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.logging.Level;

public class SocketMessageHandler {


    private ArrayList<SocketMessageListener> listeners = new ArrayList<>();

    public SocketMessageHandler(CobbleSocketClient client) {
        listeners.add(new SocketTokenResponseListener("tokenResponse", client));
        listeners.add(new SocketRequestBalanceListener("requestBalance", client));
        listeners.add(new SocketUpdateBalanceListener("updateBalance", client));
    }


    public void handleMessage(String msg) {
        Bukkit.getLogger().log(Level.SEVERE, "Received Socket message : " + msg);
        JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
        if(!json.has("type")) {
            Bukkit.getLogger().log(Level.WARNING, "REPORT THIS TO COBBLEBET ADMINS: json received from web socket has no type: " + msg);
            return;
        }
        String type = json.get("type").getAsString();

        for(SocketMessageListener listener : listeners) {
            if(listener.type.equalsIgnoreCase(type)) {
                listener.trigger(json);
                return;
            }
        }
        Bukkit.getLogger().log(Level.SEVERE, "Received Fraudulent Socket message : " + msg);


    }





    public ArrayList<SocketMessageListener> getListeners() {
        return listeners;
    }
}
