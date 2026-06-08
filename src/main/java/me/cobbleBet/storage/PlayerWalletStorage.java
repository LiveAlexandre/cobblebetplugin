package me.cobbleBet.storage;

import me.cobbleBet.Main;
import me.cobbleBet.players.PlayerWallet;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerWalletStorage {

    private final File folder;

    public PlayerWalletStorage(File dataFolder) {
        this.folder = new File(dataFolder, "wallets");

        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    // -------------------------
    // LOAD ALL (onEnable)
    // -------------------------
    public void loadAll() {

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {

            if (!file.getName().endsWith(".yml")) continue;

            UUID uuid;

            try {
                uuid = UUID.fromString(file.getName().replace(".yml", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            PlayerWallet wallet = new PlayerWallet(uuid);


            HashMap<Material, Integer> items = new HashMap<>();

            if (config.contains("items")) {
                for (String key : config.getConfigurationSection("items").getKeys(false)) {
                    Material mat = Material.valueOf(key);
                    int amount = config.getInt("items." + key);
                    items.put(mat, amount);
                }
            }
            if(config.contains("ecoCurrency")) {
                double ecoCurrency = config.getDouble("ecoCurrency");
                wallet.setEcoCurrency(ecoCurrency);

            }

            wallet.setEconomyItemInWalletMap(items);

            Main.playerWalletHashMap.put(uuid, wallet);
        }
    }

    // -------------------------
    // SAVE ALL (onDisable)
    // -------------------------
    public void saveAll() {

        for (Map.Entry<UUID, PlayerWallet> entry : Main.playerWalletHashMap.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    // -------------------------
    // SAVE SINGLE
    // -------------------------
    private void save(UUID uuid, PlayerWallet wallet) {

        File file = new File(folder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();


        for (Map.Entry<Material, Integer> entry : wallet.getEconomyItemInWalletMap().entrySet()) {
            config.set("items." + entry.getKey().name(), entry.getValue());
        }

        config.set("ecoCurrency", wallet.getEcoCurrency());

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}