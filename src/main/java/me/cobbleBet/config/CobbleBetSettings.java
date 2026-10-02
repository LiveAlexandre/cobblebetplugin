package me.cobbleBet.config;

import me.cobbleBet.Main;
import org.bukkit.Material;

public final class CobbleBetSettings {
    private CobbleBetSettings() {}

    public static void load(Main plugin) {
        Main.economyType = plugin.getConfig().getString("economyType", "vault");
        String itemName = plugin.getConfig().getString("economyItem", "DIAMOND");
        try {
            Main.economyItem = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException error) {
            Main.economyItem = Material.DIAMOND;
            plugin.getLogger().warning("Invalid economyItem, defaulting to DIAMOND");
        }

        Main.gambleCommandNeedsPermission = plugin.getConfig().getBoolean("gambleCommandNeedsPermission", false);
        Main.vaultCurrencyName = plugin.getConfig().getString("vaultCurrencyName", "");
        Main.maximumBalance = plugin.getConfig().getLong("maximumBalance", 1_000_000_000L);
        Main.cobblebetToken = plugin.getConfig().getString("cobblebetToken", "");
        Main.serverDisplayName = plugin.getConfig().getString("serverName", "").trim();
        Main.serverId = plugin.getDataStore().getString("serverId", "").trim();
        Main.premiumEnabled = plugin.getConfig().getBoolean("premiumEnabled", false);
        Main.broadcastingEnabled = plugin.getConfig().getBoolean("broadcastingEnabled", true);
        Main.broadcastPrefix = plugin.getConfig().getString("broadcastPrefix", "<blue><bold>CobbleBet</bold> »");
        Main.bigWinThreshold = plugin.getConfig().getLong("bigWinThreshold", 500L);

        Main.broadcastEvents.clear();
        if (plugin.getConfig().isConfigurationSection("broadcastEvents")) {
            for (String key : plugin.getConfig().getConfigurationSection("broadcastEvents").getKeys(false)) {
                Main.broadcastEvents.put(key, plugin.getConfig().getBoolean("broadcastEvents." + key));
            }
        }

        Main.testMode = plugin.getConfig().getBoolean("debug.testMode", false);
        Main.autoUpdateEnabled = plugin.getConfig().getBoolean("autoUpdate", true);
        if (plugin.gamblingIndicatorManager != null) plugin.gamblingIndicatorManager.reloadSettings();
        if (plugin.getEconomyManager() != null) plugin.getEconomyManager().refreshVaultCurrencyName();

        plugin.getLogger().info("Settings loaded: economy=" + Main.economyType
                + ", autoUpdate=" + Main.autoUpdateEnabled
                + ", broadcasts=" + Main.broadcastingEnabled
                + ", testMode=" + Main.testMode + ".");
    }
}
