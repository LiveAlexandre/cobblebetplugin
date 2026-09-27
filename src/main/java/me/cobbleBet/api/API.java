package me.cobbleBet.api;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface API {

    static void generateAndRegisterPlayerToken(Player player) {


        JsonObject res = new JsonObject();
        res.addProperty("type", "requestToken");
        res.addProperty("playerUUID", player.getUniqueId().toString());
        res.addProperty("playerName", player.getName());

        Main.getInstance().cobbleSocketClient.send(res.toString());

    }
}
