package me.cobbleBet.listeners;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

/** Rebuilds CobbleBet's display entities after third-party /killall commands remove them. */
public final class PhysicalModelProtectionListener implements Listener {
    private final Main plugin;
    private boolean rebuildScheduled;

    public PhysicalModelProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        protect(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        protect(event.getCommand());
    }

    private void protect(String commandLine) {
        if (!isKillAll(commandLine) || rebuildScheduled) return;
        rebuildScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                plugin.reloadPhysicalModels();
                plugin.getLogger().info("Restored CobbleBet 3D models after /killall.");
            } finally {
                rebuildScheduled = false;
            }
        });
    }

    static boolean isKillAll(String commandLine) {
        if (commandLine == null) return false;
        String value = commandLine.trim();
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isEmpty()) return false;
        String command = value.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = command.lastIndexOf(':');
        if (namespace >= 0) command = command.substring(namespace + 1);
        return command.equals("killall");
    }
}
