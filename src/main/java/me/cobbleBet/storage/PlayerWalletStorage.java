package me.cobbleBet.storage;

import me.cobbleBet.Main;
import me.cobbleBet.players.PlayerWallet;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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

    // =========================
    // LOAD ALL
    // =========================
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

            // =========================
            // LOAD VAULT BALANCE
            // =========================
            if (config.contains("vault")) {
                wallet.setVaultRaw(config.getDouble("vault"));
            }

            // =========================
            // LOAD ITEM BALANCES
            // =========================
            if (config.contains("items")) {

                for (String key : config.getConfigurationSection("items").getKeys(false)) {

                    try {
                        Material mat = Material.valueOf(key.toUpperCase());
                        double amount = config.getDouble("items." + key);

                        wallet.setItemBalance(mat, amount);

                    } catch (IllegalArgumentException ignored) {
                        // skip invalid materials safely
                    }
                }
            }

            Main.playerWalletHashMap.put(uuid, wallet);
        }
    }

    // =========================
    // SAVE ALL
    // =========================
    public void saveAll() {

        for (Map.Entry<UUID, PlayerWallet> entry : Main.playerWalletHashMap.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    // =========================
    // SAVE SINGLE
    // =========================
    private void save(UUID uuid, PlayerWallet wallet) {

        File file = new File(folder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        // =========================
        // SAVE VAULT
        // =========================
        config.set("vault", wallet.getVaultRaw());

        // =========================
        // SAVE ITEMS
        // =========================
        for (Map.Entry<Material, Double> entry : wallet.getItemBalances().entrySet()) {

            if (entry.getValue() <= 0)
                continue;

            config.set("items." + entry.getKey().name(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}