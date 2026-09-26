package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import org.bukkit.Bukkit;

public class SocketSetEconomyListener extends SocketMessageListener {
    public SocketSetEconomyListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("requestId") || !json.has("economyType")) return;
        String requestId = json.get("requestId").getAsString();
        String type = json.get("economyType").getAsString();
        String item = json.has("economyItem") ? json.get("economyItem").getAsString() : "";
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            String error = Main.getInstance().getEconomyManager().applySettings(type, item);
            JsonObject result = new JsonObject();
            result.addProperty("type", "economySettingsResult");
            result.addProperty("requestId", requestId);
            result.addProperty("success", error == null);
            result.addProperty("message", error == null ? "Economy updated and saved to plugins/CobbleBet/config.yml." : error);
            result.addProperty("economyType", Main.economyType);
            result.addProperty("economyItem", Main.economyItem == null ? "" : Main.economyItem.name());
            result.addProperty("currencyName", Main.economyType.equalsIgnoreCase("vault")
                    ? Main.vaultCurrencyName
                    : (Main.economyItem == null ? "Coins" : Main.economyItem.name()));
            client.send(result.toString());
        });
    }
}
