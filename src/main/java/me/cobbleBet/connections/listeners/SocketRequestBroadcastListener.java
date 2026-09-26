package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.text.DecimalFormat;
import java.util.UUID;

public class SocketRequestBroadcastListener extends SocketMessageListener {
    public SocketRequestBroadcastListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("message") || !json.has("broadcastType")) return;

        String broadcastType = json.get("broadcastType").getAsString();
        String template = json.get("message").getAsString();
        double amount = 0;
        if ("bigWin".equalsIgnoreCase(broadcastType)) {
            if (!json.has("amount")) return;
            try {
                amount = json.get("amount").getAsDouble();
            } catch (RuntimeException ignored) {
                return;
            }
            if (!Double.isFinite(amount) || amount < Main.bigWinThreshold) return;
        }

        final double winningAmount = amount;
        final String playerUUID = json.has("playerUUID") ? json.get("playerUUID").getAsString() : "";
        String currency = json.has("currency") ? json.get("currency").getAsString() : Main.vaultCurrencyName;
        if (currency == null || currency.isBlank()) currency = Main.economyItem == null ? "Coins" : Main.economyItem.name();
        final String winningCurrency = currency;

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (!Main.broadcastingEnabled || !Main.broadcastEvents.getOrDefault(broadcastType, false)) return;

            String message = template;
            if ("bigWin".equalsIgnoreCase(broadcastType)) {
                String playerName = "A player";
                try {
                    String name = Bukkit.getOfflinePlayer(UUID.fromString(playerUUID)).getName();
                    if (name != null && !name.isBlank()) playerName = name;
                } catch (IllegalArgumentException ignored) {
                    // Keep the generic player label for malformed UUIDs.
                }

                MiniMessage miniMessage = MiniMessage.miniMessage();
                message = message
                        .replace("{player}", miniMessage.escapeTags(playerName))
                        .replace("{amount}", new DecimalFormat("#,##0.##").format(winningAmount))
                        .replace("{currency}", miniMessage.escapeTags(winningCurrency));
            }

            Component finalMessage = MiniMessage.miniMessage().deserialize(Main.broadcastPrefix + " " + message);
            Bukkit.broadcast(finalMessage);
        });
    }
}
