package me.cobbleBet.listeners;

import me.cobbleBet.Main;
import me.cobbleBet.config.PublicJoinAddress;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

/** Learns the address that players actually use instead of guessing from the outbound web connection. */
public final class JoinAddressDetectionListener implements Listener {
    private final Main plugin;

    public JoinAddressDetectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        InetSocketAddress virtualHost = virtualHost(event.getPlayer());
        String hostname = virtualHost == null ? event.getHostname() : virtualHost.getHostString();
        int port = virtualHost == null ? detectedAllocationPort() : virtualHost.getPort();
        Bukkit.getScheduler().runTask(plugin,
                () -> PublicJoinAddress.observePlayerHandshake(plugin, hostname, port));
    }

    private InetSocketAddress virtualHost(Player player) {
        try {
            Method method = player.getClass().getMethod("getVirtualHost");
            Object value = method.invoke(player);
            return value instanceof InetSocketAddress address ? address : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private int detectedAllocationPort() {
        for (String name : new String[]{"SERVER_PORT", "P_SERVER_PORT", "PUBLIC_PORT", "EXTERNAL_PORT",
                "ALLOCATED_PORT", "PRIMARY_PORT", "MINECRAFT_SERVER_PORT", "MINECRAFT_PORT", "MC_PORT", "PANEL_PORT"}) {
            try {
                int port = Integer.parseInt(String.valueOf(System.getenv(name)).trim());
                if (port >= 1 && port <= 65535) return port;
            } catch (NumberFormatException ignored) {}
        }
        return Bukkit.getPort();
    }
}
