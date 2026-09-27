package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static me.cobbleBet.visuals.GamblingAnimation.*;

public final class GamblingIndicatorManager {
    private static final long IDLE_DELAY_MILLIS = 1800;
    private static final long PAGE_LEASE_MILLIS = 45000;
    private static final int PREVIEW_TICKS = 300;
    private static final int GOLD = 0xFFD889;
    private static final int CREAM = 0xFFF5D6;
    private static final int LILAC = 0xBCA5F4;
    private static final int ROSE = 0xED91A4;
    private static final int MUTED = 0xB9ADCA;
    private static final String ENTITY_TAG = "cobblebet_visual";
    private static final DecimalFormat SHORT_NUMBER = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] UNITS = {"", "k", "Mil", "Bil", "Tril", "Quad"};

    private final JavaPlugin plugin;
    private final Map<UUID, Indicator> indicators = new HashMap<>();
    private final Map<UUID, Long> webPages = new HashMap<>();
    private final Map<UUID, Long> lastMovementAt = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingIdleChecks = new HashMap<>();
    private final BukkitTask animationLoop;
    private long ticks;
    private long retryAfter;
    private boolean enabled;
    private boolean sounds;
    private boolean screenTitles;
    private boolean actionBar;
    private boolean nearbyVisible;
    private boolean reducedMotion;
    private int density;
    private int maxIndicators;
    private double viewDistance;
    private float volume;

    public GamblingIndicatorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
        animationLoop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("gamblingVisuals.enabled", true);
        sounds = config.getBoolean("gamblingVisuals.sounds", true);
        screenTitles = config.getBoolean("gamblingVisuals.screenTitles", true);
        actionBar = config.getBoolean("gamblingVisuals.actionBar", true);
        nearbyVisible = config.getBoolean("gamblingVisuals.visibleToNearbyPlayers", true);
        reducedMotion = config.getBoolean("gamblingVisuals.reducedMotion", false);
        density = config.getString("gamblingVisuals.quality", "balanced").equalsIgnoreCase("high") ? 12 : 8;
        if (config.getString("gamblingVisuals.quality", "balanced").equalsIgnoreCase("low")) density = 4;
        maxIndicators = Math.max(1, Math.min(64, config.getInt("gamblingVisuals.maxActive", 32)));
        viewDistance = Math.max(4, Math.min(32, config.getDouble("gamblingVisuals.viewDistance", 20)));
        double requestedVolume = config.getDouble("gamblingVisuals.volume", 0.35);
        volume = (float) (Double.isFinite(requestedVolume) ? Math.max(0, Math.min(1, requestedVolume)) : 0.35);
        if (!Double.isFinite(viewDistance)) viewDistance = 20;
        for (UUID id : indicators.keySet().toArray(UUID[]::new)) clear(id);
    }

    public void startGambling(Player player) {
        if (player == null || !enabled) return;
        clear(player.getUniqueId());
        Indicator indicator = create(player, true);
        if (indicator != null) indicators.put(player.getUniqueId(), indicator);
    }

    public void stopGambling(Player player) {
        if (player != null) clear(player.getUniqueId());
    }

    public void setWebGamePage(UUID playerId, boolean active) {
        if (playerId == null) return;
        if (active) {
            long now = System.currentTimeMillis();
            boolean resumed = webPages.getOrDefault(playerId, 0L) <= now;
            webPages.put(playerId, now + PAGE_LEASE_MILLIS);
            if (resumed) lastMovementAt.put(playerId, now);
            else lastMovementAt.putIfAbsent(playerId, now);
            scheduleIdleIndicator(playerId);
        } else {
            webPages.remove(playerId);
            lastMovementAt.remove(playerId);
            cancelIdleCheck(playerId);
            Indicator indicator = indicators.get(playerId);
            // Let an already received result finish its exit animation.
            if (indicator != null && indicator.result == null) clear(playerId);
        }
    }

    public void onPlayerMove(Player player) {
        UUID id = player.getUniqueId();
        if (webPages.containsKey(id)) {
            lastMovementAt.put(id, System.currentTimeMillis());
            scheduleIdleIndicator(id);
        }
        Indicator indicator = indicators.get(id);
        if (indicator != null && indicator.result == null) clear(id);
        else if (indicator != null) indicator.preview = false;
    }

    public void onPlayerJoin(Player player) {
        if (webPages.containsKey(player.getUniqueId())) lastMovementAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void onPlayerQuit(Player player) {
        UUID id = player.getUniqueId();
        clear(id);
        webPages.remove(id);
        lastMovementAt.remove(id);
        cancelIdleCheck(id);
    }

    public void onPlayerTeleport(Player player) {
        clear(player.getUniqueId());
        if (webPages.containsKey(player.getUniqueId())) {
            lastMovementAt.put(player.getUniqueId(), System.currentTimeMillis());
            scheduleIdleIndicator(player.getUniqueId());
        }
    }

    public void showResult(UUID playerId, boolean won, double amount, String currency) {
        showResult(playerId, won, amount, currency, 0);
    }

    public void showResult(UUID playerId, boolean won, double amount, String currency, double multiplier) {
        if (!enabled || !Double.isFinite(amount) || amount < 0) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) return;
        String currencyName = currency == null || currency.isBlank() ? Main.vaultCurrencyName : currency;
        if (currencyName == null || currencyName.isBlank()) currencyName = "Coins";
        // Plain text only. Keep holograms readable with custom currency names.
        currencyName = currencyName.replaceAll("[\\p{Cntrl}\\r\\n]", "").trim();
        if (currencyName.length() > 24) currencyName = currencyName.substring(0, 23) + "…";
        Result result = new Result(won, amount, currencyName, multiplier);
        Indicator indicator = indicators.get(playerId);
        if (indicator != null && (!indicator.header.isValid() || !indicator.world.equals(player.getWorld().getUID()))) {
            clear(playerId);
            indicator = null;
        }
        if (indicator == null) {
            // Results take priority over an idle ornament when the display budget is full.
            if (indicators.size() >= maxIndicators) {
                UUID idle = indicators.entrySet().stream().filter(entry -> entry.getValue().result == null)
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (idle != null) clear(idle);
            }
            indicator = create(player, false);
            if (indicator != null) indicators.put(playerId, indicator);
        }
        if (indicator != null) {
            indicator.result = result;
            indicator.preview = false;
        }
        showScreenResult(player, result);
        play(player, won ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_HARP, won ? 1.0f : 0.65f, 1.0f);
    }

    public void clearAll() {
        animationLoop.cancel();
        for (UUID playerId : indicators.keySet().toArray(UUID[]::new)) clear(playerId);
        webPages.clear();
        lastMovementAt.clear();
        for (BukkitTask task : pendingIdleChecks.values()) task.cancel();
        pendingIdleChecks.clear();
    }

    private void scheduleIdleIndicator(UUID playerId) {
        cancelIdleCheck(playerId);
        long delayTicks = Math.max(1L, (IDLE_DELAY_MILLIS + 49L) / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingIdleChecks.remove(playerId);
            long now = System.currentTimeMillis();
            if (!enabled || indicators.containsKey(playerId) || !idle(playerId, now)) return;
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) return;
            Indicator indicator = create(player, false);
            if (indicator != null) indicators.put(playerId, indicator);
        }, delayTicks);
        pendingIdleChecks.put(playerId, task);
    }

    private void cancelIdleCheck(UUID playerId) {
        BukkitTask pending = pendingIdleChecks.remove(playerId);
        if (pending != null) pending.cancel();
    }

    private void tickAll() {
        ticks++;
        long now = System.currentTimeMillis();
        if (ticks % 10 == 0) {
            webPages.entrySet().removeIf(entry -> {
                if (entry.getValue() > now) return false;
                lastMovementAt.remove(entry.getKey());
                return true;
            });
            if (enabled) {
                for (UUID playerId : webPages.keySet()) {
                    if (indicators.containsKey(playerId) || !idle(playerId, now)) continue;
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline() || player.isDead()) continue;
                    Indicator indicator = create(player, false);
                    if (indicator != null) indicators.put(playerId, indicator);
                }
            }
        }
        for (UUID id : indicators.keySet().toArray(UUID[]::new)) {
            Indicator indicator = indicators.get(id);
            if (indicator == null) continue;
            try {
                tick(id, indicator, now);
            } catch (RuntimeException error) {
                clear(id);
                if (ticks >= retryAfter) plugin.getLogger().warning("Could not render a CobbleBet effect: " + error.getMessage());
                retryAfter = ticks + 600;
            }
        }
    }

    private boolean idle(UUID id, long now) {
        return webPages.getOrDefault(id, 0L) > now && now - lastMovementAt.getOrDefault(id, now) >= IDLE_DELAY_MILLIS;
    }

    private Indicator create(Player player, boolean preview) {
        if (!enabled || ticks < retryAfter || indicators.size() >= maxIndicators || player.isDead()) return null;
        List<Display> entities = new ArrayList<>();
        try {
            Location anchor = anchor(player);
            TextDisplay header = text(player, anchor, entities);
            TextDisplay amount = text(player, anchor, entities);
            TextDisplay footer = text(player, anchor, entities);
            ItemDisplay[] coins = new ItemDisplay[2];
            for (int i = 0; i < coins.length; i++) {
                coins[i] = player.getWorld().spawn(anchor, ItemDisplay.class, entity -> {
                    configure(entity);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setItemStack(new ItemStack(Material.GOLD_NUGGET));
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                });
                entities.add(coins[i]);
            }
            Indicator indicator = new Indicator(header, amount, footer, coins, entities, player, preview);
            updateViewers(player, indicator);
            return indicator;
        } catch (RuntimeException error) {
            for (Display entity : entities) entity.remove();
            plugin.getLogger().warning("Could not create a CobbleBet hologram: " + error.getMessage());
            retryAfter = ticks + 600;
            return null;
        }
    }

    private void configure(Display display) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setSilent(true);
        display.setVisibleByDefault(false);
        display.addScoreboardTag(ENTITY_TAG);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange((float) (viewDistance / 64.0));
        display.setDisplayWidth(4);
        display.setDisplayHeight(4);
        display.setTeleportDuration(reducedMotion ? 0 : 2);
        display.setInterpolationDuration(reducedMotion ? 0 : 3);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.06f), new Quaternionf()));
    }

    private TextDisplay text(Player player, Location location, List<Display> entities) {
        TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
            configure(entity);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(360);
            entity.text(Component.empty());
        });
        entities.add(display);
        return display;
    }

    private void tick(UUID id, Indicator indicator, long now) {
        Player player = Bukkit.getPlayer(id);
        if (!enabled || player == null || !player.isOnline() || player.isDead()
                || !player.getWorld().getUID().equals(indicator.world) || indicator.entities.stream().anyMatch(entity -> !entity.isValid())) {
            clear(id);
            return;
        }
        if (indicator.result == null) {
            if (indicator.preview && (indicator.age >= PREVIEW_TICKS || player.getLocation().distanceSquared(indicator.start) > 0.01)) indicator.preview = false;
            if (!indicator.preview && !idle(id, now)) { clear(id); return; }
        } else if (indicator.result.age >= indicator.result.duration) {
            indicator.result = null;
            indicator.age = 0;
            player.clearTitle();
            if (!idle(id, now)) { clear(id); return; }
        }
        if (indicator.age % 10 == 0) updateViewers(player, indicator);
        if (ticks % 2 == 0) {
            if (indicator.result == null) renderIdle(player, indicator);
            else renderResult(player, indicator);
        }
        indicator.age++;
        if (indicator.result != null) indicator.result.age++;
    }

    private void renderIdle(Player player, Indicator indicator) {
        double phase = indicator.age * 0.045;
        double intro = reducedMotion ? 1 : easeOut(indicator.age / 14.0);
        double bob = reducedMotion ? 0 : Math.sin(phase) * 0.035;
        Location anchor = anchor(player).add(0, bob, 0);
        indicator.header.text(gradient("G A M B L I N G", LILAC, GOLD, phase, true));
        indicator.amount.text(Component.text("◆   ◆   ◆", TextColor.color(mix(LILAC, CREAM, (Math.sin(phase) + 1) / 2))));
        indicator.footer.text(Component.text("COBBLEBET", TextColor.color(MUTED)));
        positionText(indicator.header, anchor.clone().add(0, 0.27, 0), 0.52 * intro, 1);
        positionText(indicator.amount, anchor, 0.31 * intro, 0.85);
        positionText(indicator.footer, anchor.clone().add(0, -0.22, 0), 0.27 * intro, 0.75);
        orbitCoins(indicator, anchor, phase, 0.86, 0.28 * intro);
        if (!reducedMotion && indicator.age % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = phase + Math.PI * i;
                dust(indicator, player.getLocation().add(Math.cos(angle) * 0.7, 0.12, Math.sin(angle) * 0.7), i == 0 ? GOLD : LILAC, 0.65f);
            }
        }
        if (actionBar && indicator.age % 20 == 0) {
            player.sendActionBar(gradient("◆  Gambling  ◆", LILAC, GOLD, phase, false));
            indicator.ownsActionBar = true;
        }
    }

    private void renderResult(Player player, Indicator indicator) {
        Result result = indicator.result;
        int age = result.age;
        double alpha = opacity(age, result.duration);
        double reveal = reducedMotion ? 1 : revealScale(age);
        double rise = reducedMotion ? 0 : result.won ? easeOut(age / 40.0) * 0.23 : -smooth(age / (double) result.duration) * 0.16;
        Location anchor = anchor(player).add(0, rise, 0);
        int accent = result.won ? GOLD : ROSE;
        String label = result.won ? result.tier == 2 ? "SPECTACULAR WIN" : result.tier == 1 ? "BIG WIN" : "WIN" : "ROUND LOST";
        double amount = reducedMotion ? result.amount : countedAmount(result.amount, age);
        String value = (result.won ? "" : "−") + compact(amount);
        indicator.header.text(gradient(label, accent, result.won ? CREAM : 0xC6A4C7, age * 0.04, true));
        indicator.amount.text(Component.text(value, TextColor.color(result.won ? CREAM : ROSE)).decorate(TextDecoration.BOLD));
        String detail = result.won ? result.currency + " · PAYOUT" : result.currency + " · STAKE LOST";
        if (result.won && result.multiplier > 0) detail = compact(result.multiplier) + "× · " + detail;
        indicator.footer.text(Component.text(detail, TextColor.color(MUTED)));
        positionText(indicator.header, anchor.clone().add(0, 0.37, 0), 0.55 * reveal, alpha);
        positionText(indicator.amount, anchor, (result.tier > 0 ? 0.97 : 0.82) * reveal, alpha);
        positionText(indicator.footer, anchor.clone().add(0, -0.27, 0), 0.31 * reveal, alpha);
        double radius = reducedMotion ? 0.9 : result.won ? 0.82 + Math.sin(clamp(age / 36.0) * Math.PI) * 0.38 : 0.9 * (1 - smooth(age / 34.0));
        orbitCoins(indicator, anchor, age * (result.won ? 0.09 : -0.04), radius, 0.32 * reveal * alpha);
        if (!reducedMotion && age % 4 == 0) {
            if (result.won) winParticles(player, indicator, result);
            else lossParticles(player, indicator, age);
        }
        if (age == 8) play(player, result.won ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_HARP, result.won ? 1.25f : 0.5f, 0.7f);
        if (result.won && age == 16) play(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 0.6f);
        if (result.won && result.tier > 0 && age == 24) play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.25f, 0.65f);
        if (actionBar && age % 4 == 0) {
            player.sendActionBar(Component.text(result.won ? "Payout  " : "Stake lost  ", TextColor.color(MUTED))
                    .append(Component.text(value + " " + result.currency, TextColor.color(accent))));
            indicator.ownsActionBar = true;
        }
    }

    private void winParticles(Player player, Indicator indicator, Result result) {
        int age = result.age;
        int points = density + result.tier * 4;
        Location feet = player.getLocation();
        if (age <= 28) {
            double progress = easeOut(age / 28.0);
            double radius = 0.3 + progress * (1.0 + 0.25 * result.tier);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points + age * 0.035;
                dust(indicator, feet.clone().add(Math.cos(angle) * radius, 0.1 + 0.18 * Math.sin(progress * Math.PI), Math.sin(angle) * radius), i % 3 == 0 ? CREAM : GOLD, 0.95f);
            }
            // Two ascending trails join the payout hologram.
            double height = 0.25 + age / 28.0 * 2.4;
            for (int i = 0; i < 2; i++) {
                double angle = age * 0.22 + i * Math.PI;
                Location point = feet.clone().add(Math.cos(angle) * 0.65, height, Math.sin(angle) * 0.65);
                dust(indicator, point, i == 0 ? GOLD : LILAC, 1.05f);
            }
        }
        if (age == 12 || (result.tier > 0 && age == 28)) {
            particle(indicator, Particle.END_ROD, feet.clone().add(0, 1.6, 0), points, 0.45, 0.45, 0.45, 0.025);
        }
        if (age > 28 && age < 60 && age % 8 == 0) {
            double angle = age * 0.3;
            particle(indicator, Particle.END_ROD, feet.clone().add(Math.cos(angle) * 0.75, 2.3 + Math.sin(angle) * 0.3, Math.sin(angle) * 0.75), 1, 0, 0, 0, 0.01);
        }
    }

    private void lossParticles(Player player, Indicator indicator, int age) {
        if (age > 24) return;
        double radius = 0.95 * (1 - smooth(age / 28.0));
        for (int i = 0; i < density; i++) {
            double angle = 2 * Math.PI * i / density - age * 0.025;
            dust(indicator, player.getLocation().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius), i % 2 == 0 ? ROSE : 0x9A789E, 0.7f);
        }
    }

    private void orbitCoins(Indicator indicator, Location anchor, double phase, double radius, double scale) {
        for (int i = 0; i < indicator.coins.length; i++) {
            double angle = reducedMotion ? i * Math.PI : phase + i * Math.PI;
            Location point = anchor.clone().add(Math.cos(angle) * radius, reducedMotion ? 0 : Math.sin(angle * 2) * 0.09, Math.sin(angle) * radius * 0.45);
            point.setYaw(0);
            point.setPitch(0);
            ItemDisplay coin = indicator.coins[i];
            coin.teleport(point);
            coin.setInterpolationDelay(0);
            coin.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) angle).rotateZ(0.22f), new Vector3f((float) Math.max(0.001, scale)), new Quaternionf()));
        }
    }

    private void positionText(TextDisplay display, Location point, double scale, double alpha) {
        point.setYaw(0);
        point.setPitch(0);
        display.teleport(point);
        display.setTextOpacity((byte) Math.max(0, Math.min(255, Math.round(alpha * 255))));
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f((float) Math.max(0.001, scale)), new Quaternionf()));
    }

    private Location anchor(Player player) {
        return player.getLocation().add(0, player.isSneaking() ? 2.1 : 2.4, 0);
    }

    private void updateViewers(Player owner, Indicator indicator) {
        Set<UUID> visible = new HashSet<>();
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (canView(viewer, owner)) visible.add(viewer.getUniqueId());
        }
        for (UUID previous : indicator.viewers) {
            if (visible.contains(previous)) continue;
            Player viewer = Bukkit.getPlayer(previous);
            if (viewer != null) for (Display entity : indicator.entities) viewer.hideEntity(plugin, entity);
        }
        for (UUID id : visible) {
            if (indicator.viewers.contains(id)) continue;
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) for (Display entity : indicator.entities) viewer.showEntity(plugin, entity);
        }
        indicator.viewers = visible;
    }

    private boolean canView(Player viewer, Player owner) {
        return viewer.isOnline() && viewer.getWorld().equals(owner.getWorld())
                && (viewer.equals(owner) || nearbyVisible && !owner.isInvisible() && viewer.canSee(owner))
                && viewer.getLocation().distanceSquared(owner.getLocation()) <= viewDistance * viewDistance;
    }

    private void dust(Indicator indicator, Location location, int rgb, float size) {
        Player owner = Bukkit.getPlayer(indicator.owner);
        if (owner == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), size);
        for (UUID id : indicator.viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && canView(viewer, owner)) viewer.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, dust);
        }
    }

    private void particle(Indicator indicator, Particle particle, Location location, int count, double x, double y, double z, double speed) {
        Player owner = Bukkit.getPlayer(indicator.owner);
        if (owner == null) return;
        for (UUID id : indicator.viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && canView(viewer, owner)) viewer.spawnParticle(particle, location, count, x, y, z, speed);
        }
    }

    private void showScreenResult(Player player, Result result) {
        if (!screenTitles) return;
        String label = result.won ? result.tier == 2 ? "SPECTACULAR WIN" : result.tier == 1 ? "BIG WIN" : "WIN" : "ROUND LOST";
        Component title = gradient(label, result.won ? GOLD : ROSE, result.won ? CREAM : 0xC6A4C7, 0, true);
        Component subtitle = Component.text((result.won ? "Payout  " : "Stake lost  ") + compact(result.amount) + " " + result.currency, TextColor.color(result.won ? CREAM : MUTED));
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(180), Duration.ofMillis(result.duration * 50L - 650), Duration.ofMillis(470))));
    }

    private void play(Player player, Sound sound, float pitch, float factor) {
        if (sounds && volume > 0) player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume * factor, pitch);
    }

    private void clear(UUID id) {
        Indicator indicator = indicators.remove(id);
        if (indicator == null) return;
        for (Display entity : indicator.entities) if (entity.isValid()) entity.remove();
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            if (indicator.ownsActionBar) player.sendActionBar(Component.empty());
            if (indicator.result != null && screenTitles) player.clearTitle();
        }
    }

    private static Component gradient(String value, int start, int end, double phase, boolean bold) {
        Component text = Component.empty();
        for (int i = 0; i < value.length(); i++) {
            double blend = (Math.sin(i * 0.35 - phase) + 1) / 2;
            text = text.append(Component.text(String.valueOf(value.charAt(i)), TextColor.color(mix(start, end, blend))));
        }
        return bold ? text.decorate(TextDecoration.BOLD) : text;
    }

    private static int mix(int from, int to, double amount) {
        double t = clamp(amount);
        int r = (int) (((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int g = (int) (((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static String compact(double number) {
        double value = Math.max(0, number);
        int unit = 0;
        while (value >= 1000 && unit < UNITS.length - 1) { value /= 1000; unit++; }
        return SHORT_NUMBER.format(value) + UNITS[unit];
    }

    private static final class Result {
        final boolean won;
        final double amount;
        final String currency;
        final double multiplier;
        final int tier;
        final int duration;
        int age;
        Result(boolean won, double amount, String currency, double multiplier) {
            this.won = won;
            this.amount = amount;
            this.currency = currency;
            this.multiplier = Double.isFinite(multiplier) && multiplier > 0 ? multiplier : 0;
            this.tier = tier(won, this.multiplier);
            this.duration = duration(won, tier);
        }
    }

    private static final class Indicator {
        final TextDisplay header;
        final TextDisplay amount;
        final TextDisplay footer;
        final ItemDisplay[] coins;
        final List<Display> entities;
        final UUID owner;
        final UUID world;
        final Location start;
        Set<UUID> viewers = new HashSet<>();
        boolean preview;
        boolean ownsActionBar;
        int age;
        Result result;

        Indicator(TextDisplay header, TextDisplay amount, TextDisplay footer, ItemDisplay[] coins, List<Display> entities, Player player, boolean preview) {
            this.header = header;
            this.amount = amount;
            this.footer = footer;
            this.coins = coins;
            this.entities = entities;
            this.owner = player.getUniqueId();
            this.world = player.getWorld().getUID();
            this.start = player.getLocation();
            this.preview = preview;
        }
    }
}
