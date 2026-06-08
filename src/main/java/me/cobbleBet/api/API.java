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

        Main.getInstance().cobbleSocketClient.send(res.toString());

    }
}
