package me.cobbleBet.listeners;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PluginUpdateListener implements Listener {
    private final Main plugin;

    public PluginUpdateListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.resetPluginUpdateNotification(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.notifyAdminAboutPluginUpdate(player), 60L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.notifyAdminAboutPluginUpdate(player), 200L);
    }
}
