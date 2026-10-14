package me.cobbleBet.listeners;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class OwnerActivityListener implements Listener {
    private final Main plugin;
    public OwnerActivityListener(Main plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { send("join", event.getPlayer().getUniqueId().toString(), event.getPlayer().getName(), "Joined the server"); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) { send("quit", event.getPlayer().getUniqueId().toString(), event.getPlayer().getName(), "Left the server"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void chat(AsyncPlayerChatEvent event) { send("chat", event.getPlayer().getUniqueId().toString(), event.getPlayer().getName(), event.getMessage()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void command(PlayerCommandPreprocessEvent event) { send("command", event.getPlayer().getUniqueId().toString(), event.getPlayer().getName(), redact(event.getMessage())); }

    private void send(String kind, String uuid, String name, String message) {
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "ownerActivity"); event.addProperty("kind", kind);
        event.addProperty("playerUUID", uuid); event.addProperty("playerName", name);
        event.addProperty("message", message.substring(0, Math.min(300, message.length())));
        event.addProperty("timestamp", System.currentTimeMillis());
        try { plugin.cobbleSocketClient.send(event.toString()); } catch (RuntimeException ignored) {}
    }

    private static String redact(String command) {
        String first = command.trim().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        return first.matches("/?(login|register|changepassword|password|cobblebetkey)") ? first + " [redacted]" : command;
    }
}
