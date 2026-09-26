package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.UUID;

public class SocketAdminPanelResponseListener extends SocketMessageListener {

    public SocketAdminPanelResponseListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("playerUUID") || !json.has("url")) {
            return;
        }

        UUID playerId;
        String url;
        try {
            playerId = UUID.fromString(json.get("playerUUID").getAsString());
            url = json.get("url").getAsString();
            URI parsedUrl = URI.create(url);
            if (!"https".equalsIgnoreCase(parsedUrl.getScheme()) || parsedUrl.getHost() == null) {
                return;
            }
            if (Main.testMode) {
                String localPath = parsedUrl.getRawPath();
                if (parsedUrl.getRawQuery() != null) {
                    localPath += "?" + parsedUrl.getRawQuery();
                }
                url = URI.create("http://localhost:8908").resolve(localPath).toString();
            }
        } catch (IllegalArgumentException e) {
            return;
        }

        Long expiresAt = Main.getInstance().pendingAdminPanelRequests.remove(playerId);
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            return;
        }

        String panelUrl = url;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }

            Component link = Component.text(panelUrl, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.openUrl(panelUrl));
            player.sendMessage(Component.text("Your CobbleBet admin panel link (one use, expires in 5 minutes): ", NamedTextColor.GREEN)
                    .append(link));
        });
    }
}
