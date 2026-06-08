package me.cobbleBet.players;

import me.cobbleBet.Main;
import me.cobbleBet.storage.PlayerWalletStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class PlayerWallet {

    private final UUID playerUUID;

    private HashMap<Material, Integer> economyItemInWalletMap = new HashMap<>();
    private double currency;


    public PlayerWallet(UUID uuid) {
        this.playerUUID=uuid;
    }

    public double getBalance() {

        if(Main.economyType.equalsIgnoreCase("vault")) {
            return currency;
        }

        if(Main.economyType.equalsIgnoreCase("item")) {
            return economyItemInWalletMap.getOrDefault(Main.economyItem, 0);
        }

        return 0;
    }



    public void addCurrency(double amount) {

        if(Main.economyType.equalsIgnoreCase("vault")) {
            currency += amount;
            return;
        }

        economyItemInWalletMap.merge(
                Main.economyItem,
                (int) amount,
                Integer::sum
        );
    }

    public void setCurrency(double amount) {

    }

    public void removeCurrency(double amount) {

        if(Main.economyType.equalsIgnoreCase("vault")) {
            currency = Math.max(0, currency - amount);
            return;
        }

        int current = economyItemInWalletMap.getOrDefault(Main.economyItem, 0);

        economyItemInWalletMap.put(
                Main.economyItem,
                Math.max(0, current - (int) amount)
        );
    }
    public OfflinePlayer getPlayer() {
        return Bukkit.getOfflinePlayer(this.playerUUID);
    }
    public void setEconomyItemInWalletMap(HashMap<Material, Integer> map) {
        this.economyItemInWalletMap=map;
    }
    public HashMap<Material, Integer> getEconomyItemInWalletMap() {
        return this.economyItemInWalletMap;
    }
    public UUID getPlayerUUID() {
        return this.playerUUID;
    }
    public double getEcoCurrency() {
        return this.currency;
    }
    public void setEcoCurrency(double amount) {
        this.currency=amount;
    }
    public void setItemCurrency(int amount) {
        this.economyItemInWalletMap.put(Main.economyItem, amount);
    }
}