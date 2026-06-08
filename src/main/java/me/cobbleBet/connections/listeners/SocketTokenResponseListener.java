package me.cobbleBet.connections.listeners;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SocketTokenResponseListener extends SocketMessageListener {

    public SocketTokenResponseListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {

        if(json.has("token")) {
            if(json.has("playerUUID")) {

                String token = json.get("token").getAsString();
                String uuidString = json.get("playerUUID").getAsString();
                UUID uuid = UUID.fromString(uuidString);

                try {
                    Player player = Bukkit.getPlayer(uuid);

                    Component message = getLinkFromToken(token);

                    player.sendMessage(Component.text("Click this link to open your Gambling Session: ", NamedTextColor.GRAY).append(message));
                }
                catch (Exception ignored) {}
            }
        }

    }

    private static @NotNull Component getLinkFromToken(String token) {
        StringBuilder link = new StringBuilder(Main.testMode? "http://localhost:8908/login-token?token=" : "https://cobblebet.com/login-token?token=");
        link.append(token);

        Component message = Component.text(link.toString(), NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("DO NOT SHARE THIS LINK AS IT COwNTAINS YOUR PRIVATE TOKEN", NamedTextColor.RED)))
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, link.toString()));
        return message;
    }
}
