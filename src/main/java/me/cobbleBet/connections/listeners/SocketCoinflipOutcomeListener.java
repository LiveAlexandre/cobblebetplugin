package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.UUID;

public class SocketCoinflipOutcomeListener extends SocketMessageListener {
    public SocketCoinflipOutcomeListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("playerUUID") || !json.has("won") || !json.has("amount")) return;
        try {
            UUID playerId = UUID.fromString(json.get("playerUUID").getAsString());
            boolean won = json.get("won").getAsBoolean();
            double amount = json.get("amount").getAsDouble();
            String opponent = json.has("opponent") ? json.get("opponent").getAsString() : "another player";
            if (!Double.isFinite(amount) || amount < 0) return;
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) return;
                Main.getInstance().coinflipController.reveal(player, won, amount, opponent);
                String result = won ? "won" : "lost";
                Component message = Component.text("Coinflip: ", NamedTextColor.GOLD)
                        .append(Component.text("You " + result + " " + new DecimalFormat("#,##0.##").format(amount)
                                + " against " + opponent + ".", won ? NamedTextColor.GREEN : NamedTextColor.RED));
                player.sendMessage(message);
            });
        } catch (RuntimeException ignored) {
            // Ignore malformed notifications from the remote service.
        }
    }
}
