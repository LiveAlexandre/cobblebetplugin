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
import java.util.logging.Level;

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
            boolean allowedScheme = "https".equalsIgnoreCase(parsedUrl.getScheme())
                    || (Main.testMode && "http".equalsIgnoreCase(parsedUrl.getScheme()));
            if (!allowedScheme || parsedUrl.getHost() == null) {
                return;
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
            try {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    acknowledgeResponse(json);
                    return;
                }

                Component link = Component.text(panelUrl, NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.openUrl(panelUrl));
                player.sendMessage(Component.text("Your CobbleBet admin panel link (one use, expires in 5 minutes): ", NamedTextColor.GREEN)
                        .append(link));
                acknowledgeResponse(json);
            } catch (RuntimeException error) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Received a CobbleBet admin link but could not deliver it to " + playerId + ".", error);
            }
        });
    }
}
