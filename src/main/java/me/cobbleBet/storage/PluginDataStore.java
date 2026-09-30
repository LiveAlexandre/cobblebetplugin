package me.cobbleBet.storage;

import me.cobbleBet.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Stores generated identity, updater state, and physical game locations outside config.yml. */
public final class PluginDataStore {
    private static final List<String> LEGACY_DATA_PATHS = List.of(
            "serverId",
            "updater.pendingVersion",
            "physicalGames.coinflip.boards",
            "physicalGames.blackjack.tables",
            "physicalGames.mines.fields"
    );

    private final Main plugin;
    private final File file;
    private YamlConfiguration data;

    public PluginDataStore(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void migrateFromConfig(FileConfiguration config) {
        boolean dataChanged = false;
        boolean configChanged = false;
        for (String path : LEGACY_DATA_PATHS) {
            Object legacy = config.get(path);
            if (!data.contains(path) && hasValue(legacy)) {
                data.set(path, legacy);
                dataChanged = true;
            }
            if (config.contains(path)) {
                config.set(path, null);
                configChanged = true;
            }
        }
        if (config.contains("updater") && config.getConfigurationSection("updater") != null
                && config.getConfigurationSection("updater").getKeys(false).isEmpty()) {
            config.set("updater", null);
        }
        if (config.contains("physicalGames")) {
            config.set("physicalGames", null);
            configChanged = true;
        }
        if (dataChanged) save();
        if (configChanged) plugin.saveConfig();
        if (dataChanged || configChanged) plugin.getLogger().info("Moved generated CobbleBet state out of config.yml and into data.yml.");
    }

    public synchronized String getString(String path, String fallback) {
        return data.getString(path, fallback);
    }

    public synchronized List<Map<?, ?>> getMapList(String path) {
        return data.getMapList(path);
    }

    public synchronized void set(String path, Object value) {
        data.set(path, value);
        save();
    }

    private boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof String string) return !string.isBlank();
        if (value instanceof List<?> list) return !list.isEmpty();
        return true;
    }

    private void save() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Path temporary = Files.createTempFile(plugin.getDataFolder().toPath(), "cobblebet-data-", ".tmp");
            try {
                Files.writeString(temporary, data.saveToString(), StandardCharsets.UTF_8);
                moveWithRetry(temporary, file.toPath());
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not save CobbleBet data.yml", error);
        }
    }

    private void moveWithRetry(Path source, Path destination) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                try {
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException error) {
                last = error;
                try {
                    Thread.sleep(40L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
        throw last;
    }
}
