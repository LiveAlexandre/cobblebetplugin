package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import me.cobbleBet.gui.BlackjackController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public final class BlackjackTableManager implements Listener {
    private final Main plugin;
    private final NamespacedKey key;
    private final Map<String, View> tables = new LinkedHashMap<>();
    private final Map<String, UUID> activePlayers = new HashMap<>();

    public BlackjackTableManager(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "blackjack_table");
    }

    public void load() {
        clearAll();
        for (Map<?, ?> map : plugin.getDataStore().getMapList("physicalGames.blackjack.tables")) {
            try {
                World world = Bukkit.getWorld(String.valueOf(map.get("world")));
                Style style = Style.parse(String.valueOf(map.get("style")));
                PlayMode mode = PlayMode.parse(String.valueOf(map.get("playMode")));
                if (world == null || style == null) continue;
                if (mode == null) mode = PlayMode.GUI;
                float yaw = Math.round((float) number(map.get("yaw")) / 22.5f) * 22.5f;
                boolean glowing = Boolean.parseBoolean(String.valueOf(map.get("glowing")));
                spawn(new Table(String.valueOf(map.get("id")), new Location(world, number(map.get("x")), number(map.get("y")), number(map.get("z")), yaw, 0), style, mode, String.valueOf(map.get("name")), glowing));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Skipped an invalid Blackjack table: " + error.getMessage());
            }
        }
    }

    public void create(Player player, String styleName, String name) { create(player, styleName, "gui", name); }
    public void create(Player player, String styleName, String modeName, String name) {
        Style style = Style.parse(styleName);
        PlayMode mode = PlayMode.parse(modeName);
        if (style == null || mode == null) { player.sendMessage("§cChoose a valid Blackjack style and play mode."); return; }
        Table table = new Table(nextId(), placement(player), style, mode, cleanName(name), false);
        spawn(table); save();
        player.sendMessage("§aCreated Blackjack table §f" + table.id + "§a in §f" + mode.id + " §amode.");
    }

    public boolean moveHere(Player player, String id) {
        View view = available(player, id); if (view == null) return false;
        replace(view, new Table(view.table.id, placement(player), view.table.style, view.table.mode, view.table.name, view.table.glowing));
        player.sendMessage("§aMoved Blackjack table §f" + id + "§a."); return true;
    }
    public boolean changeStyle(Player player, String id, String value) {
        View view = available(player, id); Style style = Style.parse(value); if (view == null) return false;
        if (style == null) { player.sendMessage("§cUnknown style. Choose: " + String.join(", ", styles())); return false; }
        replace(view, new Table(view.table.id, view.table.location.clone(), style, view.table.mode, view.table.name, view.table.glowing)); return true;
    }
    public boolean changeMode(Player player, String id, String value) {
        View view = available(player, id); PlayMode mode = PlayMode.parse(value); if (view == null) return false;
        if (mode == null) { player.sendMessage("§cChoose gui or world."); return false; }
        replace(view, new Table(view.table.id, view.table.location.clone(), view.table.style, mode, view.table.name, view.table.glowing)); return true;
    }
    public boolean toggleGlow(Player player, String id) {
        View view = available(player, id); if (view == null) return false;
        replace(view, new Table(view.table.id, view.table.location.clone(), view.table.style, view.table.mode, view.table.name, !view.table.glowing)); return true;
    }
    public boolean rotate(Player player, String id, int quarterTurns) {
        View view = available(player, id); if (view == null) return false;
        Location location = view.table.location.clone(); location.setYaw(Math.round((location.getYaw() + quarterTurns * 90f) / 90f) * 90f);
        replace(view, new Table(view.table.id, location, view.table.style, view.table.mode, view.table.name, view.table.glowing)); return true;
    }
    public boolean rotateByDegrees(Player player, String id, float degrees) {
        View view = available(player, id); if (view == null) return false;
        Location location = view.table.location.clone(); location.setYaw(Math.round((location.getYaw() + degrees) / 22.5f) * 22.5f);
        replace(view, new Table(view.table.id, location, view.table.style, view.table.mode, view.table.name, view.table.glowing)); return true;
    }
    public boolean rename(Player player, String id, String name) {
        View view = available(player, id); if (view == null) return false;
        replace(view, new Table(view.table.id, view.table.location.clone(), view.table.style, view.table.mode, cleanName(name), view.table.glowing)); return true;
    }
    public boolean remove(Player player, String id) {
        View view = available(player, id); if (view == null) return false;
        view.remove(); tables.remove(view.table.id); activePlayers.remove(view.table.id); save(); return true;
    }

    public Set<String> styles() { return new LinkedHashSet<>(Arrays.stream(Style.values()).map(style -> style.id).toList()); }
    public List<TableSummary> summaries() { return tables.values().stream().map(view -> view.table.summary()).toList(); }
    public TableSummary summary(String id) { View view = find(id); return view == null ? null : view.table.summary(); }
    public void clearAll() { tables.values().forEach(View::remove); tables.clear(); activePlayers.clear(); }

    public void update(String id, UUID player, List<BlackjackController.Card> playerHand, List<BlackjackController.Card> dealerHand, int playerTotal, int dealerTotal, boolean started, String result) {
        View view = find(id); if (view == null) return;
        UUID active = activePlayers.get(id); if (active != null && !active.equals(player)) return;
        activePlayers.put(id, player); clearDynamic(view); view.opener = null;
        renderHand(view, dealerHand, true, started);
        renderHand(view, playerHand, false, false);
        view.feltMark.text(Component.text((started ? "?" : dealerTotal) + " DEALER     •     " + playerTotal + " YOU", view.table.style.accent).decorate(TextDecoration.BOLD));
        String totals = (started ? "?" : dealerTotal) + " DEALER  •  YOU " + playerTotal;
        view.prompt.text(Component.text(totals + "\n" + (result != null ? result : "CHOOSE YOUR MOVE"), result == null ? NamedTextColor.WHITE : view.table.style.accent).decorate(TextDecoration.BOLD));
        if (started) {
            control(view, .86, "HIT", "hit", Material.GOLD_BLOCK, NamedTextColor.YELLOW);
            control(view, 0, "STAND", "stand", Material.IRON_BLOCK, NamedTextColor.WHITE);
            control(view, -.86, "DOUBLE", "double", Material.DIAMOND_BLOCK, NamedTextColor.AQUA);
        }
    }

    public void finish(String id, UUID player, List<BlackjackController.Card> playerHand, List<BlackjackController.Card> dealerHand, int playerTotal, int dealerTotal, String result) {
        update(id, player, playerHand, dealerHand, playerTotal, dealerTotal, false, result);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.equals(activePlayers.get(id))) { activePlayers.remove(id); View view = find(id); if (view != null) reset(view); }
        }, 160L);
    }
    public void cancel(String id, UUID player) { if (player.equals(activePlayers.get(id))) { activePlayers.remove(id); View view = find(id); if (view != null) reset(view); } }

    private void spawn(Table table) {
        World world = table.location.getWorld(); if (world == null) return;
        float face = table.location.getYaw() + 180; List<Entity> structure = new ArrayList<>();
        structure.add(block(table, point(table.location, 0, .75, 0), face, table.style.base, 3.0f, .18f, 1.72f));
        structure.add(block(table, point(table.location, 0, .855, 0), face, table.style.felt, 2.68f, .045f, 1.40f));
        structure.add(block(table, point(table.location, 0, .89, -.77), face, table.style.rail, 3.08f, .12f, .16f));
        structure.add(block(table, point(table.location, 0, .89, .77), face, table.style.rail, 3.08f, .12f, .16f));
        structure.add(block(table, point(table.location, -1.46, .89, 0), face, table.style.rail, .16f, .12f, 1.42f));
        structure.add(block(table, point(table.location, 1.46, .89, 0), face, table.style.rail, .16f, .12f, 1.42f));
        for (double x : new double[]{-1.22, 1.22}) for (double z : new double[]{-.57, .57}) structure.add(block(table, point(table.location, x, .36, z), face, table.style.leg, .18f, .72f, .18f));
        for (double x : new double[]{-1.42, 1.42}) for (double z : new double[]{-.72, .72}) structure.add(block(table, point(table.location, x, .955, z), face, table.style.trim, .17f, .055f, .17f));
        TextDisplay title = text(table, point(table.location, 0, .73, .91), face, .66f); title.text(Component.text("♠  " + table.name + "  ♠", table.style.accent).decorate(TextDecoration.BOLD)); structure.add(title);
        TextDisplay prompt = text(table, point(table.location, 0, .48, .92), face, .50f); structure.add(prompt);
        TextDisplay feltMark = flatText(table, point(table.location, 0, .925, .02), face, .48f); structure.add(feltMark);
        View view = new View(table, title, prompt, feltMark, structure); tables.put(table.id, view); reset(view);
    }

    private void reset(View view) {
        clearDynamic(view);
        view.prompt.text(Component.text("RIGHT CLICK TO PLAY", NamedTextColor.WHITE));
        view.feltMark.text(Component.text("♠    ♥    21    ♦    ♣", view.table.style.accent).decorate(TextDecoration.BOLD));
        view.opener = interaction(point(view.table.location, 0, .95, .12), 2.95f, .72f, "open:" + view.table.id);
        view.dynamic.add(view.opener);
        idleCard(view, -.25, -.34, Material.PAPER, -13); idleCard(view, 0, -.32, Material.MAP, 0); idleCard(view, .25, -.34, Material.PAPER, 13);
        idleCard(view, -.16, .38, Material.MAP, -8); idleCard(view, .16, .39, Material.PAPER, 9);
        for (double side : new double[]{-.82, .82}) {
            Material first = side < 0 ? view.table.style.chipA : view.table.style.chipB, second = side < 0 ? view.table.style.chipB : view.table.style.chipA;
            view.dynamic.add(chip(view.table, point(view.table.location, side, .966, .27), view.table.location.getYaw() + 180, first, .25f));
            view.dynamic.add(chip(view.table, point(view.table.location, side - .10, .969, .38), view.table.location.getYaw() + 180, second, .21f));
            view.dynamic.add(chip(view.table, point(view.table.location, side + .10, .972, .38), view.table.location.getYaw() + 180, first, .21f));
        }
    }

    private void renderHand(View view, List<BlackjackController.Card> cards, boolean dealer, boolean hideSecond) {
        int count = Math.min(cards.size(), 7); float face = view.table.location.getYaw() + 180;
        for (int i = 0; i < count; i++) {
            boolean hidden = dealer && hideSecond && i == 1; BlackjackController.Card card = cards.get(i);
            double right = (i - (count - 1) / 2.0) * .34, forward = dealer ? -.38 : .34, up = .965 + i * .002;
            view.dynamic.add(card(view.table, point(view.table.location, right, up, forward), face, hidden ? Material.MAP : Material.PAPER, (i - count / 2f) * 3f));
            TextDisplay label = cardLabel(view.table, point(view.table.location, right, up + .07, forward), face, .45f);
            boolean red = card.suit().equals("♥") || card.suit().equals("♦");
            label.text(Component.text(hidden ? "?" : card.rank() + card.suit(), hidden ? NamedTextColor.DARK_GRAY : red ? NamedTextColor.RED : NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
            view.dynamic.add(label);
        }
    }

    private void control(View view, double right, String label, String action, Material material, NamedTextColor color) {
        float face = view.table.location.getYaw() + 180;
        view.dynamic.add(block(view.table, point(view.table.location, right, .90, .96), face, view.table.style.rail, .72f, .11f, .34f));
        view.dynamic.add(block(view.table, point(view.table.location, right, .975, .96), face, material, .61f, .045f, .25f));
        TextDisplay text = text(view.table, point(view.table.location, right, .82, 1.15), face, .30f); text.text(Component.text(label, color).decorate(TextDecoration.BOLD)); view.dynamic.add(text);
        view.dynamic.add(interaction(point(view.table.location, right, .91, 1.08), .64f, .28f, "action:" + view.table.id + ":" + action));
    }

    private void idleCard(View view, double right, double forward, Material material, float tilt) { view.dynamic.add(card(view.table, point(view.table.location, right, .96, forward), view.table.location.getYaw() + 180, material, tilt)); }
    private Interaction interaction(Location at, float width, float height, String value) { return at.getWorld().spawn(at, Interaction.class, entity -> { entity.setInteractionWidth(width); entity.setInteractionHeight(height); entity.setResponsive(true); entity.setPersistent(false); entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value); }); }
    private BlockDisplay block(Table table, Location at, float yaw, Material material, float width, float height, float depth) { return at.getWorld().spawn(at, BlockDisplay.class, entity -> { entity.setBlock(material.createBlockData()); display(entity, table); entity.setRotation(yaw, 0); entity.setTransformation(new Transformation(new Vector3f(-width / 2, -height / 2, -depth / 2), new Quaternionf(), new Vector3f(width, height, depth), new Quaternionf())); }); }
    private TextDisplay text(Table table, Location at, float yaw, float scale) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 0); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setLineWidth(360); entity.setShadowed(true); entity.setSeeThrough(false); entity.setBackgroundColor(Color.fromARGB(165, 7, 10, 8)); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private TextDisplay flatText(Table table, Location at, float yaw, float scale) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, -90); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setShadowed(false); entity.setSeeThrough(false); entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private TextDisplay cardLabel(Table table, Location at, float yaw, float scale) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, -70); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setLineWidth(90); entity.setShadowed(true); entity.setSeeThrough(true); entity.setDefaultBackground(false); entity.setBackgroundColor(Color.fromARGB(252, 250, 248, 240)); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private ItemDisplay card(Table table, Location at, float yaw, Material material, float tilt) { return at.getWorld().spawn(at, ItemDisplay.class, entity -> { entity.setItemStack(new ItemStack(material)); display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 90); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) Math.toRadians(tilt)), new Vector3f(.27f), new Quaternionf())); }); }
    private ItemDisplay chip(Table table, Location at, float yaw, Material material, float scale) { return at.getWorld().spawn(at, ItemDisplay.class, entity -> { entity.setItemStack(new ItemStack(material)); display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 0); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private void display(Display display, Table table) { display.setPersistent(false); display.setViewRange(1.5f); display.setGlowing(table.glowing); if (table.glowing) display.setGlowColorOverride(table.style.glow); }

    @EventHandler public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String value = event.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING); if (value == null) return;
        event.setCancelled(true); String[] parts = value.split(":"); if (parts.length < 2) return;
        View view = find(parts[1]); if (view == null) return; Player player = event.getPlayer();
        if (parts[0].equals("open")) {
            if (view.table.mode == PlayMode.GUI) { plugin.blackjackController.open(player); return; }
            UUID active = activePlayers.get(view.table.id);
            if (active == null) { if (activePlayers.containsValue(player.getUniqueId())) { player.sendMessage("§cYou are already using another Blackjack table."); return; } activePlayers.put(view.table.id, player.getUniqueId()); plugin.blackjackController.openPhysical(player, view.table.id); }
            else if (!active.equals(player.getUniqueId())) player.sendMessage("§cThis Blackjack table is already in use.");
            return;
        }
        if (parts.length == 3 && parts[0].equals("action") && player.getUniqueId().equals(activePlayers.get(view.table.id))) plugin.blackjackController.action(player, parts[2]);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID player = event.getPlayer().getUniqueId(); for (String id : new ArrayList<>(activePlayers.keySet())) if (player.equals(activePlayers.get(id))) cancel(id, player); }

    private View available(Player player, String id) { View view = find(id); if (view == null) { missing(player, id); return null; } if (activePlayers.containsKey(view.table.id)) { player.sendMessage("§cWait for the active Blackjack hand to finish first."); return null; } return view; }
    private void replace(View old, Table table) { old.remove(); tables.remove(old.table.id); spawn(table); save(); }
    private View find(String id) { return id == null ? null : tables.get(id.toLowerCase(Locale.ROOT)); }
    private boolean missing(Player player, String id) { player.sendMessage("§cNo Blackjack table exists with ID §f" + id + "§c."); return false; }
    private String cleanName(String name) { return name == null || name.isBlank() ? "BLACKJACK" : name.substring(0, Math.min(28, name.length())); }
    private void clearDynamic(View view) { view.dynamic.forEach(this::removeEntity); view.dynamic.clear(); }
    private void removeEntity(Entity entity) { if (entity != null && !entity.isDead()) entity.remove(); }
    private Location placement(Player player) { Location location = player.getLocation().clone(); location.setPitch(0); location.setYaw(Math.round(location.getYaw() / 22.5f) * 22.5f); location.add(direction(location.getYaw()).multiply(3)); location.setY(Math.floor(location.getY()) + .03); return location; }
    private Location point(Location origin, double right, double up, double forward) { double radians = Math.toRadians(origin.getYaw()); return origin.clone().add(-Math.cos(radians) * right + Math.sin(radians) * forward, up, -Math.sin(radians) * right - Math.cos(radians) * forward); }
    private org.bukkit.util.Vector direction(float yaw) { double radians = Math.toRadians(yaw); return new org.bukkit.util.Vector(-Math.sin(radians), 0, Math.cos(radians)); }
    private double number(Object value) { return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value)); }
    private String nextId() { String id; do { id = UUID.randomUUID().toString().substring(0, 6).toLowerCase(Locale.ROOT); } while (tables.containsKey(id)); return id; }

    private void save() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (View view : tables.values()) { Table table = view.table; Map<String, Object> row = new LinkedHashMap<>(); row.put("id", table.id); row.put("world", table.location.getWorld().getName()); row.put("x", table.location.getX()); row.put("y", table.location.getY()); row.put("z", table.location.getZ()); row.put("yaw", table.location.getYaw()); row.put("style", table.style.id); row.put("playMode", table.mode.id); row.put("name", table.name); row.put("glowing", table.glowing); rows.add(row); }
        plugin.getDataStore().set("physicalGames.blackjack.tables", rows);
    }

    private static final class View {
        final Table table; final TextDisplay title, prompt, feltMark; final List<Entity> structure, dynamic = new ArrayList<>(); Interaction opener;
        View(Table table, TextDisplay title, TextDisplay prompt, TextDisplay feltMark, List<Entity> structure) { this.table = table; this.title = title; this.prompt = prompt; this.feltMark = feltMark; this.structure = structure; }
        void remove() { structure.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); dynamic.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); }
    }
    private record Table(String id, Location location, Style style, PlayMode mode, String name, boolean glowing) { Table { id = id.toLowerCase(Locale.ROOT); } TableSummary summary() { return new TableSummary(id, name, style.id, mode.id, glowing, location.getYaw(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); } }
    public record TableSummary(String id, String name, String style, String playMode, boolean glowing, float yaw, String world, int x, int y, int z) { public String locationText() { return world + " · " + x + ", " + y + ", " + z; } }
    private enum PlayMode { GUI("gui"), WORLD("world"); final String id; PlayMode(String id) { this.id = id; } static PlayMode parse(String value) { for (PlayMode mode : values()) if (mode.id.equalsIgnoreCase(value) || mode == WORLD && "physical".equalsIgnoreCase(value)) return mode; return null; } }
    private enum Style {
        CASINO("casino", Material.DARK_OAK_PLANKS, Material.POLISHED_BLACKSTONE, Material.GREEN_CONCRETE, Material.DARK_OAK_LOG, Material.GOLD_BLOCK, Material.GOLD_NUGGET, Material.EMERALD, NamedTextColor.GOLD, Color.YELLOW),
        SALOON("saloon", Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.GREEN_WOOL, Material.SPRUCE_LOG, Material.COPPER_BLOCK, Material.COPPER_INGOT, Material.GOLD_NUGGET, NamedTextColor.YELLOW, Color.ORANGE),
        ROYAL("royal", Material.PURPLE_CONCRETE, Material.GILDED_BLACKSTONE, Material.BLUE_CONCRETE, Material.POLISHED_BLACKSTONE, Material.AMETHYST_BLOCK, Material.AMETHYST_SHARD, Material.GOLD_NUGGET, NamedTextColor.LIGHT_PURPLE, Color.PURPLE),
        MODERN("modern", Material.SMOOTH_QUARTZ, Material.IRON_BLOCK, Material.CYAN_CONCRETE, Material.QUARTZ_PILLAR, Material.SEA_LANTERN, Material.DIAMOND, Material.IRON_NUGGET, NamedTextColor.AQUA, Color.AQUA);
        final String id; final Material base, rail, felt, leg, trim, chipA, chipB; final NamedTextColor accent; final Color glow;
        Style(String id, Material base, Material rail, Material felt, Material leg, Material trim, Material chipA, Material chipB, NamedTextColor accent, Color glow) { this.id = id; this.base = base; this.rail = rail; this.felt = felt; this.leg = leg; this.trim = trim; this.chipA = chipA; this.chipB = chipB; this.accent = accent; this.glow = glow; }
        static Style parse(String value) { for (Style style : values()) if (style.id.equalsIgnoreCase(value)) return style; return null; }
    }
}
