package me.cobbleBet.connections.listeners;

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

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public class SocketTokenResponseListener extends SocketMessageListener {

    private static final ConcurrentMap<UUID, Long> PENDING = new ConcurrentHashMap<>();

    public SocketTokenResponseListener(String command, CobbleSocketClient client) {
        super(command, client);
    }

    @Override
    public void trigger(JsonObject json) {
        if (!json.has("token") || !json.has("playerUUID")) {
            Main.getInstance().getLogger().warning("CobbleBet returned an incomplete login-link response.");
            return;
        }
        final String token;
        final UUID uuid;
        try {
            token = json.get("token").getAsString();
            uuid = UUID.fromString(json.get("playerUUID").getAsString());
        } catch (RuntimeException error) {
            Main.getInstance().getLogger().log(Level.WARNING, "CobbleBet returned an invalid login-link response.", error);
            return;
        }
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            PENDING.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                Main.getInstance().getLogger().warning("CobbleBet generated a login link, but the requesting player is no longer online: " + uuid);
                return;
            }
            Component message = getLink(json, token);
            player.sendMessage(Component.text("Click this private link to open CobbleBet: ", NamedTextColor.GRAY).append(message));
        });
    }

    public static void expect(Player player) {
        UUID uuid = player.getUniqueId();
        long request = System.nanoTime();
        PENDING.put(uuid, request);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!PENDING.remove(uuid, request) || !player.isOnline()) return;
            player.sendMessage(Component.text("CobbleBet did not return a login link. Use /cobblebet panel to check the connection, then try /gamble again.", NamedTextColor.RED));
        }, 20L * 10);
    }

    public static void cancel(UUID uuid) {
        PENDING.remove(uuid);
    }

    private static @NotNull Component getLink(JsonObject json, String token) {
        String link = responseUrl(json);
        if (link == null) {
            link = (Main.testMode ? "http://localhost:8908/auth/magic-login?token="
                    : "https://cobblebet.com/auth/magic-login?token=") + token;
        }

        Component message = Component.text(link, NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Private one-use link. Do not share it.", NamedTextColor.RED)))
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, link));
        return message;
    }

    private static String responseUrl(JsonObject json) {
        if (!json.has("url")) return null;
        try {
            URI uri = URI.create(json.get("url").getAsString());
            boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (Main.testMode && "http".equalsIgnoreCase(uri.getScheme()));
            return allowedScheme && uri.getHost() != null ? uri.toString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
