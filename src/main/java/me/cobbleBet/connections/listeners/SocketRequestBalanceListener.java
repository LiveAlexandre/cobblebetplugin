package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class SocketRequestBalanceListener extends SocketMessageListener {

    public SocketRequestBalanceListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if(json.has("playerUUID")) {
            UUID uuid = UUID.fromString(json.get("playerUUID").getAsString());
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            double balance = Main.getInstance().getEconomyManager().getBalance(player);
            this.client.sendPlayerBalance(player, balance);

        }

    }

}
