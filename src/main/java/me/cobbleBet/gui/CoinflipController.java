package me.cobbleBet.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.cobbleBet.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CoinflipController implements Listener {
    private static final int[] LISTING_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private final Main plugin;
    private final List<Listing> listings = new CopyOnWriteArrayList<>();
    private final Map<UUID, View> openViews = new HashMap<>();
    private String currency = "Coins";
    private double maximumAmount = 1_000_000_000_000D;

    public CoinflipController(Main plugin) { this.plugin = plugin; }

    public void requestLobby() {
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) return;
        JsonObject request = new JsonObject(); request.addProperty("type", "coinflipAction"); request.addProperty("action", "list");
        plugin.cobbleSocketClient.send(request.toString());
    }

    public void acceptLobby(JsonObject json) {
        List<Listing> next = new ArrayList<>();
        if (json.has("listings") && json.get("listings").isJsonArray()) {
            for (var element : json.getAsJsonArray("listings")) {
                try {
                    JsonObject row = element.getAsJsonObject();
                    next.add(new Listing(row.get("id").getAsString(), row.get("creatorName").getAsString(),
                            row.has("creatorUUID") && !row.get("creatorUUID").isJsonNull() ? row.get("creatorUUID").getAsString() : "",
                            row.get("amount").getAsDouble(), row.get("payout").getAsDouble(), row.get("createdAt").getAsLong()));
                } catch (RuntimeException ignored) {}
            }
        }
        listings.clear(); listings.addAll(next);
        if (json.has("currency")) currency = json.get("currency").getAsString();
        if (json.has("maximumAmount") && json.get("maximumAmount").getAsDouble() > 0) maximumAmount = json.get("maximumAmount").getAsDouble();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (var entry : new ArrayList<>(openViews.entrySet())) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && entry.getValue().kind == Kind.LOBBY) renderLobby(player, entry.getValue().page);
            }
            if (plugin.coinflipBoardManager != null) plugin.coinflipBoardManager.updateListings(listings);
        });
    }

    public void openLobby(Player player, int page) {
        if (!plugin.requireGameEnabled(player, "coinflip")) return;
        requestLobby();
        renderLobby(player, page);
    }

    private void renderLobby(Player player, int requestedPage) {
        int pages = Math.max(1, (int)Math.ceil(listings.size() / (double)LISTING_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(Kind.LOBBY, page, null, false);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("Coinflip Lobby", NamedTextColor.DARK_AQUA)); holder.inventory = inv;
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        int start = page * LISTING_SLOTS.length;
        for (int i = 0; i < LISTING_SLOTS.length && start + i < listings.size(); i++) {
            Listing listing = listings.get(start + i);
            inv.setItem(LISTING_SLOTS[i], listingItem(player, listing));
        }
        if (listings.isEmpty()) inv.setItem(22, item(Material.CLOCK, "No open Coinflips", NamedTextColor.GRAY,
                "Create one and wait for a challenger."));
        inv.setItem(36, item(Material.EMERALD, "Create a Coinflip", NamedTextColor.GREEN,
                "Choose a stake or enter a custom amount.", "Maximum: " + money(maximumAmount)));
        if (page > 0) inv.setItem(39, item(Material.ARROW, "Previous page", NamedTextColor.WHITE));
        inv.setItem(40, item(Material.SUNFLOWER, "Refresh", NamedTextColor.YELLOW, listings.size() + " open on this server"));
        if (page + 1 < pages) inv.setItem(41, item(Material.ARROW, "Next page", NamedTextColor.WHITE));
        inv.setItem(44, item(Material.BARRIER, "Close", NamedTextColor.RED));
        openViews.put(player.getUniqueId(), new View(Kind.LOBBY, page));
        player.openInventory(inv);
    }

    public void openCreateMenu(Player player) {
        if (!plugin.requireGameEnabled(player, "coinflip")) return;
        MenuHolder holder = new MenuHolder(Kind.CREATE, 0, null, false);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Create a Coinflip", NamedTextColor.DARK_GREEN)); holder.inventory = inv;
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        double[] values = {10, 50, 100, 500, 1000};
        int[] slots = {10,11,12,13,14};
        for (int i=0;i<values.length;i++) inv.setItem(slots[i], item(Material.GOLD_NUGGET, money(values[i]), NamedTextColor.GOLD,
                "Click to list this stake."));
        inv.setItem(16, item(Material.NAME_TAG, "Custom amount", NamedTextColor.AQUA,
                "Type any amount in chat.", "Maximum: " + money(maximumAmount)));
        inv.setItem(22, item(Material.ARROW, "Back to lobby", NamedTextColor.GRAY));
        openViews.put(player.getUniqueId(), new View(Kind.CREATE, 0));
        player.openInventory(inv);
    }

    public void create(Player player, String rawAmount) {
        if (!plugin.requireGameEnabled(player, "coinflip")) return;
        double amount;
        try { amount = Double.parseDouble(rawAmount.replace(",", "")); }
        catch (NumberFormatException error) { player.sendMessage(Component.text("Enter a valid Coinflip amount.", NamedTextColor.RED)); return; }
        if (!Double.isFinite(amount) || amount < .1 || amount > maximumAmount || Math.abs(amount * 10 - Math.round(amount * 10)) > .001) {
            player.sendMessage(Component.text("Choose an amount from 0.1 to " + money(maximumAmount) + ".", NamedTextColor.RED)); return;
        }
        send(player, "create", null, amount);
    }

    public void confirm(Player player, String id, boolean cancel) {
        if (!cancel && !plugin.requireGameEnabled(player, "coinflip")) return;
        Listing listing = listings.stream().filter(row -> row.id.equals(id)).findFirst().orElse(null);
        if (listing == null) { player.sendMessage(Component.text("That Coinflip is no longer open.", NamedTextColor.RED)); requestLobby(); return; }
        MenuHolder holder = new MenuHolder(Kind.CONFIRM, 0, id, cancel);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(cancel ? "Cancel Coinflip?" : "Match Coinflip?", NamedTextColor.GOLD)); holder.inventory = inv;
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(13, listingItem(player, listing));
        inv.setItem(11, item(Material.LIME_CONCRETE, cancel ? "Cancel and refund" : "Match for " + money(listing.amount), NamedTextColor.GREEN,
                cancel ? "Your reserved stake will be returned." : "This starts the winner reveal."));
        inv.setItem(15, item(Material.RED_CONCRETE, "Go back", NamedTextColor.RED));
        openViews.put(player.getUniqueId(), new View(Kind.CONFIRM, 0));
        player.openInventory(inv);
    }

    private void send(Player player, String action, String id, double amount) {
        if (!action.equals("cancel") && !plugin.requireGameEnabled(player, "coinflip")) return;
        if (plugin.cobbleSocketClient == null || !plugin.cobbleSocketClient.isApproved()) {
            player.sendMessage(Component.text("CobbleBet is reconnecting. Try again shortly.", NamedTextColor.RED)); return;
        }
        JsonObject request = new JsonObject(); request.addProperty("type", "coinflipAction"); request.addProperty("action", action);
        request.addProperty("playerUUID", player.getUniqueId().toString()); request.addProperty("playerName", player.getName());
        if (id != null) request.addProperty("id", id); if (amount > 0) request.addProperty("amount", amount);
        plugin.cobbleSocketClient.send(request.toString());
        player.closeInventory();
        player.sendActionBar(Component.text(action.equals("create") ? "Listing your Coinflip…" : action.equals("join") ? "Matching Coinflip…" : "Cancelling Coinflip…", NamedTextColor.GRAY));
    }

    private void customPrompt(Player player) {
        player.closeInventory();
        player.sendMessage(Component.text("Type your Coinflip stake in chat, or type cancel.", NamedTextColor.AQUA));
        new ConversationFactory(plugin).withLocalEcho(false).withTimeout(30).withFirstPrompt(new StringPrompt() {
            @Override public @NotNull String getPromptText(@NotNull org.bukkit.conversations.ConversationContext context) { return ""; }
            @Override public Prompt acceptInput(@NotNull org.bukkit.conversations.ConversationContext context, String input) {
                if (!input.equalsIgnoreCase("cancel")) Bukkit.getScheduler().runTask(plugin, () -> create(player, input));
                else player.sendMessage(Component.text("Coinflip creation cancelled.", NamedTextColor.GRAY));
                return END_OF_CONVERSATION;
            }
        }).buildConversation(player).begin();
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true); if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (holder.kind == Kind.LOBBY) {
            if (slot == 36) openCreateMenu(player); else if (slot == 40) openLobby(player, holder.page); else if (slot == 44) player.closeInventory();
            else if (slot == 39 && holder.page > 0) renderLobby(player, holder.page - 1); else if (slot == 41) renderLobby(player, holder.page + 1);
            else {
                int index = indexOfSlot(slot); if (index < 0) return; int absolute = holder.page * LISTING_SLOTS.length + index;
                if (absolute < listings.size()) { Listing listing = listings.get(absolute); confirm(player, listing.id, listing.isMine(player)); }
            }
        } else if (holder.kind == Kind.CREATE) {
            if (slot >= 10 && slot <= 14) { double[] values={10,50,100,500,1000}; create(player, String.valueOf(values[slot-10])); }
            else if (slot == 16) customPrompt(player); else if (slot == 22) openLobby(player, 0);
        } else if (holder.kind == Kind.CONFIRM) {
            if (slot == 11) send(player, holder.cancel ? "cancel" : "join", holder.id, -1);
            else if (slot == 15) openLobby(player, 0);
        } else if (holder.kind == Kind.REVEAL && slot == 22) {
            openLobby(player, 0);
        }
    }

    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder && holder.kind != Kind.REVEAL) openViews.remove(event.getPlayer().getUniqueId());
    }

    public void actionResult(JsonObject json) {
        if (!json.has("playerUUID")) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player;
            try { player = Bukkit.getPlayer(UUID.fromString(json.get("playerUUID").getAsString())); } catch (RuntimeException e) { return; }
            if (player == null) return;
            boolean success = json.has("success") && json.get("success").getAsBoolean();
            String message = json.has("message") ? json.get("message").getAsString() : "Coinflip updated.";
            player.sendMessage(Component.text(message, success ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (success) requestLobby();
        });
    }

    public void reveal(Player player, boolean won, double amount, String opponent) {
        MenuHolder holder = new MenuHolder(Kind.REVEAL, 0, null, false);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Coinflip • Winner reveal", NamedTextColor.GOLD)); holder.inventory=inv;
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        OfflinePlayer self = player; OfflinePlayer other = Bukkit.getOfflinePlayer(opponent);
        player.openInventory(inv);
        new BukkitRunnable() {
            int tick, step; long next;
            @Override public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) { cancel(); return; }
                tick++; if (tick < next) return;
                step++; boolean showSelf = step % 2 == 0;
                for (int slot=10;slot<=16;slot++) inv.setItem(slot, head((slot + step) % 2 == 0 ? self : other,
                        (slot + step) % 2 == 0 ? player.getName() : opponent, NamedTextColor.WHITE));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .35f, 1.1f + Math.min(.7f, step*.025f));
                next = tick + (step < 12 ? 2 : step < 20 ? 4 : 7);
                if (step >= 25) {
                    OfflinePlayer winner = won ? self : other; String winnerName = won ? player.getName() : opponent;
                    for (int slot=10;slot<=16;slot++) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
                    inv.setItem(13, head(winner, winnerName + " wins", won ? NamedTextColor.GREEN : NamedTextColor.RED));
                    inv.setItem(22, item(won ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                            won ? "You won " + money(amount) : "You lost " + money(amount), won ? NamedTextColor.GREEN : NamedTextColor.RED,
                            "Opponent: " + opponent, "Click to return to the lobby."));
                    player.playSound(player.getLocation(), won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, .8f, won ? 1.2f : .7f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private ItemStack listingItem(Player viewer, Listing listing) {
        OfflinePlayer owner;
        try { owner = listing.creatorUUID.isBlank() ? Bukkit.getOfflinePlayer(listing.creatorName) : Bukkit.getOfflinePlayer(UUID.fromString(listing.creatorUUID)); }
        catch (IllegalArgumentException ignored) { owner = Bukkit.getOfflinePlayer(listing.creatorName); }
        ItemStack head = head(owner, listing.creatorName, NamedTextColor.AQUA);
        ItemMeta meta=head.getItemMeta(); meta.lore(List.of(Component.text("Stake: " + money(listing.amount), NamedTextColor.GOLD),
                Component.text("Winner receives: " + money(listing.payout), NamedTextColor.GREEN),
                Component.text(listing.isMine(viewer) ? "Click to cancel and refund" : "Click to match", listing.isMine(viewer) ? NamedTextColor.RED : NamedTextColor.YELLOW)));
        head.setItemMeta(meta); return head;
    }
    private ItemStack head(OfflinePlayer owner, String name, NamedTextColor color) { ItemStack stack=new ItemStack(Material.PLAYER_HEAD); SkullMeta meta=(SkullMeta)stack.getItemMeta(); meta.setOwningPlayer(owner); meta.displayName(Component.text(name,color).decoration(TextDecoration.ITALIC,false)); stack.setItemMeta(meta); return stack; }
    private ItemStack item(Material material,String name,NamedTextColor color,String... lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Component.text(name,color).decoration(TextDecoration.ITALIC,false));if(lore.length>0)meta.lore(Arrays.stream(lore).map(s->Component.text(s,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());stack.setItemMeta(meta);return stack;}
    private void fill(Inventory inventory,Material material){ItemStack pane=item(material," ",NamedTextColor.GRAY);for(int i=0;i<inventory.getSize();i++)inventory.setItem(i,pane);}
    private int indexOfSlot(int slot){for(int i=0;i<LISTING_SLOTS.length;i++)if(LISTING_SLOTS[i]==slot)return i;return -1;}
    private String money(double amount){return MONEY.format(amount)+" "+currency;}
    public List<Listing> getListings(){return List.copyOf(listings);}

    public record Listing(String id,String creatorName,String creatorUUID,double amount,double payout,long createdAt){public boolean isMine(Player player){return player.getUniqueId().toString().equalsIgnoreCase(creatorUUID);}}
    private enum Kind { LOBBY, CREATE, CONFIRM, REVEAL }
    private record View(Kind kind,int page){}
    private static final class MenuHolder implements InventoryHolder { final Kind kind;final int page;final String id;final boolean cancel;Inventory inventory;MenuHolder(Kind kind,int page,String id,boolean cancel){this.kind=kind;this.page=page;this.id=id;this.cancel=cancel;}@Override public @NotNull Inventory getInventory(){return inventory;}}
}
