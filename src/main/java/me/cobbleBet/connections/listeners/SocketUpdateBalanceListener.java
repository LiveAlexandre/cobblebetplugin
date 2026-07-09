package me.cobbleBet.connections.listeners;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import me.cobbleBet.economy.EconomyManager;
import me.cobbleBet.players.PlayerWallet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

public class SocketUpdateBalanceListener extends SocketMessageListener {

    public SocketUpdateBalanceListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if(json.has("playerUUID")) {
            UUID uuid = UUID.fromString(json.get("playerUUID").getAsString());
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            double balance = json.get("balance").getAsDouble();

            EconomyManager eco = Main.getInstance().getEconomyManager();
            if(eco.setBalance(player, balance))
                Bukkit.getLogger().log(Level.INFO, player.getName() + "'s new balance is: " + balance);
            else
                Bukkit.getLogger().log(Level.INFO, "could not update " + player.getName() + "'s balance to" + balance);

            return;
        }
        Bukkit.getLogger().log(Level.SEVERE, "RECEIVED BALANCE UPDATE SOCKET WITH NO PLAYER UUID");
    }

}
