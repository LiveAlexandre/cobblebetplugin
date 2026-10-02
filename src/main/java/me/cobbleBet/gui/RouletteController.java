package me.cobbleBet.gui;

import com.google.gson.JsonObject;
import me.cobbleBet.Main;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;

public final class RouletteController implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private static final Set<Integer> RED = Set.of(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36);
    private final Main plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, String> physicalTables = new HashMap<>();

    public RouletteController(Main plugin) { this.plugin = plugin; }

    public void open(Player player) {
        cancelPhysical(player.getUniqueId());
        if (!plugin.requireGameEnabled(player, "roulette") || !request(player, "open", null)) return;
        openBetMenu(player);
    }

    public void openPhysical(Player player, String tableId) {
        if (!plugin.requireGameEnabled(player, "roulette")) {
            plugin.rouletteTableManager.cancel(tableId, player.getUniqueId());
            return;
        }
        physicalTables.put(player.getUniqueId(), tableId);
        if (plugin.gamblingIndicatorManager != null) plugin.gamblingIndicatorManager.expectPhysicalReveal(player.getUniqueId(), "roulette", 100L);
        if (!request(player, "open", null)) { cancelPhysical(player.getUniqueId()); return; }
        openBetMenu(player);
    }

    private void openBetMenu(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.spinning = false;
        RouletteHolder holder = new RouletteHolder(Kind.BET);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Roulette • Set your stake", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, item(Material.GOLD_INGOT, "Balance " + MONEY.format(state.balance), NamedTextColor.GOLD,
                "Choose a stake, then place your bet."));
        double[] amounts = {10, 50, 100, 500, 1000};
        int[] slots = {10, 11, 12, 13, 14};
        for (int index = 0; index < amounts.length; index++)
            inventory.setItem(slots[index], item(Material.GOLD_NUGGET, MONEY.format(amounts[index]), NamedTextColor.GOLD, "Place a Roulette bet for this amount."));
        inventory.setItem(16, item(Material.NAME_TAG, "Custom stake", NamedTextColor.AQUA, "Type an amount in chat."));
        inventory.setItem(22, item(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    private void openBoard(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.bet <= 0) { openBetMenu(player); return; }
        RouletteHolder holder = new RouletteHolder(Kind.BOARD);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Roulette • Bet " + MONEY.format(state.bet), NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        putChoice(inventory, holder, 1, Material.RED_CONCRETE, "Red", "color:red", NamedTextColor.RED, "Pays 37/18× before extra house edge.");
        putChoice(inventory, holder, 4, Material.BLACK_CONCRETE, "Black", "color:black", NamedTextColor.WHITE, "Pays 37/18× before extra house edge.");
        putChoice(inventory, holder, 7, Material.LIME_CONCRETE, "Green", "color:green", NamedTextColor.GREEN, "Wins only when the wheel lands on 0. Pays 37×.");
        for (int number = 1; number <= 36; number++) {
            int slot = 8 + number;
            boolean red = RED.contains(number);
            putChoice(inventory, holder, slot, red ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                    String.valueOf(number), "number:" + number, red ? NamedTextColor.RED : NamedTextColor.WHITE, "Pays 37× before extra house edge.");
        }
        putChoice(inventory, holder, 49, Material.LIME_STAINED_GLASS_PANE, "0", "number:0", NamedTextColor.GREEN, "Pays 37× before extra house edge.");
        inventory.setItem(45, item(Material.ARROW, "Change bet", NamedTextColor.YELLOW, "Current bet: " + MONEY.format(state.bet)));
        inventory.setItem(47, item(Material.GOLD_INGOT, "Stake " + MONEY.format(state.bet), NamedTextColor.GOLD,
                "Pick a color or an exact number."));
        inventory.setItem(53, item(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    private void chooseBet(Player player, double amount) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        if (!validBet(amount)) { player.sendMessage(Component.text("Roulette bets must be at least 0.1 and use one decimal place.", NamedTextColor.RED)); return; }
        state.bet = amount;
        state.awaitingChat = false;
        openBoard(player);
    }

    private void spin(Player player, String choice) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.bet <= 0 || state.spinning) return;
        String[] parts = choice.split(":", 2);
        if (parts.length != 2) return;
        JsonObject extra = new JsonObject();
        extra.addProperty("bet", state.bet);
        extra.addProperty("requestId", UUID.randomUUID().toString());
        extra.addProperty("clientSeed", UUID.randomUUID().toString());
        if (parts[0].equals("color")) extra.addProperty("color", parts[1]);
        else {
            try { extra.addProperty("number", Integer.parseInt(parts[1])); }
            catch (NumberFormatException ignored) { return; }
        }
        state.spinning = true;
        state.choice = choice;
        if (!request(player, "spin", extra)) { state.spinning = false; cancelPhysical(player.getUniqueId()); return; }
        player.closeInventory();
        player.sendActionBar(Component.text("The wheel is spinning…", NamedTextColor.GOLD));
    }

    private boolean request(Player player, String action, JsonObject extra) {
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) {
            player.sendMessage(Component.text("CobbleBet is reconnecting. Try again shortly.", NamedTextColor.RED));
            return false;
        }
        JsonObject json = extra == null ? new JsonObject() : extra;
        json.addProperty("type", "rouletteAction");
        json.addProperty("action", action);
        json.addProperty("playerUUID", player.getUniqueId().toString());
        json.addProperty("playerName", player.getName());
        plugin.cobbleSocketClient.send(json.toString());
        return true;
    }

    public void accept(JsonObject message) {
        if (!message.has("playerUUID") || !message.has("event")) return;
        UUID uuid;
        try { uuid = UUID.fromString(message.get("playerUUID").getAsString()); }
        catch (RuntimeException ignored) { return; }
        String event = message.get("event").getAsString();
        JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            State state = states.computeIfAbsent(uuid, ignored -> new State());
            if (event.equals("error")) {
                state.spinning = false;
                player.sendMessage(Component.text(data.has("message") ? data.get("message").getAsString() : "Roulette action failed.", NamedTextColor.RED));
                if (physicalTables.containsKey(uuid)) openBoard(player); else openBetMenu(player);
                return;
            }
            if (event.equals("roulette-ready") || event.equals("balance-update")) {
                if (data.has("balance")) state.balance = data.get("balance").getAsDouble();
                return;
            }
            if (!event.equals("roulette-result") || !data.has("result")) return;
            JsonObject result = data.getAsJsonObject("result");
            int number = result.get("number").getAsInt();
            String color = result.get("color").getAsString();
            boolean won = data.has("won") && data.get("won").getAsBoolean();
            double payout = data.has("payout") ? data.get("payout").getAsDouble() : 0;
            if (data.has("balance")) state.balance = data.get("balance").getAsDouble();
            state.spinning = false;
            String table = physicalTables.remove(uuid);
            if (table != null) plugin.rouletteTableManager.animate(table, uuid, number, color, won, payout, state.choice);
            else animateGui(player, number, color, won, payout, state.balance);
        });
    }

    private void animateGui(Player player, int result, String color, boolean won, double payout, double balance) {
        RouletteHolder holder = new RouletteHolder(Kind.RESULT);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Roulette • Spinning", NamedTextColor.GOLD));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        int[] ring = {9,10,11,12,13,14,15,16,17};
        for (int slot : ring) inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        player.openInventory(inventory);
        new BukkitRunnable() {
            int tick;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (tick < 28) {
                    int preview = (result + 37 - ((28 - tick) * 5 % 37)) % 37;
                    inventory.setItem(13, numberItem(preview));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .35f, .7f + tick / 32f);
                    tick++;
                    return;
                }
                inventory.setItem(13, numberItem(result));
                inventory.setItem(22, item(won ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                        won ? "YOU WIN " + MONEY.format(payout) : "NO WIN", won ? NamedTextColor.GREEN : NamedTextColor.RED,
                        "Result: " + result + " " + color.toUpperCase(Locale.ROOT), "Balance: " + MONEY.format(balance)));
                inventory.setItem(18, item(Material.LIME_DYE, "Bet again", NamedTextColor.GREEN,
                        "Use the same " + MONEY.format(states.getOrDefault(player.getUniqueId(), new State()).bet) + " stake."));
                inventory.setItem(20, item(Material.GOLD_NUGGET, "Change stake", NamedTextColor.YELLOW));
                inventory.setItem(26, item(Material.BARRIER, "Close", NamedTextColor.RED));
                player.playSound(player.getLocation(), won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, .8f, won ? 1.25f : .72f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private ItemStack numberItem(int number) {
        NamedTextColor color = number == 0 ? NamedTextColor.GREEN : RED.contains(number) ? NamedTextColor.RED : NamedTextColor.WHITE;
        Material material = number == 0 ? Material.LIME_CONCRETE : RED.contains(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        return item(material, String.valueOf(number), color);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof RouletteHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (holder.kind == Kind.BET) {
            if (slot >= 10 && slot <= 14) chooseBet(player, new double[]{10,50,100,500,1000}[slot - 10]);
            else if (slot == 16) customBet(player);
            else if (slot == 22) { cancelPhysical(player.getUniqueId()); player.closeInventory(); }
        } else if (holder.kind == Kind.BOARD) {
            String choice = holder.choices.get(slot);
            if (choice != null) spin(player, choice);
            else if (slot == 45) openBetMenu(player);
            else if (slot == 53) { cancelPhysical(player.getUniqueId()); player.closeInventory(); }
        } else if (slot == 18) openBoard(player);
        else if (slot == 20) openBetMenu(player);
        else if (slot == 26) player.closeInventory();
    }

    @EventHandler public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof RouletteHolder)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            State state = states.get(player.getUniqueId());
            if (state != null && (state.spinning || state.awaitingChat)) return;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RouletteHolder) return;
            cancelPhysical(player.getUniqueId());
        });
    }

    private void customBet(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.awaitingChat = true;
        player.closeInventory();
        player.sendMessage(Component.text("Type your Roulette bet in chat, or type cancel.", NamedTextColor.AQUA));
        new ConversationFactory(plugin).withLocalEcho(false).withTimeout(30).addConversationAbandonedListener(event -> {
            state.awaitingChat = false;
            if (!event.gracefulExit()) Bukkit.getScheduler().runTask(plugin, () -> cancelPhysical(player.getUniqueId()));
        }).withFirstPrompt(new StringPrompt() {
            @Override public @NotNull String getPromptText(@NotNull ConversationContext context) { return ""; }
            @Override public Prompt acceptInput(@NotNull ConversationContext context, String input) {
                state.awaitingChat = false;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (input.equalsIgnoreCase("cancel")) cancelPhysical(player.getUniqueId());
                    else try { chooseBet(player, Double.parseDouble(input.replace(",", ""))); }
                    catch (NumberFormatException ignored) { player.sendMessage(Component.text("Enter a valid Roulette bet.", NamedTextColor.RED)); cancelPhysical(player.getUniqueId()); }
                });
                return END_OF_CONVERSATION;
            }
        }).buildConversation(player).begin();
    }

    private boolean validBet(double amount) { return Double.isFinite(amount) && amount >= .1 && amount <= 1e12 && Math.abs(amount * 10 - Math.round(amount * 10)) <= .001; }
    private void cancelPhysical(UUID uuid) { String table = physicalTables.remove(uuid); if (plugin.gamblingIndicatorManager != null) plugin.gamblingIndicatorManager.cancelPhysicalReveal(uuid); if (table != null) plugin.rouletteTableManager.cancel(table, uuid); }
    private void putChoice(Inventory inventory, RouletteHolder holder, int slot, Material material, String name, String value, NamedTextColor color, String... lore) { inventory.setItem(slot, item(material, name, color, lore)); holder.choices.put(slot, value); }
    private void fill(Inventory inventory, Material material) { ItemStack pane = item(material, " ", NamedTextColor.GRAY); for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane); }
    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false)); if (lore.length > 0) meta.lore(Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); stack.setItemMeta(meta); return stack; }

    private enum Kind { BET, BOARD, RESULT }
    private static final class State { double bet, balance; boolean spinning, awaitingChat; String choice = ""; }
    private static final class RouletteHolder implements InventoryHolder {
        final Kind kind; final Map<Integer, String> choices = new HashMap<>(); Inventory inventory;
        RouletteHolder(Kind kind) { this.kind = kind; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
