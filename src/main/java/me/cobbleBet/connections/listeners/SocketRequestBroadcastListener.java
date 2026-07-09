package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.awt.*;
import java.util.UUID;

public class SocketRequestBroadcastListener extends SocketMessageListener {
    public SocketRequestBroadcastListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if(json.has("message")) {
            String message = json.get("message").getAsString();
            String broadcastType = json.get("broadcastType").getAsString();

            if(!Main.broadcastingEnabled)
                return;
            if(!Main.broadcastEvents.getOrDefault(broadcastType, false))
                return;

            StringBuilder bc = new StringBuilder(Main.broadcastPrefix);

            bc.append(" " + message);

            Component finalMessage = MiniMessage.miniMessage().deserialize(bc.toString());

            Bukkit.broadcast(finalMessage);

        }
    }
}
