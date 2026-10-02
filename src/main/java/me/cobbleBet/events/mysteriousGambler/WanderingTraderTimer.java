package me.cobbleBet.events.mysteriousGambler;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WanderingTraderTimer extends BukkitRunnable {
    private final Main plugin;
    private final Map<UUID, Long> lastTriggeredAt = new HashMap<>();

    public WanderingTraderTimer(Main plugin) {
        this.plugin = plugin;
        migrateTimingDefaults();
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("events.wanderingGambler.enabled", true)) return;
        if (WanderingGamblerService.isActive()) return;

        double chance = Math.max(0.0, Math.min(100.0,
                plugin.getConfig().getDouble("events.wanderingGambler.chancePercent", 2.5)));
        long cooldownMillis = Math.max(1L,
                plugin.getConfig().getLong("events.wanderingGambler.cooldownMinutes", 30L)) * 60_000L;
        long now = System.currentTimeMillis();

        List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> now - lastTriggeredAt.getOrDefault(player.getUniqueId(), 0L) >= cooldownMillis)
                .filter(player -> WanderingGamblerService.findVillage(player) != null)
                .toList();

        if (!candidates.isEmpty() && ThreadLocalRandom.current().nextDouble(100.0) < chance) {
            Player player = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            if (WanderingGamblerService.startFor(player) == WanderingGamblerService.StartResult.STARTED) {
            lastTriggeredAt.put(player.getUniqueId(), now);
            }
        }
        lastTriggeredAt.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void migrateTimingDefaults() {
        String versionPath = "events.wanderingGambler.timingVersion";
        if (plugin.getConfig().contains(versionPath, true)) return;
        String chancePath = "events.wanderingGambler.chancePercent";
        if (Math.abs(plugin.getConfig().getDouble(chancePath, 20.0) - 20.0) < 0.0001) {
            plugin.getConfig().set(chancePath, 2.5);
        }
        plugin.getConfig().set(versionPath, 2);
        plugin.saveConfig();
    }
}
