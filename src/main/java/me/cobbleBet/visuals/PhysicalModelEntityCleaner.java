package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/** Removes stale physical-game entities before rebuilding them. */
public final class PhysicalModelEntityCleaner {
    private static final String MARKER = "physical_model";
    private static final List<ModelType> TYPES = List.of(
            new ModelType("physicalGames.coinflip.boards", "coinflip_board", 3.0, 4.5),
            new ModelType("physicalGames.blackjack.tables", "blackjack_table", 3.0, 3.0),
            new ModelType("physicalGames.mines.fields", "mines_field", 3.5, 4.5),
            new ModelType("physicalGames.plinko.boards", "plinko_board", 3.5, 6.0),
            new ModelType("physicalGames.roulette.tables", "roulette_table_action", 4.0, 3.0)
    );

    private PhysicalModelEntityCleaner() {}

    public static void mark(Main plugin, Entity entity, String model) {
        if (entity != null) {
            entity.getPersistentDataContainer().set(markerKey(plugin), PersistentDataType.STRING, model);
        }
    }

    public static void mark(Main plugin, Iterable<? extends Entity> entities, String model) {
        for (Entity entity : entities) mark(plugin, entity, model);
    }

    public static int removeStale(Main plugin) {
        NamespacedKey marker = markerKey(plugin);
        int removed = 0;

        // Newer entities have an explicit marker and are always safe to remove.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }

        // Versions 1.5.0-1.5.2 persisted displays without a shared marker. Their
        // interaction entity is the safe anchor proving the surrounding displays
        // belong to a configured CobbleBet model.
        for (ModelType type : TYPES) {
            NamespacedKey legacyKey = new NamespacedKey(plugin, type.legacyKey());
            for (Map<?, ?> row : plugin.getDataStore().getMapList(type.dataPath())) {
                try {
                    World world = Bukkit.getWorld(String.valueOf(row.get("world")));
                    String id = String.valueOf(row.get("id"));
                    if (world == null || id.isBlank()) continue;
                    Location origin = new Location(world, number(row.get("x")), number(row.get("y")), number(row.get("z")));
                    origin.getChunk().load();
                    var nearby = world.getNearbyEntities(origin, type.radius(), type.height(), type.radius());
                    boolean legacyAnchor = nearby.stream().anyMatch(entity -> {
                        String value = entity.getPersistentDataContainer().get(legacyKey, PersistentDataType.STRING);
                        if (value == null) return false;
                        for (String part : value.split(":")) if (part.equalsIgnoreCase(id)) return true;
                        return false;
                    });
                    if (!legacyAnchor) continue;
                    for (Entity entity : nearby) {
                        if (entity instanceof Display || entity instanceof Interaction) {
                            entity.remove();
                            removed++;
                        }
                    }
                } catch (RuntimeException error) {
                    plugin.getLogger().warning("Could not clean a legacy physical model: " + error.getMessage());
                }
            }
        }
        return removed;
    }

    private static NamespacedKey markerKey(Main plugin) {
        return new NamespacedKey(plugin, MARKER);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private record ModelType(String dataPath, String legacyKey, double radius, double height) {}
}
