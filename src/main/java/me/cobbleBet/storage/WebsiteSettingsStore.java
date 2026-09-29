package me.cobbleBet.storage;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WebsiteSettingsStore {
    private static final String ROOT = "websiteCustomization";
    private static volatile JsonObject snapshot = new JsonObject();
    private static volatile boolean initialized;
    private static final String[] GAMES = {"mines", "blackjack", "roulette", "coinflip", "plinko"};

    private WebsiteSettingsStore() {}

    public static synchronized void load(Main plugin) {
        FileConfiguration config = plugin.getConfig();
        initialized = config.getBoolean(ROOT + ".initialized", false);
        JsonObject settings = new JsonObject();
        String theme = config.getString(ROOT + ".theme", "classic");
        settings.addProperty("theme", Set.of("classic", "pink", "cat", "viking", "ember").contains(theme) ? theme : "classic");
        String message = config.getString(ROOT + ".playerMessage", "");
        settings.addProperty("playerMessage", message.substring(0, Math.min(160, message.length())));
        JsonObject enabled = new JsonObject();
        JsonObject rules = new JsonObject();
        for (String game : GAMES) {
            enabled.addProperty(game, config.getBoolean(ROOT + ".enabledGames." + game, true));
            JsonObject rule = new JsonObject();
            double defaultPercent = 0;
            double percent = config.getDouble(ROOT + ".gameRules." + game + ".percent", defaultPercent);
            rule.addProperty("percent", Double.isFinite(percent) ? Math.round(Math.max(0, Math.min(50, percent)) * 10) / 10.0 : defaultPercent);
            rules.add(game, rule);
        }
        settings.add("enabledGames", enabled);
        settings.add("gameRules", rules);
        JsonObject background = new JsonObject();
        String image = config.getString(ROOT + ".background.image", "");
        background.addProperty("image", validImage(image) ? image : "");
        background.addProperty("blur", Math.max(0, Math.min(30, config.getInt(ROOT + ".background.blur", 8))));
        settings.add("background", background);
        JsonObject maintenance = new JsonObject();
        maintenance.addProperty("enabled", config.getBoolean(ROOT + ".maintenance.enabled", false));
        String maintenanceMessage = config.getString(ROOT + ".maintenance.message", "Games are temporarily unavailable while this server completes maintenance.");
        maintenance.addProperty("message", maintenanceMessage.substring(0, Math.min(180, maintenanceMessage.length())));
        settings.add("maintenance", maintenance);
        snapshot = settings;
    }

    public static JsonObject getSettings() { return snapshot.deepCopy(); }
    public static boolean isInitialized() { return initialized; }

    public static synchronized void save(Main plugin, JsonObject settings) throws IOException {
        validate(settings);
        FileConfiguration config = plugin.getConfig();
        config.set(ROOT, null);
        config.set(ROOT + ".initialized", true);
        config.set(ROOT + ".theme", settings.get("theme").getAsString());
        config.set(ROOT + ".playerMessage", settings.get("playerMessage").getAsString());
        for (String game : GAMES) {
            config.set(ROOT + ".enabledGames." + game, settings.getAsJsonObject("enabledGames").get(game).getAsBoolean());
            config.set(ROOT + ".gameRules." + game + ".percent", settings.getAsJsonObject("gameRules").getAsJsonObject(game).get("percent").getAsDouble());
        }
        config.set(ROOT + ".background.image", settings.getAsJsonObject("background").get("image").getAsString());
        config.set(ROOT + ".background.blur", settings.getAsJsonObject("background").get("blur").getAsInt());
        config.set(ROOT + ".maintenance.enabled", settings.getAsJsonObject("maintenance").get("enabled").getAsBoolean());
        config.set(ROOT + ".maintenance.message", settings.getAsJsonObject("maintenance").get("message").getAsString());
        config.setComments(ROOT, java.util.List.of("", "WEBSITE CUSTOMIZATION — managed in the CobbleBet owner panel", "Keep this section at the bottom. New game settings apply to new bets.", "Background images are resized data URLs, stored here to survive restarts."));
        try {
            saveConfirmed(plugin);
            load(plugin);
        } catch (IOException error) {
            plugin.reloadConfig();
            load(plugin);
            throw error;
        }
    }

    public static void saveConfirmed(Main plugin) throws IOException {
        moveToBottom(plugin.getConfig());
        java.nio.file.Path destination = new File(plugin.getDataFolder(), "config.yml").toPath();
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile(plugin.getDataFolder().toPath(), "cobblebet-config-", ".tmp");
        try {
            java.nio.file.Files.writeString(temporary, plugin.getConfig().saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { java.nio.file.Files.deleteIfExists(temporary); }
    }

    public static void moveToBottom(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection(ROOT);
        if (section == null) return;
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) values.put(key, section.get(key));
        }
        java.util.List<String> comments = config.getComments(ROOT);
        config.set(ROOT, null);
        ConfigurationSection newSection = config.createSection(ROOT);
        values.forEach(newSection::set);
        config.setComments(ROOT, comments);
    }

    private static void validate(JsonObject settings) {
        if (!Set.of("classic", "pink", "cat", "viking", "ember").contains(settings.get("theme").getAsString())
                || settings.get("playerMessage").getAsString().length() > 160) throw new IllegalArgumentException("Invalid theme or message.");
        for (String game : GAMES) {
            double percent = settings.getAsJsonObject("gameRules").getAsJsonObject(game).get("percent").getAsDouble();
            if (!Double.isFinite(percent) || percent < 0 || percent > 50) throw new IllegalArgumentException("Invalid game percentage.");
            if (!settings.getAsJsonObject("enabledGames").get(game).getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid game availability.");
        }
        JsonObject background = settings.getAsJsonObject("background");
        double blur = background.get("blur").getAsDouble();
        if (!validImage(background.get("image").getAsString()) || !Double.isFinite(blur) || blur < 0 || blur > 30) throw new IllegalArgumentException("Invalid background.");
        JsonObject maintenance = settings.getAsJsonObject("maintenance");
        if (maintenance == null || !maintenance.get("enabled").getAsJsonPrimitive().isBoolean()
                || maintenance.get("message").getAsString().length() > 180) throw new IllegalArgumentException("Invalid maintenance settings.");
    }

    private static boolean validImage(String value) {
        if (value.isEmpty()) return true;
        if (value.length() > 700000 || !value.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}")) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1));
            if (bytes.length < 12 || bytes.length > 512000) return false;
            if (value.startsWith("data:image/png")) return bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
            if (value.startsWith("data:image/jpeg")) return bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255;
            return new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
