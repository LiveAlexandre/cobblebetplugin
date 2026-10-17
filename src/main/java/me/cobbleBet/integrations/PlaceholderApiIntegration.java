package me.cobbleBet.integrations;

import me.cobbleBet.Main;
import org.bukkit.Bukkit;

/** Keeps PlaceholderAPI optional and prevents its classes from loading when it is absent. */
public final class PlaceholderApiIntegration {
    private PlaceholderApiIntegration() {}

    public static void register(Main plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI not found; CobbleBet placeholders are disabled.");
            return;
        }

        try {
            boolean registered = new CobbleBetPlaceholderExpansion(plugin).register();
            if (registered) plugin.getLogger().info("Registered PlaceholderAPI expansion: %cobblebet_*%.");
            else plugin.getLogger().warning("PlaceholderAPI rejected the CobbleBet expansion registration.");
        } catch (LinkageError error) {
            plugin.getLogger().warning("PlaceholderAPI was detected but its expansion API could not be loaded: " + error.getMessage());
        }
    }
}
