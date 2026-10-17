package me.cobbleBet.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.cobbleBet.Main;
import me.cobbleBet.players.PlayerWallet;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CobbleBetPlaceholderExpansion extends PlaceholderExpansion {
    private static final Pattern TOP_PLACEHOLDER = Pattern.compile("top_(\\d{1,3})_(name|uuid|balance|balance_formatted)");
    private static final ThreadLocal<DecimalFormat> NUMBER = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> FORMATTED_NUMBER = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final long CACHE_MILLIS = 1_000L;
    private static final int MAX_RANK = 100;

    private final Main plugin;
    private volatile LeaderboardSnapshot cachedLeaderboard = new LeaderboardSnapshot(0L, List.of());

    CobbleBetPlaceholderExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cobblebet";
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "CobbleBet" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        String key = parameters.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "balance" -> player == null ? "0" : number(Main.getWallet(player.getUniqueId()).getBalance());
            case "balance_formatted" -> player == null ? "0" : formattedNumber(Main.getWallet(player.getUniqueId()).getBalance());
            case "currency" -> currencyName();
            case "economy" -> Main.economyType == null ? "unknown" : Main.economyType.toLowerCase(Locale.ROOT);
            case "rank" -> player == null ? "0" : Integer.toString(rankOf(player.getUniqueId()));
            case "total_players" -> Integer.toString(Main.playerWalletHashMap.size());
            case "total_balance" -> number(leaderboard().entries().stream().mapToDouble(RankedWallet::balance).sum());
            case "total_balance_formatted" -> formattedNumber(leaderboard().entries().stream().mapToDouble(RankedWallet::balance).sum());
            case "server_id" -> Main.serverId == null ? "" : Main.serverId;
            case "server_name" -> Main.serverDisplayName == null ? "" : Main.serverDisplayName;
            case "plus" -> Boolean.toString(Main.premiumEnabled);
            case "version" -> plugin.getDescription().getVersion();
            default -> resolveTopPlaceholder(key);
        };
    }

    private String resolveTopPlaceholder(String key) {
        Matcher matcher = TOP_PLACEHOLDER.matcher(key);
        if (!matcher.matches()) return null;

        int position = Integer.parseInt(matcher.group(1));
        if (position < 1 || position > MAX_RANK) return "";
        List<RankedWallet> entries = leaderboard().entries();
        if (position > entries.size()) return "";

        RankedWallet entry = entries.get(position - 1);
        return switch (matcher.group(2)) {
            case "name" -> playerName(entry.uuid());
            case "uuid" -> entry.uuid().toString();
            case "balance" -> number(entry.balance());
            case "balance_formatted" -> formattedNumber(entry.balance());
            default -> "";
        };
    }

    private int rankOf(UUID uuid) {
        List<RankedWallet> entries = leaderboard().entries();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).uuid().equals(uuid)) return index + 1;
        }
        return 0;
    }

    private LeaderboardSnapshot leaderboard() {
        long now = System.currentTimeMillis();
        LeaderboardSnapshot current = cachedLeaderboard;
        if (now - current.createdAt() < CACHE_MILLIS) return current;

        List<RankedWallet> entries = new ArrayList<>();
        try {
            for (Map.Entry<UUID, PlayerWallet> entry : Main.playerWalletHashMap.entrySet()) {
                double balance = entry.getValue().getBalance();
                entries.add(new RankedWallet(entry.getKey(), Double.isFinite(balance) ? Math.max(0, balance) : 0));
            }
        } catch (ConcurrentModificationException ignored) {
            // An asynchronous scoreboard refresh overlapped a wallet load/update. Use the
            // last complete snapshot and retry on the next PlaceholderAPI refresh.
            return current;
        }
        entries.sort(Comparator.comparingDouble(RankedWallet::balance).reversed().thenComparing(entry -> entry.uuid().toString()));
        LeaderboardSnapshot refreshed = new LeaderboardSnapshot(now, List.copyOf(entries));
        cachedLeaderboard = refreshed;
        return refreshed;
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name == null || name.isBlank() ? uuid.toString() : name;
    }

    private String currencyName() {
        if (Main.economyType != null && Main.economyType.equalsIgnoreCase("item")) {
            return Main.economyItem == null ? "Items" : Main.economyItem.name();
        }
        return Main.vaultCurrencyName == null || Main.vaultCurrencyName.isBlank() ? "Coins" : Main.vaultCurrencyName;
    }

    private String number(double value) {
        return NUMBER.get().format(value);
    }

    private String formattedNumber(double value) {
        return FORMATTED_NUMBER.get().format(value);
    }

    private record RankedWallet(UUID uuid, double balance) {}
    private record LeaderboardSnapshot(long createdAt, List<RankedWallet> entries) {}
}
