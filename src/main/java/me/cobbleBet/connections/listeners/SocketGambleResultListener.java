package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.UUID;

public class SocketGambleResultListener extends SocketMessageListener {

    public SocketGambleResultListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        // Expected message: {"type":"gambleResult","playerUUID":"...","outcome":"win|loss","amount":25,"currency":"Coins"}
        if (!json.has("playerUUID") || !json.has("amount")) {
            return;
        }

        try {
            UUID playerId = UUID.fromString(json.get("playerUUID").getAsString());
            double amount = json.get("amount").getAsDouble();
            String outcome = json.has("outcome")
                    ? json.get("outcome").getAsString().toLowerCase(Locale.ROOT)
                    : json.has("result") ? json.get("result").getAsString().toLowerCase(Locale.ROOT) : "";
            if (outcome.isBlank() && json.has("won")) {
                outcome = json.get("won").getAsBoolean() ? "win" : "loss";
            }

            boolean won;
            if (outcome.equals("win") || outcome.equals("won")) {
                won = true;
            } else if (outcome.equals("loss") || outcome.equals("lost")) {
                won = false;
            } else {
                return;
            }

            String currency = json.has("currency") ? json.get("currency").getAsString() : Main.vaultCurrencyName;
            double multiplier = json.has("multiplier") ? json.get("multiplier").getAsDouble() : 0;
            if (!Double.isFinite(multiplier) || multiplier < 0) multiplier = 0;
            final double payoutMultiplier = multiplier;
            String game = json.has("game") ? json.get("game").getAsString().toLowerCase(Locale.ROOT) : "";
            if (!game.matches("[a-z0-9_-]{0,24}")) game = "";
            final String resultGame = game;
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (Main.getInstance().gamblingIndicatorManager == null) return;
                long delay = Main.getInstance().gamblingIndicatorManager.takePhysicalRevealDelay(playerId, resultGame);
                Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                    if (Main.getInstance().gamblingIndicatorManager != null) {
                        Main.getInstance().gamblingIndicatorManager.showResult(playerId, won, amount, currency, payoutMultiplier, resultGame);
                    }
                }, delay);
            });
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // Ignore malformed or incomplete gambling result messages.
        }
    }
}
