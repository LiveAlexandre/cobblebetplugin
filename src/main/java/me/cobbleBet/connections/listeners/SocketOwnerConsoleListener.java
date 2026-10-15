package me.cobbleBet.connections.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SocketOwnerConsoleListener extends SocketMessageListener {
    public SocketOwnerConsoleListener(String type, CobbleSocketClient client) { super(type, client); }

    @Override public void trigger(JsonObject json) {
        if (!json.has("requestId")) return;
        String requestId = json.get("requestId").getAsString();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> respond(requestId, json));
    }

    private void respond(String requestId, JsonObject json) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "ownerConsoleResult");
        result.addProperty("requestId", requestId);
        try {
            switch (type) {
                case "ownerConsoleCommand" -> {
                    String command = text(json, "command", 256).replaceFirst("^/+", "");
                    if (command.isBlank()) throw new IllegalArgumentException("Enter a command first.");
                    boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    result.addProperty("success", accepted);
                    result.addProperty("message", accepted ? "Command sent to the server console." : "The server did not recognize that command.");
                }
                case "ownerConsoleChat" -> {
                    String message = text(json, "message", 256);
                    if (message.isBlank()) throw new IllegalArgumentException("Enter a chat message first.");
                    Bukkit.broadcast(Component.text("[Broadcast] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
                    result.addProperty("success", true);
                    result.addProperty("message", "Message broadcast to online players.");
                }
                case "ownerConsoleComplete" -> {
                    String command = text(json, "command", 256).replaceFirst("^/+", "");
                    Set<String> suggestions = new LinkedHashSet<>();
                    List<String> direct = Bukkit.getCommandMap().tabComplete(Bukkit.getConsoleSender(), command);
                    if (direct != null) suggestions.addAll(direct);
                    List<String> slashed = Bukkit.getCommandMap().tabComplete(Bukkit.getConsoleSender(), "/" + command);
                    if (slashed != null) suggestions.addAll(slashed);
                    String first = command.toLowerCase().split("\\s+", 2)[0];
                    for (String known : List.of("announce", "ban", "ban-ip", "cobble", "cobblebet", "gamemode", "gamerule", "give", "kick", "killall", "list", "pardon", "reload", "say", "stop", "teleport", "time", "weather", "whitelist")) {
                        if (known.startsWith(first)) suggestions.add(known);
                    }
                    JsonArray values = new JsonArray();
                    suggestions.stream().map(value -> value.replaceFirst("^/+", "")).limit(20).forEach(values::add);
                    result.add("suggestions", values);
                    result.addProperty("success", true);
                }
                case "ownerConsoleSnapshot" -> {
                    JsonArray players = new JsonArray();
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("uuid", player.getUniqueId().toString());
                        entry.addProperty("name", player.getName());
                        entry.addProperty("balance", Main.getInstance().getEconomyManager().getBalance(player));
                        players.add(entry);
                    });
                    result.add("players", players);
                    result.addProperty("success", true);
                }
                case "ownerWalletSet" -> {
                    if (!json.has("playerUUID") || !json.has("amount")) throw new IllegalArgumentException("Choose a player and amount.");
                    UUID uuid = UUID.fromString(json.get("playerUUID").getAsString());
                    double amount = json.get("amount").getAsDouble();
                    if (!Double.isFinite(amount) || amount < 0 || amount > Main.maximumBalance) throw new IllegalArgumentException("That wallet amount is outside the allowed range.");
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (!Main.getInstance().getEconomyManager().setBalance(player, amount)) throw new IllegalStateException("The economy rejected that wallet amount.");
                    result.addProperty("success", true);
                    result.addProperty("balance", amount);
                    result.addProperty("message", "Wallet updated for " + (player.getName() == null ? uuid : player.getName()) + ".");
                    Main.getInstance().sendServerStatus();
                }
                default -> throw new IllegalArgumentException("Unsupported owner console action.");
            }
        } catch (Exception error) {
            result.addProperty("success", false);
            result.addProperty("message", error.getMessage() == null ? "The server could not complete that action." : error.getMessage());
        }
        if (client.isOpen()) client.send(result.toString());
    }

    private static String text(JsonObject json, String key, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return "";
        String value = json.get(key).getAsString().trim();
        return value.substring(0, Math.min(max, value.length()));
    }
}
