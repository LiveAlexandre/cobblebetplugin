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
import java.util.logging.Level;

public class EconomyManager {

    private final JavaPlugin plugin;
    private Economy vaultEconomy;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupVault();
    }

    public String applySettings(String requestedType, String requestedItem) {
        String type = requestedType == null ? "" : requestedType.trim().toLowerCase();
        if (!type.equals("vault") && !type.equals("item")) {
            return "Choose Vault or item currency.";
        }

        Material item = Main.economyItem;
        Economy nextVault = null;
        if (type.equals("vault")) {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return "Vault is not installed. Install Vault and a Vault-compatible economy plugin first.";
            }
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null || rsp.getProvider() == null) {
                return "No Vault economy provider is active. Check that your economy plugin supports Vault.";
            }
            nextVault = rsp.getProvider();
        } else {
            item = Material.matchMaterial(requestedItem == null ? "" : requestedItem.trim());
            if (item == null || item.isAir() || !item.isItem()) {
                return "That Minecraft item is not valid. Try a material name such as DIAMOND or EMERALD.";
            }
        }

        String oldType = plugin.getConfig().getString("economyType", "vault");
        String oldItem = plugin.getConfig().getString("economyItem", "DIAMOND");
        plugin.getConfig().set("economyType", type);
        if (type.equals("item")) plugin.getConfig().set("economyItem", item.name());
        try {
            plugin.saveConfig();
        } catch (RuntimeException error) {
            plugin.getConfig().set("economyType", oldType);
            plugin.getConfig().set("economyItem", oldItem);
            return "Could not save plugins/CobbleBet/config.yml. Check the server file permissions.";
        }

        vaultEconomy = nextVault;
        Main.loadConfigValues();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBalanceToServer(player, getBalance(player));
        }
        Main.getInstance().sendServerStatus();
        return null;
    }
    private void setupVault() {

        plugin.getLogger().log(Level.INFO, "registered economy Type: " + Main.economyType);
        if (!Main.economyType.equalsIgnoreCase("vault")) {
            refreshVaultCurrencyName();
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found!");
            refreshVaultCurrencyName();
            return;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("No Vault economy provider found!");
            refreshVaultCurrencyName();
            return;
        }

        vaultEconomy = rsp.getProvider();
        refreshVaultCurrencyName();

        plugin.getLogger().log(Level.INFO, "Registered Vault Into CobbleBet Economy!");
    }

    public void refreshVaultCurrencyName() {
        if (Main.vaultCurrencyName != null
                && !Main.vaultCurrencyName.isBlank()
                && !Main.vaultCurrencyName.equalsIgnoreCase("Coins")) {
            return;
        }

        if (vaultEconomy != null) {
            String providerCurrencyName = vaultEconomy.currencyNamePlural();
            if (providerCurrencyName == null || providerCurrencyName.isBlank()) {
                providerCurrencyName = vaultEconomy.currencyNameSingular();
            }
            Main.vaultCurrencyName = providerCurrencyName == null || providerCurrencyName.isBlank()
                    ? "Coins"
                    : providerCurrencyName;
        } else {
            Main.vaultCurrencyName = "Coins";
        }
    }

    private PlayerWallet wallet(OfflinePlayer player) {
        return Main.getWallet(player.getUniqueId());
    }

    // ==================================
    // WALLET BALANCE
    // ==================================

    public double getBalance(OfflinePlayer player) {
        return wallet(player).getBalance();
    }

    private void sendBalanceToServer(OfflinePlayer player, double balance) {
        if (Main.getInstance().cobbleSocketClient != null) {
            Main.getInstance().cobbleSocketClient.sendPlayerBalance(player, balance);
        }
    }

    public boolean setBalance(OfflinePlayer player, double amount) {

        if (amount < 0)
            return false;

        PlayerWallet wallet = wallet(player);

        if (Main.economyType.equalsIgnoreCase("vault")) {
            wallet.setCurrency(amount);
            plugin.getLogger().info("ECO: set " + player.getName() + "'s eco balance to: " + amount);
        } else {
            wallet.setCurrency(amount);
            plugin.getLogger().info("ECO: set " + player.getName() + "'s " + Main.economyItem.name() + " balance to: " + amount);
        }

        sendBalanceToServer(player, wallet.getBalance());
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

            wallet.addCurrency(amount);
            sendBalanceToServer(player, wallet.getBalance());
            return true;
        }

        int itemAmount = (int) amount;

        if (!removeFromInventory(player, Main.economyItem, itemAmount))
            return false;

        wallet.addCurrency(itemAmount);
        sendBalanceToServer(player, wallet.getBalance());
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

            wallet.removeCurrency(amount);

            vaultEconomy.depositPlayer(player, amount);
            sendBalanceToServer(player, wallet.getBalance());
            return true;
        }

        int itemAmount = (int) amount;

        wallet.removeCurrency(itemAmount);

        giveItem(player, Main.economyItem, itemAmount);

        sendBalanceToServer(player, wallet.getBalance());
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
