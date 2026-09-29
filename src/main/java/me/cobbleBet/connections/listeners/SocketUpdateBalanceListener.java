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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SocketUpdateBalanceListener extends SocketMessageListener {
    private final ConcurrentHashMap<String, JsonObject> completed = new ConcurrentHashMap<>();

    public SocketUpdateBalanceListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {
        String transactionId = json.has("transactionId") && !json.get("transactionId").isJsonNull()
                ? json.get("transactionId").getAsString() : "";
        if (!transactionId.isBlank() && completed.containsKey(transactionId)) {
            client.send(completed.get(transactionId).toString());
            return;
        }
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            JsonObject result = new JsonObject();
            result.addProperty("type", "balanceUpdateResult");
            if (!transactionId.isBlank()) result.addProperty("transactionId", transactionId);
            try {
                if (!json.has("playerUUID") || !json.has("balance")) throw new IllegalArgumentException("Missing player or balance.");
                UUID uuid = UUID.fromString(json.get("playerUUID").getAsString());
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                double balance = json.get("balance").getAsDouble();
                if (!Double.isFinite(balance)) throw new IllegalArgumentException("Invalid balance.");
                EconomyManager eco = Main.getInstance().getEconomyManager();
                if (!eco.setBalance(player, balance)) throw new IllegalStateException("The configured economy rejected the balance update.");
                result.addProperty("success", true);
                result.addProperty("balance", balance);
                Bukkit.getLogger().log(Level.INFO, player.getName() + "'s new balance is: " + balance);
            } catch (Exception error) {
                result.addProperty("success", false);
                result.addProperty("message", error.getMessage() == null ? "Balance update failed." : error.getMessage());
                Bukkit.getLogger().log(Level.WARNING, "CobbleBet balance update failed: " + error.getMessage());
            }
            if (!transactionId.isBlank()) {
                if (completed.size() >= 500) completed.clear();
                completed.put(transactionId, result.deepCopy());
            }
            if (client.isOpen()) client.send(result.toString());
        });
    }

}
