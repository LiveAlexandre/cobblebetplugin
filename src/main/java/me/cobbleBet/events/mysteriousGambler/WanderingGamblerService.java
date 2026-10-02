package me.cobbleBet.events.mysteriousGambler;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;

public final class WanderingGamblerService {
    public enum StartResult { STARTED, NOT_IN_VILLAGE, ALREADY_ACTIVE }

    private WanderingGamblerService() {}

    public static StartResult startFor(Player player) {
        if (isActive()) return StartResult.ALREADY_ACTIVE;
        GeneratedStructure village = findVillage(player);
        if (village == null) return StartResult.NOT_IN_VILLAGE;
        WanderingTraderEvent event = new WanderingTraderEvent(player.getWorld(), village);
        Main.activeEvents.put(event.id(), event);
        Bukkit.getPluginManager().registerEvents(event, Main.getInstance());
        long durationMinutes = Math.max(1L, Main.getInstance().getConfig()
                .getLong("events.wanderingGambler.durationMinutes", 5L));
        new MysteriousGamblerDeadLine(event.id()).runTaskLater(
                Main.getInstance(), durationMinutes * 60L * 20L);
        return StartResult.STARTED;
    }

    public static boolean isActive() {
        return Main.activeEvents.values().stream().anyMatch(WanderingTraderEvent.class::isInstance);
    }

    public static void stopAll() {
        Main.activeEvents.values().stream()
                .filter(WanderingTraderEvent.class::isInstance)
                .map(WanderingTraderEvent.class::cast)
                .toList()
                .forEach(WanderingTraderEvent::finish);
    }

    public static GeneratedStructure findVillage(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        // World#getStructures expects chunk coordinates. Passing block coordinates
        // made the lookup miss the village for nearly every player position.
        return world.getStructures(location.getBlockX() >> 4, location.getBlockZ() >> 4).stream()
                .filter(structure -> isVillage(structure.getStructure()))
                .filter(structure -> structure.getBoundingBox().contains(
                        player.getX(), player.getY(), player.getZ()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isVillage(Structure structure) {
        return structure == Structure.VILLAGE_PLAINS
                || structure == Structure.VILLAGE_DESERT
                || structure == Structure.VILLAGE_SAVANNA
                || structure == Structure.VILLAGE_SNOWY
                || structure == Structure.VILLAGE_TAIGA;
    }
}
