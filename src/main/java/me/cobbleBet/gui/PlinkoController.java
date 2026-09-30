package me.cobbleBet.gui;

import com.google.gson.*;
import me.cobbleBet.Main;
import me.cobbleBet.visuals.PlinkoBoardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;

public final class PlinkoController implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private final Main plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, String> physicalBoards = new HashMap<>();

    public PlinkoController(Main plugin) { this.plugin = plugin; }

    public void explain(Player player) {
        player.sendMessage(Component.text("Plinko is played on a physical board. Ask a server owner where the Plinko board is.", NamedTextColor.LIGHT_PURPLE));
    }

    public void openPhysical(Player player, String boardId) {
        if (!plugin.requireGameEnabled(player, "plinko")) { plugin.plinkoBoardManager.cancel(boardId, player.getUniqueId()); return; }
        physicalBoards.put(player.getUniqueId(), boardId);
        if (!request(player, "open", null)) { cancelPhysical(player.getUniqueId()); return; }
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        plugin.plinkoBoardManager.prepare(boardId, player.getUniqueId(), state.rows, state.risk);
        openSetup(player);
    }

    private void openSetup(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Plinko", NamedTextColor.DARK_PURPLE));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, item(Material.SLIME_BALL, "Configure your drop", NamedTextColor.LIGHT_PURPLE, "Choose the options below, then press Drop."));
        inventory.setItem(8, item(Material.GOLD_NUGGET, "Balance: " + MONEY.format(state.balance), NamedTextColor.GOLD));
        inventory.setItem(9, item(Material.GOLD_INGOT, "Stake per ball", NamedTextColor.GOLD, "Selected: " + MONEY.format(state.bet)));
        double[] bets = {10, 50, 100, 500, 1000};
        for (int index = 0; index < bets.length; index++) inventory.setItem(10 + index, selectedStake(state.bet == bets[index], bets[index]));
        inventory.setItem(16, item(Material.NAME_TAG, "Custom stake", NamedTextColor.AQUA, "Enter an amount in chat."));
        inventory.setItem(18, item(Material.COMPASS, "Risk", NamedTextColor.AQUA, "Controls how widely payouts vary."));
        inventory.setItem(20, selectedRisk(state.risk.equals("low"), Material.LIME_DYE, "Low risk", "Payouts stay closer to the center."));
        inventory.setItem(22, selectedRisk(state.risk.equals("medium"), Material.YELLOW_DYE, "Medium risk", "Balanced payout spread."));
        inventory.setItem(24, selectedRisk(state.risk.equals("high"), Material.RED_DYE, "High risk", "Rare outside slots pay the most."));
        inventory.setItem(27, item(Material.IRON_BARS, "Board rows", NamedTextColor.WHITE, "More rows create a longer drop."));
        int[] rows = {8, 10, 12, 14, 16};
        int[] slots = {29, 30, 31, 32, 33};
        for (int index = 0; index < rows.length; index++) inventory.setItem(slots[index], selected(state.rows == rows[index], Material.IRON_NUGGET, rows[index] + " rows", "Changes the length of the physical drop."));
        inventory.setItem(36, item(Material.SNOWBALL, "Ball count", NamedTextColor.WHITE, "Balls launch quickly and overlap on the board."));
        int[] ballCounts = {1, 3, 5, 10, 20};
        int[] ballSlots = {38, 39, 40, 41, 42};
        for (int index = 0; index < ballCounts.length; index++) inventory.setItem(ballSlots[index], selected(state.ballCount == ballCounts[index], Material.SNOWBALL, ballCounts[index] + (ballCounts[index] == 1 ? " ball" : " balls"), "Your stake applies to every ball."));
        double total = state.bet * state.ballCount;
        inventory.setItem(45, item(Material.PAPER, "Drop summary", NamedTextColor.GRAY, MONEY.format(state.bet) + " per ball", state.ballCount + (state.ballCount == 1 ? " ball" : " balls"), "Total stake: " + MONEY.format(total)));
        inventory.setItem(49, item(Material.SLIME_BLOCK, "Drop " + state.ballCount + (state.ballCount == 1 ? " ball" : " balls"), NamedTextColor.GREEN, "Total stake: " + MONEY.format(total), "Click to start."));
        inventory.setItem(53, item(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    private void drop(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        if (state.pending) { player.sendMessage(Component.text("Wait for the current Plinko drop to finish.", NamedTextColor.RED)); return; }
        double bet = state.bet;
        if (!Double.isFinite(bet) || bet < .1 || bet > 1e12 || Math.abs(bet * 10 - Math.round(bet * 10)) > .001) {
            player.sendMessage(Component.text("Plinko stakes must be at least 0.1 and use one decimal place.", NamedTextColor.RED)); return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("bet", bet); data.addProperty("risk", state.risk); data.addProperty("rows", state.rows);
        data.addProperty("ballCount", state.ballCount);
        data.addProperty("clientSeed", UUID.randomUUID().toString()); data.addProperty("requestId", UUID.randomUUID().toString());
        state.pending = true;
        int sequence = ++state.requestSequence;
        if (!request(player, "drop", data)) { state.pending = false; state.requestSequence++; cancelPhysical(player.getUniqueId()); return; }
        player.closeInventory();
        player.sendActionBar(Component.text("Preparing " + state.ballCount + (state.ballCount == 1 ? " Plinko ball…" : " Plinko balls…"), NamedTextColor.LIGHT_PURPLE));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!state.pending || state.requestSequence != sequence) return;
            state.pending = false; state.requestSequence++; cancelPhysical(player.getUniqueId());
            if (player.isOnline()) player.sendMessage(Component.text("The Plinko server did not answer. Restart or update the CobbleBet server, then try again.", NamedTextColor.RED));
        }, 200L);
    }

    private boolean request(Player player, String action, JsonObject data) {
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) {
            player.sendMessage(Component.text("CobbleBet is reconnecting. Try again shortly.", NamedTextColor.RED)); return false;
        }
        JsonObject message = data == null ? new JsonObject() : data;
        message.addProperty("type", "plinkoAction"); message.addProperty("action", action);
        message.addProperty("playerUUID", player.getUniqueId().toString()); message.addProperty("playerName", player.getName());
        plugin.cobbleSocketClient.send(message.toString()); return true;
    }

    public void accept(JsonObject message) {
        if (!message.has("playerUUID") || !message.has("event")) return;
        UUID uuid; try { uuid = UUID.fromString(message.get("playerUUID").getAsString()); } catch (RuntimeException error) { return; }
        String event = message.get("event").getAsString();
        JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid); if (player == null) return;
            State state = states.computeIfAbsent(uuid, ignored -> new State());
            if (event.equals("error")) {
                state.pending = false; state.requestSequence++; player.sendMessage(Component.text(data.has("message") ? data.get("message").getAsString() : "Plinko failed.", NamedTextColor.RED)); cancelPhysical(uuid); return;
            }
            if (event.equals("balance-update") || event.equals("plinko-ready")) { if (data.has("balance")) state.balance = data.get("balance").getAsDouble(); if (event.equals("plinko-ready") && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) openSetup(player); return; }
            if (event.equals("plinko-processing")) { player.sendActionBar(Component.text("CobbleBet received the drop. Syncing your balance…", NamedTextColor.GRAY)); return; }
            if (!event.equals("plinko-result")) return;
            state.pending = false; state.requestSequence++;
            List<Integer> path = new ArrayList<>();
            JsonArray values = data.has("path") && data.get("path").isJsonArray() ? data.getAsJsonArray("path") : null;
            if (values != null) for (JsonElement value : values) try { path.add(value.getAsInt() == 0 ? 0 : 1); } catch (RuntimeException ignored) {}
            double payout = data.has("payout") ? data.get("payout").getAsDouble() : 0;
            int slot = data.has("slot") ? data.get("slot").getAsInt() : path.stream().mapToInt(Integer::intValue).sum();
            String outcome = data.has("outcome") ? data.get("outcome").getAsString() : "loss";
            List<Double> multipliers = new ArrayList<>();
            JsonArray multiplierValues = data.has("multipliers") && data.get("multipliers").isJsonArray() ? data.getAsJsonArray("multipliers") : null;
            if (multiplierValues != null) for (JsonElement value : multiplierValues) try { multipliers.add(value.getAsDouble()); } catch (RuntimeException ignored) {}
            List<PlinkoBoardManager.BallDrop> drops = new ArrayList<>();
            JsonArray balls = data.has("balls") && data.get("balls").isJsonArray() ? data.getAsJsonArray("balls") : null;
            if (balls != null) for (JsonElement element : balls) try {
                JsonObject ball = element.getAsJsonObject(); List<Integer> ballPath = new ArrayList<>();
                for (JsonElement direction : ball.getAsJsonArray("path")) ballPath.add(direction.getAsInt() == 0 ? 0 : 1);
                int ballSlot = ball.has("slot") ? ball.get("slot").getAsInt() : ballPath.stream().mapToInt(Integer::intValue).sum();
                drops.add(new PlinkoBoardManager.BallDrop(ballPath, ballSlot, ball.get("multiplier").getAsDouble(), ball.get("payout").getAsDouble()));
            } catch (RuntimeException ignored) {}
            if (drops.isEmpty() && !path.isEmpty()) drops.add(new PlinkoBoardManager.BallDrop(path, slot, data.has("multiplier") ? data.get("multiplier").getAsDouble() : 0, payout));
            String board = physicalBoards.remove(uuid);
            if (board != null && drops.isEmpty()) { plugin.plinkoBoardManager.cancel(board, uuid); player.sendMessage(Component.text("The Plinko drop was settled, but its animation path was unavailable.", NamedTextColor.RED)); return; }
            if (board != null) plugin.plinkoBoardManager.animate(board, uuid, drops, payout, outcome, multipliers);
        });
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true); if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State()); int slot = event.getRawSlot();
        if (slot >= 10 && slot <= 14) { double[] bets = {10, 50, 100, 500, 1000}; state.bet = bets[slot - 10]; }
        if (slot == 16) { custom(player); return; }
        else if (slot == 20) state.risk = "low"; else if (slot == 22) state.risk = "medium"; else if (slot == 24) state.risk = "high";
        else if (slot == 29) state.rows = 8; else if (slot == 30) state.rows = 10; else if (slot == 31) state.rows = 12; else if (slot == 32) state.rows = 14; else if (slot == 33) state.rows = 16;
        else if (slot == 38) state.ballCount = 1; else if (slot == 39) state.ballCount = 3; else if (slot == 40) state.ballCount = 5; else if (slot == 41) state.ballCount = 10; else if (slot == 42) state.ballCount = 20;
        else if (slot == 49) { drop(player); return; } else if (slot == 53) { player.closeInventory(); return; } else if (slot < 10 || slot > 14) return;
        String board = physicalBoards.get(player.getUniqueId());
        if (board != null) plugin.plinkoBoardManager.prepare(board, player.getUniqueId(), state.rows, state.risk);
        openSetup(player);
    }

    @EventHandler public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof Holder)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) return;
            State state = states.get(player.getUniqueId()); if (state != null && state.pending) return; cancelPhysical(player.getUniqueId());
        });
    }

    private void custom(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State()); state.pending = true; player.closeInventory();
        player.sendMessage(Component.text("Type your Plinko stake in chat, or type cancel.", NamedTextColor.AQUA));
        new ConversationFactory(plugin).withLocalEcho(false).withTimeout(30).addConversationAbandonedListener(event -> {
            if (!event.gracefulExit()) { state.pending = false; Bukkit.getScheduler().runTask(plugin, () -> cancelPhysical(player.getUniqueId())); }
        }).withFirstPrompt(new StringPrompt() {
            @Override public @NotNull String getPromptText(@NotNull ConversationContext context) { return ""; }
            @Override public Prompt acceptInput(@NotNull ConversationContext context, String input) { state.pending = false; Bukkit.getScheduler().runTask(plugin, () -> { if (input.equalsIgnoreCase("cancel")) { openSetup(player); return; } try { double value = Double.parseDouble(input.replace(",", "")); if (!validBet(value)) throw new NumberFormatException(); state.bet = value; openSetup(player); } catch (NumberFormatException error) { player.sendMessage(Component.text("Enter a valid stake of at least 0.1 using one decimal place.", NamedTextColor.RED)); openSetup(player); } }); return END_OF_CONVERSATION; }
        }).buildConversation(player).begin();
    }

    private void cancelPhysical(UUID player) { String board = physicalBoards.remove(player); if (board != null) plugin.plinkoBoardManager.cancel(board, player); }
    private void fill(Inventory inventory, Material material) { ItemStack pane = item(material, " ", NamedTextColor.GRAY); for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane); }
    private boolean validBet(double value) { return Double.isFinite(value) && value >= .1 && value <= 1e12 && Math.abs(value * 10 - Math.round(value * 10)) <= .001; }
    private ItemStack selectedStake(boolean selected, double amount) { return item(selected ? Material.EMERALD : Material.GOLD_INGOT, MONEY.format(amount), selected ? NamedTextColor.GREEN : NamedTextColor.GOLD, selected ? "Selected stake per ball" : "Click to select this stake."); }
    private ItemStack selectedRisk(boolean selected, Material material, String name, String lore) { return item(selected ? Material.NETHER_STAR : material, name, selected ? NamedTextColor.GREEN : NamedTextColor.WHITE, selected ? "Selected" : lore); }
    private ItemStack selected(boolean selected, Material material, String name, String lore) { return item(selected ? Material.LIME_DYE : material, name, selected ? NamedTextColor.GREEN : NamedTextColor.WHITE, selected ? "Selected" : lore); }
    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false)); if (lore.length > 0) meta.lore(Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); stack.setItemMeta(meta); return stack; }
    private static final class State { String risk = "medium"; int rows = 12, ballCount = 1; boolean pending; int requestSequence; double balance, bet = 50; }
    private static final class Holder implements InventoryHolder { Inventory inventory; @Override public @NotNull Inventory getInventory() { return inventory; } }
}
