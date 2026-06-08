package me.cobbleBet.connections.listeners;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import me.cobbleBet.players.PlayerWallet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SocketRequestBalanceListener extends SocketMessageListener {

    public SocketRequestBalanceListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if(json.has("playerUUID")) {
            UUID uuid = UUID.fromString(json.get("playerUUID").getAsString());
            PlayerWallet wallet = Main.playerWalletHashMap.get(uuid);

            double balance = wallet.getBalance();
            System.out.println(balance);

            JsonObject res = new JsonObject();
            res.addProperty("type", "receivePlayerBalance");
            res.addProperty("balance", balance);
            res.addProperty("playerUUID", uuid.toString());

            this.client.send(res.toString());

        }

    }

}
