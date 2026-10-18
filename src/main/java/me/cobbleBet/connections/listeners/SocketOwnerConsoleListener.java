package me.cobbleBet.connections.listeners;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import me.cobbleBet.connections.CobbleSocketClient;
import me.cobbleBet.connections.SocketMessageListener;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

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
                    String dispatchedCommand = command.replaceFirst("(?i)^playerlist(?=\\s|$)", "list");
                    CapturingConsoleSender sender = new CapturingConsoleSender(Bukkit.getConsoleSender());
                    boolean accepted = Bukkit.dispatchCommand(sender, dispatchedCommand);
                    String output = sender.output();
                    result.addProperty("success", accepted);
                    if (!output.isBlank()) {
                        result.addProperty("output", output);
                        result.addProperty("message", output);
                        if (Main.getInstance().getConsoleLogService() != null) {
                            Main.getInstance().getConsoleLogService().record("info", "Command /" + command.split("\\s+", 2)[0], output);
                        }
                    } else {
                        result.addProperty("message", accepted ? "Command completed without output." : "The server did not recognize that command.");
                    }
                }
                case "ownerConsoleChat" -> {
                    String message = text(json, "message", 256);
                    if (message.isBlank()) throw new IllegalArgumentException("Enter a chat message first.");
                    Bukkit.broadcast(Component.text("[Broadcast] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.WHITE)));
                    result.addProperty("success", true);
                    result.addProperty("message", "Message broadcast to online players.");
                }
                case "ownerConsoleComplete" -> {
                    String command = rawText(json, "command", 256).replaceFirst("^\\s*/+", "").replaceFirst("^\\s+", "");
                    Set<String> suggestions = new LinkedHashSet<>();
                    try { addNativeSuggestions(command, suggestions, Bukkit.getCommandMap().tabComplete(Bukkit.getConsoleSender(), command)); }
                    catch (RuntimeException ignored) {}
                    try { addNativeSuggestions(command, suggestions, Bukkit.getCommandMap().tabComplete(Bukkit.getConsoleSender(), "/" + command)); }
                    catch (RuntimeException ignored) {}
                    CompletionPoint point = completionPoint(command);
                    if (point.argument() == 0) {
                        for (String known : List.of("announce", "ban", "ban-ip", "clear", "cobble", "cobblebet", "deop", "effect", "enchant", "gamemode", "gamerule", "give", "help", "kick", "kill", "killall", "list", "op", "pardon", "playerlist", "reload", "say", "stop", "teleport", "time", "weather", "whitelist")) {
                            if (known.startsWith(point.partial().toLowerCase())) suggestions.add(known);
                        }
                    }
                    if (isPlayerArgument(point.command(), point.argument())) {
                        for (String selector : List.of("@a", "@e", "@p", "@r", "@s")) if (selector.startsWith(point.partial().toLowerCase())) suggestions.add(point.prefix() + selector);
                        Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).filter(name -> name.toLowerCase().startsWith(point.partial().toLowerCase())).forEach(name -> suggestions.add(point.prefix() + name));
                    }
                    if ((point.command().equals("give") && point.argument() == 2) || (point.command().equals("clear") && point.argument() == 2)) {
                        for (Material material : Material.values()) {
                            if (!material.isItem() || material.isLegacy()) continue;
                            String name = material.name().toLowerCase();
                            if (name.startsWith(point.partial().toLowerCase())) suggestions.add(point.prefix() + name);
                        }
                    }
                    JsonArray values = new JsonArray();
                    suggestions.stream().map(value -> value.replaceFirst("^/+", "")).limit(40).forEach(values::add);
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
                    JsonArray logs = new JsonArray();
                    if (Main.getInstance().getConsoleLogService() != null) Main.getInstance().getConsoleLogService().snapshot().forEach(log -> {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("timestamp", log.timestamp());
                        entry.addProperty("level", log.level());
                        entry.addProperty("logger", log.logger());
                        entry.addProperty("message", log.message());
                        logs.add(entry);
                    });
                    result.add("logs", logs);
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

    private static String rawText(JsonObject json, String key, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return "";
        String value = json.get(key).getAsString();
        return value.substring(0, Math.min(max, value.length()));
    }

    private static void addNativeSuggestions(String command, Set<String> suggestions, List<String> nativeValues) {
        if (nativeValues == null) return;
        CompletionPoint point = completionPoint(command);
        for (String nativeValue : nativeValues) {
            if (nativeValue == null || nativeValue.isBlank()) continue;
            String value = nativeValue.replaceFirst("^/+", "");
            if (value.startsWith("minecraft:") && !point.partial().contains(":")) value = value.substring("minecraft:".length());
            suggestions.add(point.prefix().isEmpty() || value.contains(" ") ? value : point.prefix() + value);
        }
    }

    private static CompletionPoint completionPoint(String command) {
        int start = command.length();
        while (start > 0 && !Character.isWhitespace(command.charAt(start - 1))) start--;
        String prefix = command.substring(0, start);
        String partial = command.substring(start);
        String before = prefix.trim();
        String[] tokens = before.isEmpty() ? new String[0] : before.split("\\s+");
        String root = tokens.length == 0 ? partial.toLowerCase() : tokens[0].toLowerCase();
        return new CompletionPoint(prefix, partial, root, tokens.length);
    }

    private static boolean isPlayerArgument(String command, int argument) {
        return switch (command) {
            case "give", "clear", "kick", "ban", "pardon", "op", "deop", "kill", "enchant" -> argument == 1;
            case "gamemode", "effect" -> argument == 2;
            case "teleport", "tp" -> argument == 1 || argument == 2;
            default -> false;
        };
    }

    private static final class CapturingConsoleSender implements ConsoleCommandSender {
        private static final int MAX_OUTPUT_LENGTH = 12_000;
        private final ConsoleCommandSender delegate;
        private final StringBuilder output = new StringBuilder();

        private CapturingConsoleSender(ConsoleCommandSender delegate) {
            this.delegate = delegate;
        }

        private void capture(String message) {
            if (message == null || output.length() >= MAX_OUTPUT_LENGTH) return;
            String clean = message.replaceAll("(?i)§[0-9A-FK-ORX]", "")
                    .replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                    .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
            if (clean.isEmpty()) return;
            if (!output.isEmpty()) output.append('\n');
            int remaining = MAX_OUTPUT_LENGTH - output.length();
            output.append(clean, 0, Math.min(remaining, clean.length()));
        }

        private String output() {
            return output.toString().trim();
        }

        @Override public void sendMessage(String message) { capture(message); delegate.sendMessage(message); }
        @Override public void sendMessage(String... messages) { for (String message : messages) sendMessage(message); }
        @Override public void sendMessage(UUID sender, String message) { capture(message); delegate.sendMessage(sender, message); }
        @Override public void sendMessage(UUID sender, String... messages) { for (String message : messages) sendMessage(sender, message); }
        @Override public void sendMessage(Component message) { capture(PlainTextComponentSerializer.plainText().serialize(message)); delegate.sendMessage(message); }
        @Override public void sendMessage(Identity source, Component message, MessageType type) { capture(PlainTextComponentSerializer.plainText().serialize(message)); delegate.sendMessage(source, message, type); }
        @Override public void sendRawMessage(String message) { capture(message); delegate.sendRawMessage(message); }
        @Override public void sendRawMessage(UUID sender, String message) { capture(message); delegate.sendRawMessage(sender, message); }
        @Override public Server getServer() { return delegate.getServer(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public Component name() { return delegate.name(); }
        @Override public CommandSender.Spigot spigot() { return delegate.spigot(); }
        @Override public boolean isOp() { return delegate.isOp(); }
        @Override public void setOp(boolean value) { delegate.setOp(value); }
        @Override public boolean isPermissionSet(String name) { return delegate.isPermissionSet(name); }
        @Override public boolean isPermissionSet(Permission permission) { return delegate.isPermissionSet(permission); }
        @Override public boolean hasPermission(String name) { return delegate.hasPermission(name); }
        @Override public boolean hasPermission(Permission permission) { return delegate.hasPermission(permission); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return delegate.addAttachment(plugin, name, value); }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return delegate.addAttachment(plugin); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return delegate.addAttachment(plugin, name, value, ticks); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return delegate.addAttachment(plugin, ticks); }
        @Override public void removeAttachment(PermissionAttachment attachment) { delegate.removeAttachment(attachment); }
        @Override public void recalculatePermissions() { delegate.recalculatePermissions(); }
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return delegate.getEffectivePermissions(); }
        @Override public boolean isConversing() { return delegate.isConversing(); }
        @Override public void acceptConversationInput(String input) { delegate.acceptConversationInput(input); }
        @Override public boolean beginConversation(Conversation conversation) { return delegate.beginConversation(conversation); }
        @Override public void abandonConversation(Conversation conversation) { delegate.abandonConversation(conversation); }
        @Override public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) { delegate.abandonConversation(conversation, details); }
    }

    private record CompletionPoint(String prefix, String partial, String command, int argument) {}
}
