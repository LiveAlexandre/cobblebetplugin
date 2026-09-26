package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GamblingIndicatorManager {

    private static final String[] ANIMATION_FRAMES = {
            "✦  GAMBLING  ✦",
            "✧  GAMBLING  ✧",
            "★  GAMBLING  ★",
            "🎰  GAMBLING  🎰"
    };
    private static final NamedTextColor[] ANIMATION_COLORS = {
            NamedTextColor.GOLD,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE
    };

    private final JavaPlugin plugin;
    private final Map<UUID, Indicator> indicators = new HashMap<>();

    public GamblingIndicatorManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startGambling(Player player) {
        clear(player.getUniqueId());

        Indicator indicator = createIndicator(player, true);
        if (indicator != null) {
            indicators.put(player.getUniqueId(), indicator);
        }
    }

    public void stopGambling(Player player) {
        if (player != null) clear(player.getUniqueId());
    }

    public void showResult(UUID playerId, boolean won, double amount, String currency) {
        if (!Double.isFinite(amount) || amount < 0) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        Indicator indicator = indicators.get(playerId);
        if (indicator == null || !indicator.display.isValid()) {
            clear(playerId);
            indicator = createIndicator(player, false);
            if (indicator == null) {
                return;
            }
            indicators.put(playerId, indicator);
        }

        String currencyName = currency == null || currency.isBlank()
                ? Main.vaultCurrencyName
                : currency;
        if (currencyName == null || currencyName.isBlank()) {
            currencyName = "Coins";
        }

        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        String sign = won ? "+" : "-";
        indicator.result = Component.text(sign + format.format(amount) + " " + currencyName,
                won ? NamedTextColor.GREEN : NamedTextColor.RED);
        indicator.resultTicksRemaining = 60;
    }

    public void clearAll() {
        for (UUID playerId : indicators.keySet().toArray(UUID[]::new)) {
            clear(playerId);
        }
    }

    private Indicator createIndicator(Player player, boolean stopWhenMoved) {
        Location location = displayLocation(player);
        TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setLineWidth(240);
            entity.setViewRange(32.0f);
            entity.setPersistent(false);
            entity.text(Component.text(ANIMATION_FRAMES[0], ANIMATION_COLORS[0]));
        });

        Indicator indicator = new Indicator(display, player.getLocation(), stopWhenMoved);
        indicator.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player.getUniqueId(), indicator), 1L, 2L);
        return indicator;
    }

    private void tick(UUID playerId, Indicator indicator) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !indicator.display.isValid()) {
            clear(playerId);
            return;
        }

        if (indicator.stopWhenMoved
                && (!player.getWorld().equals(indicator.startLocation.getWorld())
                || player.getLocation().distanceSquared(indicator.startLocation) > 0.01)) {
            indicator.stopWhenMoved = false;
            if (indicator.resultTicksRemaining == 0) {
                clear(playerId);
                return;
            }
        }

        if (indicator.resultTicksRemaining > 0) {
            indicator.display.text(indicator.result);
            indicator.resultTicksRemaining -= 2;
            if (indicator.resultTicksRemaining <= 0 && !indicator.stopWhenMoved) {
                clear(playerId);
                return;
            }
        } else if (indicator.stopWhenMoved) {
            int frame = (indicator.animationTicks / 5) % ANIMATION_FRAMES.length;
            indicator.display.text(Component.text(ANIMATION_FRAMES[frame], ANIMATION_COLORS[frame]));
        } else {
            clear(playerId);
            return;
        }

        Location displayLocation = displayLocation(player);
        indicator.display.teleport(displayLocation);
        if (++indicator.animationTicks % 5 == 0) {
            player.getWorld().spawnParticle(Particle.END_ROD, displayLocation, 1, 0.04, 0.04, 0.04, 0);
        }
    }

    private Location displayLocation(Player player) {
        Location eye = player.getEyeLocation();
        return eye.add(eye.getDirection().multiply(1.55));
    }

    private void clear(UUID playerId) {
        Indicator indicator = indicators.remove(playerId);
        if (indicator == null) {
            return;
        }
        if (indicator.task != null) {
            indicator.task.cancel();
        }
        if (indicator.display.isValid()) {
            indicator.display.remove();
        }
    }

    private static final class Indicator {
        private final TextDisplay display;
        private final Location startLocation;
        private boolean stopWhenMoved;
        private BukkitTask task;
        private int animationTicks;
        private Component result;
        private int resultTicksRemaining;

        private Indicator(TextDisplay display, Location startLocation, boolean stopWhenMoved) {
            this.display = display;
            this.startLocation = startLocation;
            this.stopWhenMoved = stopWhenMoved;
        }
    }
}
