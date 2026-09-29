package me.cobbleBet.connections.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class SocketAccountLinkCodeListener extends SocketMessageListener {
    public SocketAccountLinkCodeListener(String type, CobbleSocketClient client) {
        super(type, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!client.isApproved() || !json.has("playerUUID")) return;
        try {
            UUID playerId = UUID.fromString(json.get("playerUUID").getAsString());
            if (me.cobbleBet.Main.getInstance().pendingAccountLinkRequests.remove(playerId) == null) return;
            if (json.has("error")) {
                String message = json.get("error").getAsString();
                Bukkit.getScheduler().runTask(me.cobbleBet.Main.getInstance(), () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) player.sendMessage(Component.text(message, NamedTextColor.RED));
                });
                return;
            }
            if (!json.has("code")) return;
            String code = json.get("code").getAsString().replaceAll("[^A-Fa-f0-9]", "").toUpperCase();
            if (code.length() != 8) return;
            Bukkit.getScheduler().runTask(me.cobbleBet.Main.getInstance(), () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) return;
                Component value = Component.text(code, NamedTextColor.LIGHT_PURPLE)
                        .clickEvent(ClickEvent.copyToClipboard(code))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY)));
                player.sendMessage(Component.text("CobbleBet link code: ", NamedTextColor.GRAY).append(value));
                player.sendMessage(Component.text("Open Player profile on CobbleBet, paste this code, and choose Link server. It expires in 10 minutes.", NamedTextColor.DARK_GRAY));
            });
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed account link replies.
        }
    }
}
