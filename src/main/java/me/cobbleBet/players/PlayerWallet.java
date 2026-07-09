package me.cobbleBet.players;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.UUID;

public class PlayerWallet {

    private final UUID playerUUID;

    // ==================================
    // VAULT BALANCE (economy plugin money)
    // ==================================
    private double vaultBalance = 0.0;

    // ==================================
    // ITEM BALANCES (material-based economy)
    // ==================================
    private final HashMap<Material, Double> itemBalances = new HashMap<>();

    public PlayerWallet(UUID uuid) {
        this.playerUUID = uuid;
    }

    // ==================================
    // GET BALANCE
    // ==================================

    public double getBalance() {

        if (Main.economyType.equalsIgnoreCase("vault")) {
            return vaultBalance;
        }

        return itemBalances.getOrDefault(Main.economyItem, 0.0);
    }

    public double getVaultBalance() {
        return vaultBalance;
    }

    public double getItemBalance(Material material) {
        return itemBalances.getOrDefault(material, 0.0);
    }

    // ==================================
    // SET BALANCE
    // ==================================

    public void setCurrency(double amount) {

        if (Main.economyType.equalsIgnoreCase("vault")) {
            vaultBalance = Math.max(0, amount);
            return;
        }

        itemBalances.put(Main.economyItem, Math.max(0, amount));
    }

    public void setVaultBalance(double amount) {
        vaultBalance = Math.max(0, amount);
    }

    public void setItemBalance(Material material, double amount) {
        itemBalances.put(material, Math.max(0, amount));
    }

    // ==================================
    // ADD
    // ==================================

    public void addCurrency(double amount) {

        if (Main.economyType.equalsIgnoreCase("vault")) {
            vaultBalance += amount;
            return;
        }

        itemBalances.put(
                Main.economyItem,
                getBalance() + amount
        );
    }

    public void addVault(double amount) {
        vaultBalance += amount;
    }

    public void addItem(Material material, double amount) {
        itemBalances.put(material, getItemBalance(material) + amount);
    }

    // ==================================
    // REMOVE
    // ==================================

    public void removeCurrency(double amount) {

        if (Main.economyType.equalsIgnoreCase("vault")) {
            vaultBalance = Math.max(0, vaultBalance - amount);
            return;
        }

        itemBalances.put(
                Main.economyItem,
                Math.max(0, getBalance() - amount)
        );
    }

    public void removeVault(double amount) {
        vaultBalance = Math.max(0, vaultBalance - amount);
    }

    public void removeItem(Material material, double amount) {
        itemBalances.put(
                material,
                Math.max(0, getItemBalance(material) - amount)
        );
    }

    // ==================================
    // PLAYER
    // ==================================

    public OfflinePlayer getPlayer() {
        return Bukkit.getOfflinePlayer(playerUUID);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    // ==================================
    // STORAGE ACCESS
    // ==================================

    public HashMap<Material, Double> getItemBalances() {
        return itemBalances;
    }

    public void setItemBalances(HashMap<Material, Double> map) {
        itemBalances.clear();
        itemBalances.putAll(map);
    }

    public double getVaultRaw() {
        return vaultBalance;
    }

    public void setVaultRaw(double amount) {
        this.vaultBalance = amount;
    }
}