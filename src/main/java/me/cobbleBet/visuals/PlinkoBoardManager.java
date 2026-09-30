package me.cobbleBet.visuals;

import me.cobbleBet.Main;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.*;

public final class PlinkoBoardManager implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private final Main plugin;
    private final NamespacedKey key;
    private final Map<String, View> boards = new LinkedHashMap<>();
    private final Map<String, UUID> activePlayers = new HashMap<>();

    public PlinkoBoardManager(Main plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "plinko_board"); }

    public void load() {
        clearAll();
        for (Map<?, ?> row : plugin.getDataStore().getMapList("physicalGames.plinko.boards")) try {
            World world = Bukkit.getWorld(String.valueOf(row.get("world"))); Style style = Style.parse(String.valueOf(row.get("style")));
            if (world == null || style == null) continue;
            float yaw = Math.round((float) number(row.get("yaw")) / 22.5f) * 22.5f;
            spawn(new Board(String.valueOf(row.get("id")), new Location(world, number(row.get("x")), number(row.get("y")), number(row.get("z")), yaw, 0), style, cleanName(String.valueOf(row.get("name"))), Boolean.parseBoolean(String.valueOf(row.get("glowing")))));
        } catch (RuntimeException error) { plugin.getLogger().warning("Skipped an invalid Plinko board: " + error.getMessage()); }
    }

    public void create(Player player, String styleName, String name) {
        Style style = Style.parse(styleName); if (style == null) { player.sendMessage("§cChoose a valid Plinko style."); return; }
        Board board = new Board(nextId(), placement(player), style, cleanName(name), false); spawn(board); save();
        player.sendMessage("§aCreated world Plinko board §f" + board.id + "§a.");
    }
    public boolean moveHere(Player player, String id) { View view = available(player, id); if (view == null) return false; replace(view, new Board(view.board.id, placement(player), view.board.style, view.board.name, view.board.glowing)); return true; }
    public boolean changeStyle(Player player, String id, String value) { View view = available(player, id); Style style = Style.parse(value); if (view == null) return false; if (style == null) { player.sendMessage("§cUnknown Plinko style."); return false; } replace(view, new Board(view.board.id, view.board.location.clone(), style, view.board.name, view.board.glowing)); return true; }
    public boolean toggleGlow(Player player, String id) { View view = available(player, id); if (view == null) return false; replace(view, new Board(view.board.id, view.board.location.clone(), view.board.style, view.board.name, !view.board.glowing)); return true; }
    public boolean rotate(Player player, String id, int quarterTurns) { View view = available(player, id); if (view == null) return false; Location location = view.board.location.clone(); location.setYaw(Math.round((location.getYaw() + quarterTurns * 90f) / 90f) * 90f); replace(view, new Board(view.board.id, location, view.board.style, view.board.name, view.board.glowing)); return true; }
    public boolean rotateByDegrees(Player player, String id, float degrees) { View view = available(player, id); if (view == null) return false; Location location = view.board.location.clone(); location.setYaw(Math.round((location.getYaw() + degrees) / 22.5f) * 22.5f); replace(view, new Board(view.board.id, location, view.board.style, view.board.name, view.board.glowing)); return true; }
    public boolean rename(Player player, String id, String name) { View view = available(player, id); if (view == null) return false; replace(view, new Board(view.board.id, view.board.location.clone(), view.board.style, cleanName(name), view.board.glowing)); return true; }
    public boolean remove(Player player, String id) { View view = available(player, id); if (view == null) return false; view.remove(); boards.remove(view.board.id); activePlayers.remove(view.board.id); save(); return true; }
    public List<BoardSummary> summaries() { return boards.values().stream().map(view -> view.board.summary()).toList(); }
    public BoardSummary summary(String id) { View view = find(id); return view == null ? null : view.board.summary(); }
    public Set<String> styles() { return new LinkedHashSet<>(Arrays.stream(Style.values()).map(style -> style.id).toList()); }

    private void spawn(Board board) {
        World world = board.location.getWorld(); if (world == null) return; float face = board.location.getYaw() + 180; List<Entity> entities = new ArrayList<>();
        entities.add(block(board, point(board.location, 0, 2.75, 0), face, board.style.back, 4.7f, 5.45f, .18f));
        entities.add(block(board, point(board.location, 0, 5.48, .02), face, board.style.frame, 4.95f, .18f, .34f));
        entities.add(block(board, point(board.location, 0, .02, .02), face, board.style.frame, 4.95f, .18f, .34f));
        for (double side : new double[]{-2.38, 2.38}) entities.add(block(board, point(board.location, side, 2.75, .02), face, board.style.frame, .18f, 5.55f, .34f));
        for (double side : new double[]{-2.13, 2.13}) entities.add(block(board, point(board.location, side, 2.80, .18), face, board.style.peg, .055f, 4.82f, .12f));
        entities.add(block(board, point(board.location, 0, .48, .19), face, board.style.frame, 4.30f, .88f, .22f));
        entities.add(tiltedBlock(board, point(board.location, -.42, 5.02, .27), face, board.style.peg, .075f, 1.08f, .10f, -.62f));
        entities.add(tiltedBlock(board, point(board.location, .42, 5.02, .27), face, board.style.peg, .075f, 1.08f, .10f, .62f));
        for (double x : new double[]{-2.16, 2.16}) for (double y : new double[]{.26, 5.24}) entities.add(block(board, point(board.location, x, y, .29), face, board.style.peg, .12f, .12f, .10f));
        for (double side : new double[]{-1.72, 1.72}) { entities.add(block(board, point(board.location, side, -.38, -.02), face, board.style.leg, .25f, .78f, .25f)); entities.add(block(board, point(board.location, side, -.76, .15), face, board.style.frame, .9f, .12f, .8f)); }
        TextDisplay title = text(board, point(board.location, 0, 5.18, .25), face, .62f, Color.fromARGB(190, 5, 7, 10)); title.text(Component.text(board.name, board.style.accent).decorate(TextDecoration.BOLD)); entities.add(title);
        entities.add(block(board, point(board.location, 0, -.17, .16), face, board.style.frame, 3.10f, .40f, .25f));
        TextDisplay status = text(board, point(board.location, 0, -.17, .34), face, .37f, Color.fromARGB(0, 0, 0, 0)); entities.add(status);
        Location interactionAt = point(board.location, 0, 2.75, .32);
        Interaction interaction = world.spawn(interactionAt, Interaction.class, entity -> { entity.setInteractionWidth(4.55f); entity.setInteractionHeight(5.35f); entity.setResponsive(true); entity.setPersistent(false); entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, "open:" + board.id); }); entities.add(interaction);
        View view = new View(board, status, entities); boards.put(board.id, view); reset(view);
    }

    public void prepare(String id, UUID player, int rows, String risk) {
        View view = find(id); if (view == null || !player.equals(activePlayers.get(id))) return;
        clearBalls(view);
        renderLayout(view, rows, List.of());
        view.status.text(Component.text(rows + " ROWS  •  " + risk.toUpperCase(Locale.ROOT) + " RISK", view.board.style.accent));
    }

    public void animate(String id, UUID player, List<BallDrop> drops, double totalPayout, String outcome, List<Double> multipliers) {
        View view = find(id); if (view == null || !player.equals(activePlayers.get(id))) return;
        clearBalls(view);
        if (drops == null || drops.isEmpty()) { cancel(id, player); return; }
        int rows = Math.max(1, drops.getFirst().path.size());
        renderLayout(view, rows, multipliers);
        int generation = ++view.animationGeneration;
        view.status.text(Component.text(drops.size() + (drops.size() == 1 ? " BALL IN PLAY" : " BALLS IN PLAY"), view.board.style.accent).decorate(TextDecoration.BOLD));
        java.util.concurrent.atomic.AtomicInteger landed = new java.util.concurrent.atomic.AtomicInteger();
        for (int index = 0; index < drops.size(); index++) {
            int ballIndex = index;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (view.animationGeneration == generation && player.equals(activePlayers.get(id))) animateBall(view, id, player, drops.get(ballIndex), ballIndex, drops.size(), landed, generation, totalPayout, outcome);
            }, index * 4L);
        }
    }

    private void animateBall(View view, String id, UUID player, BallDrop drop, int index, int totalBalls, java.util.concurrent.atomic.AtomicInteger landed, int generation, double totalPayout, String overallOutcome) {
        if (!player.equals(activePlayers.get(id))) return;
        List<Integer> path = drop.path; int rows = Math.max(1, path.size()); double gap = 3.7 / rows;
        ItemDisplay ball = item(view.board, point(view.board.location, 0, 4.91, .42 + index * .002), view.board.location.getYaw() + 180, view.board.style.ball, .24f); view.balls.add(ball);
        new BukkitRunnable() {
            int frame; double previousX;
            @Override public void run() {
                if (view.animationGeneration != generation || !player.equals(activePlayers.get(id)) || ball.isDead()) { cancel(); return; }
                int framesPerRow = 5, row = Math.min(rows - 1, frame / framesPerRow); double progress = (frame % framesPerRow) / (double) framesPerRow;
                if (frame >= rows * framesPerRow) {
                    double finalX = (drop.slot - rows / 2.0) * gap;
                    ball.teleport(point(view.board.location, finalX, .69 + (index % 3) * .035, .43 + index * .002));
                    Player online = Bukkit.getPlayer(player); if (online != null) online.playSound(online.getLocation(), drop.multiplier >= 1 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT, .35f, drop.multiplier >= 1 ? 1.35f : .78f);
                    int count = landed.incrementAndGet();
                    view.status.text(Component.text(count + " / " + totalBalls + " LANDED", view.board.style.accent).decorate(TextDecoration.BOLD));
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!ball.isDead()) ball.remove(); view.balls.remove(ball); }, 16L);
                    if (count == totalBalls) {
                        String prefix = overallOutcome.equals("win") ? "TOTAL WIN" : overallOutcome.equals("push") ? "TOTAL RETURN" : "TOTAL PAYOUT";
                        NamedTextColor color = overallOutcome.equals("win") ? NamedTextColor.GREEN : overallOutcome.equals("push") ? NamedTextColor.YELLOW : NamedTextColor.RED;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!player.equals(activePlayers.get(id))) return;
                            view.status.text(Component.text(prefix + "  •  " + MONEY.format(totalPayout), color).decorate(TextDecoration.BOLD));
                            Player current = Bukkit.getPlayer(player); if (current != null && overallOutcome.equals("win")) current.playSound(current.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .8f, 1.2f);
                        }, 10L);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (view.animationGeneration == generation && player.equals(activePlayers.get(id))) { activePlayers.remove(id); reset(view); } }, 110L);
                    }
                    cancel(); return;
                }
                double targetX = previousX + (path.get(row) == 0 ? -gap / 2 : gap / 2);
                double x = previousX + (targetX - previousX) * progress;
                double y = 4.91 - (row + progress) * (4.04 / rows) + Math.sin(progress * Math.PI) * .075;
                ball.teleport(point(view.board.location, x, y, .42));
                if (frame % framesPerRow == framesPerRow - 1) { previousX = targetX; Player online = Bukkit.getPlayer(player); if (online != null && index % 3 == 0) online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .10f, 1.45f); }
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cancel(String id, UUID player) { if (player.equals(activePlayers.get(id))) { activePlayers.remove(id); View view = find(id); if (view != null) reset(view); } }
    public void clearAll() { boards.values().forEach(View::remove); boards.clear(); activePlayers.clear(); }
    private void reset(View view) { view.animationGeneration++; clearBalls(view); renderLayout(view, 12, List.of()); view.status.text(Component.text("RIGHT CLICK TO DROP", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)); }

    private void clearBalls(View view) { for (ItemDisplay ball : new ArrayList<>(view.balls)) if (ball != null && !ball.isDead()) ball.remove(); view.balls.clear(); }

    private void renderLayout(View view, int requestedRows, List<Double> multipliers) {
        view.layout.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); view.layout.clear();
        int rows = Math.max(8, Math.min(16, requestedRows)); double gap = 3.7 / rows;
        double top = 4.78, bottom = 1.02, rowGap = (top - bottom) / Math.max(1, rows - 1); float face = view.board.location.getYaw() + 180;
        for (int row = 0; row < rows; row++) {
            double y = top - row * rowGap;
            for (int column = 0; column <= row; column++) {
                double x = (column - row / 2.0) * gap;
                view.layout.add(block(view.board, point(view.board.location, x, y, .30), face, view.board.style.peg, .105f, .105f, .16f));
            }
        }
        for (int boundary = 0; boundary <= rows + 1; boundary++) {
            double x = (boundary - (rows + 1) / 2.0) * gap;
            view.layout.add(block(view.board, point(view.board.location, x, .61, .34), face, view.board.style.peg, .045f, .70f, .18f));
        }
        boolean hasMultipliers = multipliers != null && multipliers.size() == rows + 1;
        for (int slot = 0; slot <= rows; slot++) {
            double value = hasMultipliers ? multipliers.get(slot) : 0, x = (slot - rows / 2.0) * gap;
            Material bin = hasMultipliers ? value >= 2 ? Material.LIME_CONCRETE : value >= 1 ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE : view.board.style.back;
            view.layout.add(block(view.board, point(view.board.location, x, .40, .30), face, bin, (float) (gap * .84), .28f, .17f));
            if (hasMultipliers) {
                NamedTextColor color = value >= 2 ? NamedTextColor.GREEN : value >= 1 ? NamedTextColor.YELLOW : NamedTextColor.RED;
                TextDisplay label = text(view.board, point(view.board.location, x, .39, .43), face, rows >= 14 ? .125f : .15f, Color.fromARGB(185, 5, 7, 10));
                label.text(Component.text(compactMultiplier(value), color).decoration(TextDecoration.BOLD, false)); label.setLineWidth(80); view.layout.add(label);
            }
        }
    }

    private String compactMultiplier(double value) {
        if (value >= 100) return Math.round(value) + "x";
        String formatted = String.format(Locale.US, value >= 10 ? "%.1f" : "%.2f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted + "x";
    }

    @EventHandler public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; String value = event.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING); if (value == null) return;
        event.setCancelled(true); String[] parts = value.split(":"); if (parts.length != 2) return; View view = find(parts[1]); if (view == null) return;
        UUID player = event.getPlayer().getUniqueId(), active = activePlayers.get(view.board.id);
        if (active == null) { if (activePlayers.containsValue(player)) { event.getPlayer().sendMessage("§cYou are already using another Plinko board."); return; } activePlayers.put(view.board.id, player); plugin.plinkoController.openPhysical(event.getPlayer(), view.board.id); }
        else if (!active.equals(player)) event.getPlayer().sendMessage("§cThis Plinko board is already in use.");
    }
    @EventHandler public void quit(PlayerQuitEvent event) { UUID player = event.getPlayer().getUniqueId(); for (String id : new ArrayList<>(activePlayers.keySet())) if (player.equals(activePlayers.get(id))) cancel(id, player); }

    private View available(Player player, String id) { View view = find(id); if (view == null) { player.sendMessage("§cNo Plinko board exists with ID §f" + id + "§c."); return null; } if (activePlayers.containsKey(view.board.id)) { player.sendMessage("§cWait for the current Plinko ball to finish first."); return null; } return view; }
    private void replace(View old, Board board) { old.remove(); boards.remove(old.board.id); spawn(board); save(); }
    private View find(String id) { return id == null ? null : boards.get(id.toLowerCase(Locale.ROOT)); }
    private Location placement(Player player) { Location location = player.getLocation().clone(); location.setPitch(0); location.setYaw(Math.round(location.getYaw() / 22.5f) * 22.5f); location.add(direction(location.getYaw()).multiply(3)); location.setY(Math.floor(location.getY()) + .78); return location; }
    private org.bukkit.util.Vector direction(float yaw) { double radians = Math.toRadians(yaw); return new org.bukkit.util.Vector(-Math.sin(radians), 0, Math.cos(radians)); }
    private Location point(Location origin, double right, double up, double forward) { double radians = Math.toRadians(origin.getYaw()); return origin.clone().add(-Math.cos(radians) * right + Math.sin(radians) * forward, up, -Math.sin(radians) * right - Math.cos(radians) * forward); }
    private BlockDisplay block(Board board, Location at, float yaw, Material material, float width, float height, float depth) { return at.getWorld().spawn(at, BlockDisplay.class, entity -> { entity.setBlock(material.createBlockData()); display(entity, board); entity.setRotation(yaw, 0); entity.setTransformation(new Transformation(new Vector3f(-width / 2, -height / 2, -depth / 2), new Quaternionf(), new Vector3f(width, height, depth), new Quaternionf())); }); }
    private BlockDisplay tiltedBlock(Board board, Location at, float yaw, Material material, float width, float height, float depth, float roll) { return at.getWorld().spawn(at, BlockDisplay.class, entity -> { entity.setBlock(material.createBlockData()); display(entity, board); entity.setRotation(yaw, 0); float centerX=(float)(Math.cos(roll)*width/2-Math.sin(roll)*height/2),centerY=(float)(Math.sin(roll)*width/2+Math.cos(roll)*height/2); entity.setTransformation(new Transformation(new Vector3f(-centerX, -centerY, -depth / 2), new Quaternionf().rotateZ(roll), new Vector3f(width, height, depth), new Quaternionf())); }); }
    private TextDisplay text(Board board, Location at, float yaw, float scale, Color background) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, board); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 0); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setShadowed(true); entity.setSeeThrough(false); entity.setBackgroundColor(background); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private ItemDisplay item(Board board, Location at, float yaw, Material material, float scale) { return at.getWorld().spawn(at, ItemDisplay.class, entity -> { entity.setItemStack(new ItemStack(material)); display(entity, board); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 0); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private void display(Display display, Board board) { display.setPersistent(false); display.setViewRange(1.8f); display.setGlowing(board.glowing); if (board.glowing) display.setGlowColorOverride(board.style.glow); }
    private String nextId() { String id; do { id = UUID.randomUUID().toString().substring(0, 6).toLowerCase(Locale.ROOT); } while (boards.containsKey(id)); return id; }
    private double number(Object value) { return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value)); }
    private static String cleanName(String value) { return value == null || value.isBlank() || value.equals("null") ? "PLINKO" : value.substring(0, Math.min(28, value.length())); }
    private void save() { List<Map<String, Object>> rows = new ArrayList<>(); for (View view : boards.values()) { Board board = view.board; Map<String, Object> row = new LinkedHashMap<>(); row.put("id", board.id); row.put("world", board.location.getWorld().getName()); row.put("x", board.location.getX()); row.put("y", board.location.getY()); row.put("z", board.location.getZ()); row.put("yaw", board.location.getYaw()); row.put("style", board.style.id); row.put("name", board.name); row.put("glowing", board.glowing); rows.add(row); } plugin.getDataStore().set("physicalGames.plinko.boards", rows); }

    private static final class View { final Board board; final TextDisplay status; final List<Entity> entities; final List<Entity> layout = new ArrayList<>(); final List<ItemDisplay> balls = new ArrayList<>(); int animationGeneration; View(Board board, TextDisplay status, List<Entity> entities) { this.board = board; this.status = status; this.entities = entities; } void remove() { animationGeneration++; balls.forEach(ball -> { if (ball != null && !ball.isDead()) ball.remove(); }); layout.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); entities.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); } }
    private record Board(String id, Location location, Style style, String name, boolean glowing) { Board { id = id.toLowerCase(Locale.ROOT); } BoardSummary summary() { return new BoardSummary(id, name, style.id, glowing, location.getYaw(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); } }
    public record BallDrop(List<Integer> path, int slot, double multiplier, double payout) { public BallDrop { path = List.copyOf(path); } }
    public record BoardSummary(String id, String name, String style, boolean glowing, float yaw, String world, int x, int y, int z) { public String locationText() { return world + " · " + x + ", " + y + ", " + z; } }
    private enum Style {
        CLASSIC("classic", Material.DARK_OAK_PLANKS, Material.POLISHED_BLACKSTONE, Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.SLIME_BALL, NamedTextColor.GOLD, Color.YELLOW),
        ARCADE("arcade", Material.BLUE_CONCRETE, Material.CYAN_CONCRETE, Material.IRON_BLOCK, Material.SEA_LANTERN, Material.MAGMA_CREAM, NamedTextColor.AQUA, Color.AQUA),
        ROYAL("royal", Material.PURPLE_CONCRETE, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.AMETHYST_BLOCK, Material.AMETHYST_SHARD, NamedTextColor.LIGHT_PURPLE, Color.PURPLE),
        INDUSTRIAL("industrial", Material.DEEPSLATE_TILES, Material.IRON_BLOCK, Material.POLISHED_BASALT, Material.LIGHT_GRAY_CONCRETE, Material.FIRE_CHARGE, NamedTextColor.YELLOW, Color.ORANGE);
        final String id; final Material back, frame, leg, peg, ball; final NamedTextColor accent; final Color glow;
        Style(String id, Material back, Material frame, Material leg, Material peg, Material ball, NamedTextColor accent, Color glow) { this.id = id; this.back = back; this.frame = frame; this.leg = leg; this.peg = peg; this.ball = ball; this.accent = accent; this.glow = glow; }
        static Style parse(String value) { for (Style style : values()) if (style.id.equalsIgnoreCase(value)) return style; return null; }
    }
}
