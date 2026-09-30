package me.cobbleBet.listeners;

import me.cobbleBet.Main;
import me.cobbleBet.visuals.GamblingIndicatorManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class GamblingPageActivityListener implements Listener {
    private final GamblingIndicatorManager manager;

    public GamblingPageActivityListener(GamblingIndicatorManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld() || from.distanceSquared(to) < 0.0001) return;
        manager.onPlayerMove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.onPlayerJoin(event.getPlayer());
        Player player = event.getPlayer();
        Main plugin = Main.getInstance();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.notifyAdminAboutPluginUpdate(player), 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        manager.onPlayerTeleport(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        manager.onPlayerQuit(event.getEntity());
    }
}
