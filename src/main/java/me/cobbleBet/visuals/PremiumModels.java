package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PremiumModels {
    private PremiumModels() {}

    public static boolean allowed(Player player, String style) {
        if (Main.premiumEnabled || "classic".equalsIgnoreCase(style) || "casino".equalsIgnoreCase(style)) return true;
        player.sendMessage("§6CobbleBet Plus is required for premium model skins. §fClassic §6is available for free.");
        return false;
    }

    public static <T> T loaded(T requested, T classic) {
        return Main.premiumEnabled ? requested : classic;
    }

    public static Set<String> styles(String... premiumStyles) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("classic");
        if (Main.premiumEnabled) for (String style : premiumStyles) result.add(style);
        return result;
    }
}
