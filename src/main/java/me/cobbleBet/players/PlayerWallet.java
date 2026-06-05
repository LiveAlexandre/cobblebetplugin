package me.cobbleBet.players;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class PlayerWallet {



    private final UUID playerUUID;
    private HashMap<Material, Integer> economyItemInWalletMap = new HashMap<Material, Integer>();
    private double economyCurrency;

    public PlayerWallet(Player player) {
        this.playerUUID=player.getUniqueId();
    }




    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public double getEconomyCurrency() {
        return this.economyCurrency;
    }

    public void setEconomyCurrency(double amount) {
        this.economyCurrency=amount;
    }

    public HashMap<Material, Integer> getEconomyItemInWalletMap() {
        return economyItemInWalletMap;
    }

    public void setEconomyItemInWalletMap(HashMap<Material, Integer> economyItemInWalletMap) {
        this.economyItemInWalletMap = economyItemInWalletMap;
    }
}
