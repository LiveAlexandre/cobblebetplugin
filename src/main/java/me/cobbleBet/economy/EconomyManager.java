package me.cobbleBet.economy;

import me.cobbleBet.Main;
import me.cobbleBet.players.PlayerWallet;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class EconomyManager {

    private final JavaPlugin plugin;
    private Economy vaultEconomy;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupVault();
    }

    private void setupVault() {

        if (!Main.economyType.equalsIgnoreCase("vault"))
            return;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found!");
            return;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("No Vault economy provider found!");
            return;
        }

        vaultEconomy = rsp.getProvider();
    }

    private PlayerWallet wallet(OfflinePlayer player) {
        return Main.playerWalletHashMap.computeIfAbsent(
                player.getUniqueId(),
                PlayerWallet::new
        );
    }

    // ==================================
    // WALLET BALANCE
    // ==================================

    public double getBalance(OfflinePlayer player) {
        return wallet(player).getBalance();
    }

    public boolean setBalance(OfflinePlayer player, double amount) {

        if (amount < 0)
            return false;

        PlayerWallet wallet = wallet(player);

        if (Main.economyType.equalsIgnoreCase("vault")) {
            wallet.setCurrency(amount);
        } else {
            wallet.setItemCurrency((int)amount);
        }

        return true;
    }

    // ==================================
    // DEPOSIT TO WALLET
    // ==================================

    public boolean deposit(Player player, double amount) {

        if (amount <= 0)
            return false;

        PlayerWallet wallet = wallet(player);

        if (Main.economyType.equalsIgnoreCase("vault")) {

            if (vaultEconomy == null)
                return false;

            if (!vaultEconomy.has(player, amount))
                return false;

            vaultEconomy.withdrawPlayer(player, amount);

            wallet.addCurrency((int) amount);
            return true;
        }

        int itemAmount = (int) amount;

        if (!removeFromInventory(player, Main.economyItem, itemAmount))
            return false;

        wallet.addCurrency(itemAmount);
        return true;
    }

    // ==================================
    // WITHDRAW FROM WALLET
    // ==================================

    public boolean withdraw(Player player, double amount) {

        if (amount <= 0)
            return false;

        PlayerWallet wallet = wallet(player);

        if (wallet.getBalance() < amount)
            return false;

        if (Main.economyType.equalsIgnoreCase("vault")) {

            if (vaultEconomy == null)
                return false;

            wallet.setCurrency(wallet.getBalance() - amount);

            vaultEconomy.depositPlayer(player, amount);
            return true;
        }

        int itemAmount = (int) amount;

        wallet.removeCurrency(itemAmount);

        giveItem(player, Main.economyItem, itemAmount);

        return true;
    }

    // ==================================
    // INVENTORY HELPERS
    // ==================================

    private boolean removeFromInventory(Player player, Material mat, int amount) {

        int total = 0;

        for (ItemStack item : player.getInventory().getContents()) {

            if (item == null || item.getType() != mat)
                continue;

            total += item.getAmount();
        }

        if (total < amount)
            return false;

        int remaining = amount;

        for (ItemStack item : player.getInventory().getContents()) {

            if (item == null || item.getType() != mat)
                continue;

            int stackAmount = item.getAmount();

            if (stackAmount <= remaining) {

                remaining -= stackAmount;
                item.setAmount(0);

            } else {

                item.setAmount(stackAmount - remaining);
                remaining = 0;
            }

            if (remaining <= 0)
                break;
        }

        player.updateInventory();
        return true;
    }

    private void giveItem(Player player, Material mat, int amount) {

        Map<Integer, ItemStack> leftover =
                player.getInventory().addItem(new ItemStack(mat, amount));

        if (!leftover.isEmpty()) {

            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(
                            player.getLocation(),
                            item
                    )
            );
        }

        player.updateInventory();
    }
}