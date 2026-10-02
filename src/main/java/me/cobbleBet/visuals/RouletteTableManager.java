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

import java.util.*;

public final class RouletteTableManager implements Listener {
    private final Main plugin;
    private final NamespacedKey key;
    private final Map<String, View> tables = new LinkedHashMap<>();
    private final Map<String, UUID> activePlayers = new HashMap<>();

    public RouletteTableManager(Main plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "roulette_table_action"); }

    public void load() {
        clearAll();
        List<?> rows = plugin.getDataStore().getMapList("physicalGames.roulette.tables");
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row)) continue;
            try {
                World world = Bukkit.getWorld(String.valueOf(row.get("world")));
                Style style = Style.parse(String.valueOf(row.get("style")));
                if (world == null || style == null) continue;
                Location location = new Location(world, number(row.get("x")), number(row.get("y")), number(row.get("z")), (float) number(row.get("yaw")), 0);
                String id = String.valueOf(row.get("id")), name = cleanName(String.valueOf(row.get("name")));
                boolean glowing = Boolean.parseBoolean(String.valueOf(row.get("glowing")));
                spawn(new Table(id, location, style, name, glowing));
            } catch (RuntimeException error) { plugin.getLogger().warning("Skipped invalid Roulette table: " + error.getMessage()); }
        }
    }

    public boolean create(Player player, String styleName, String name) {
        Style style = Style.parse(styleName);
        if (style == null) { player.sendMessage("§cUnknown Roulette style. Use: " + String.join(", ", styles())); return false; }
        Table table = new Table(nextId(), placement(player), style, cleanName(name), false);
        spawn(table); save(); player.sendMessage("§aCreated Roulette table §f" + table.name + " §7[" + table.id + "]§a."); return true;
    }

    public boolean moveHere(Player player, String id) { View view = available(player, id); if (view == null) return false; replace(view, new Table(view.table.id, placement(player), view.table.style, view.table.name, view.table.glowing)); return true; }
    public boolean moveThere(Player player, String id) { View view = available(player, id); if (view == null) return false; Location location=targetPlacement(player); if(location==null){player.sendMessage("§cLook at a block within 30 blocks.");return false;} replace(view,new Table(view.table.id,location,view.table.style,view.table.name,view.table.glowing)); return true; }
    public boolean rotateByDegrees(Player player, String id, float degrees) { View view = available(player, id); if (view == null) return false; Location location = view.table.location.clone(); location.setYaw(Math.round((location.getYaw() + degrees) / 22.5f) * 22.5f); replace(view, new Table(view.table.id, location, view.table.style, view.table.name, view.table.glowing)); return true; }
    public boolean changeStyle(Player player, String id, String styleName) { View view = available(player, id); Style style = Style.parse(styleName); if (view == null) return false; if (style == null) { player.sendMessage("§cUnknown Roulette style."); return false; } replace(view, new Table(view.table.id, view.table.location, style, view.table.name, view.table.glowing)); return true; }
    public boolean toggleGlow(Player player, String id) { View view = available(player, id); if (view == null) return false; replace(view, new Table(view.table.id, view.table.location, view.table.style, view.table.name, !view.table.glowing)); return true; }
    public boolean rename(Player player, String id, String name) { View view = available(player, id); if (view == null) return false; replace(view, new Table(view.table.id, view.table.location, view.table.style, cleanName(name), view.table.glowing)); return true; }
    public boolean remove(Player player, String id) { View view = available(player, id); if (view == null) return false; view.remove(); tables.remove(view.table.id); activePlayers.remove(view.table.id); save(); return true; }

    public Set<String> styles() { return new LinkedHashSet<>(Arrays.stream(Style.values()).map(style -> style.id).toList()); }
    public List<TableSummary> summaries() { return tables.values().stream().map(view -> view.table.summary()).toList(); }
    public TableSummary summary(String id) { View view = find(id); return view == null ? null : view.table.summary(); }
    public void clearAll() { tables.values().forEach(View::remove); tables.clear(); activePlayers.clear(); }

    public void animate(String id, UUID player, int result, String color, boolean won, double payout, String selection) {
        View view = find(id);
        if (view == null || !player.equals(activePlayers.get(id))) return;
        clearDynamic(view);
        int generation = ++view.animationGeneration;
        ItemDisplay ball = item(view.table, wheelPoint(view.table, 0, 1.02), Material.SNOWBALL, .18f);
        view.dynamic.add(ball);
        view.status.text(Component.text("NO MORE BETS\nTHE WHEEL IS SPINNING", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        new BukkitRunnable() {
            int step;
            @Override public void run() {
                if (view.animationGeneration != generation || ball.isDead()) { cancel(); return; }
                double progress = step / 44.0;
                double eased = 1 - Math.pow(1 - Math.min(1, progress), 2.4);
                double finalAngle = (result / 37.0) * Math.PI * 2;
                double angle = Math.PI * 10 * eased + finalAngle * eased;
                ball.teleport(wheelPoint(view.table, angle, 1.02));
                if (step % 3 == 0) view.table.location.getWorld().playSound(view.table.location, Sound.BLOCK_NOTE_BLOCK_HAT, .25f, .8f + (float) progress);
                if (step++ < 44) return;
                cancel();
                String resultText = result + "  " + color.toUpperCase(Locale.ROOT);
                view.status.text(Component.text(resultText, rouletteColor(color)).decorate(TextDecoration.BOLD));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (view.animationGeneration != generation) return;
                    String payoutText = won ? "YOU WIN  " + format(payout) : "YOU LOST";
                    view.status.text(Component.text(resultText + "\n" + payoutText, won ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD));
                    view.table.location.getWorld().playSound(view.table.location, won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, .8f, won ? 1.2f : .72f);
                }, 10L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (view.animationGeneration == generation) { activePlayers.remove(id); reset(view); }
                }, 130L);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void cancel(String id, UUID player) { if (player.equals(activePlayers.get(id))) { activePlayers.remove(id); View view = find(id); if (view != null) reset(view); } }

    private void spawn(Table table) {
        if (table.location.getWorld() == null) return;
        float face = table.location.getYaw() + 180;
        List<Entity> structure = new ArrayList<>();
        structure.add(block(table, point(table.location, 0, .68, 0), face, table.style.base, 4.30f, .22f, 3.20f));
        structure.add(block(table, point(table.location, 0, .825, 0), face, table.style.felt, 4.02f, .07f, 2.92f));
        structure.add(block(table, point(table.location, 0, .89, -1.53), face, table.style.rail, 4.34f, .15f, .18f));
        structure.add(block(table, point(table.location, 0, .89, 1.53), face, table.style.rail, 4.34f, .15f, .18f));
        structure.add(block(table, point(table.location, -2.08, .89, 0), face, table.style.rail, .18f, .15f, 2.90f));
        structure.add(block(table, point(table.location, 2.08, .89, 0), face, table.style.rail, .18f, .15f, 2.90f));
        for (double right : new double[]{-1.78, 1.78}) for (double forward : new double[]{-1.22, 1.22})
            structure.add(block(table, point(table.location, right, .31, forward), face, table.style.leg, .24f, .72f, .24f));

        // Recessed wheel on the left. Short square pockets keep the ring clean
        // from every angle instead of overlapping into long spokes.
        structure.add(block(table, point(table.location, -1.05, .89, -.25), face,
                Material.POLISHED_BLACKSTONE, 1.82f, .045f, 1.82f));
        for (int segment = 0; segment < 18; segment++) {
            double angle = segment * Math.PI * 2 / 18;
            double right = -1.05 + Math.sin(angle) * .70;
            double forward = -.25 + Math.cos(angle) * .70;
            Material material = segment == 0 ? Material.LIME_CONCRETE
                    : segment % 2 == 0 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            structure.add(block(table, point(table.location, right, .925, forward),
                    face + (float) Math.toDegrees(angle), material, .25f, .045f, .25f));
        }
        structure.add(block(table, point(table.location, -1.05, .95, -.25), face,
                table.style.trim, .34f, .11f, .34f));

        // Calm betting area on the right, separated from the wheel.
        structure.add(block(table, point(table.location, .98, .89, .08), face,
                Material.BLACK_CONCRETE, 1.72f, .035f, 2.20f));
        structure.add(block(table, point(table.location, .98, .925, -.72), face,
                Material.RED_CONCRETE, 1.45f, .025f, .34f));
        structure.add(block(table, point(table.location, .98, .925, -.30), face,
                Material.BLACK_CONCRETE, 1.45f, .025f, .34f));
        structure.add(block(table, point(table.location, .98, .925, .12), face,
                Material.LIME_CONCRETE, 1.45f, .025f, .34f));
        for (double right : new double[]{.40, .98, 1.56})
            structure.add(block(table, point(table.location, right, .95, .72), face,
                    table.style.trim, .035f, .025f, .60f));
        structure.add(flatLabel(table, point(table.location, .42, .965, .84), face, .16f, "1–12", NamedTextColor.WHITE));
        structure.add(flatLabel(table, point(table.location, .98, .965, .84), face, .16f, "13–24", NamedTextColor.WHITE));
        structure.add(flatLabel(table, point(table.location, 1.54, .965, .84), face, .16f, "25–36", NamedTextColor.WHITE));
        structure.add(flatLabel(table, point(table.location, .42, .965, .46), face, .17f, "RED", NamedTextColor.RED));
        structure.add(flatLabel(table, point(table.location, .98, .965, .46), face, .17f, "BLACK", NamedTextColor.WHITE));
        structure.add(flatLabel(table, point(table.location, 1.54, .965, .46), face, .17f, "ZERO", NamedTextColor.GREEN));

        TextDisplay title = text(table, point(table.location, 0, .66, 1.70), face, .54f);
        title.text(Component.text(table.name, table.style.accent).decorate(TextDecoration.BOLD)); structure.add(title);
        TextDisplay status = text(table, point(table.location, 0, .38, 1.72), face, .38f); structure.add(status);
        View view = new View(table, title, status, structure); tables.put(table.id, view); reset(view);
    }

    private void reset(View view) {
        clearDynamic(view); view.animationGeneration++;
        view.status.text(Component.text("RIGHT CLICK TO PLAY", NamedTextColor.WHITE));
        view.opener = interaction(point(view.table.location, 0, .25, 0), 4.20f, 1.35f, "open:" + view.table.id);
        view.dynamic.add(view.opener);
        view.dynamic.add(item(view.table, point(view.table.location, 1.58, .99, 1.02), view.table.style.chipA, .18f));
        view.dynamic.add(item(view.table, point(view.table.location, 1.42, 1.00, .88), view.table.style.chipB, .16f));
        view.dynamic.add(item(view.table, point(view.table.location, .48, .99, 1.04), view.table.style.chipB, .18f));
    }

    @EventHandler public void interact(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String value = event.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null || !value.startsWith("open:")) return;
        event.setCancelled(true);
        String id = value.substring(5); View view = find(id); if (view == null) return;
        Player player = event.getPlayer(); UUID active = activePlayers.get(id);
        if (active == null) {
            if (activePlayers.containsValue(player.getUniqueId())) { player.sendMessage("§cYou are already using another Roulette table."); return; }
            activePlayers.put(id, player.getUniqueId()); plugin.rouletteController.openPhysical(player, id);
        } else if (!active.equals(player.getUniqueId())) player.sendMessage("§cThis Roulette table is already in use.");
    }

    @EventHandler public void quit(PlayerQuitEvent event) { UUID player = event.getPlayer().getUniqueId(); for (String id : new ArrayList<>(activePlayers.keySet())) if (player.equals(activePlayers.get(id))) cancel(id, player); }

    private Location wheelPoint(Table table, double angle, double up) { return point(table.location, -1.05 + Math.sin(angle) * .70, up, -.25 + Math.cos(angle) * .70); }
    private Interaction interaction(Location at, float width, float height, String value) { return at.getWorld().spawn(at, Interaction.class, entity -> { entity.setInteractionWidth(width); entity.setInteractionHeight(height); entity.setResponsive(true); entity.setPersistent(false); entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value); }); }
    private BlockDisplay block(Table table, Location at, float yaw, Material material, float width, float height, float depth) { return at.getWorld().spawn(at, BlockDisplay.class, entity -> { entity.setBlock(material.createBlockData()); display(entity, table); entity.setRotation(yaw, 0); entity.setTransformation(new Transformation(new Vector3f(-width / 2, -height / 2, -depth / 2), new Quaternionf(), new Vector3f(width, height, depth), new Quaternionf())); }); }
    private ItemDisplay item(Table table, Location at, Material material, float scale) { return at.getWorld().spawn(at, ItemDisplay.class, entity -> { entity.setItemStack(new ItemStack(material)); display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(table.location.getYaw() + 180, 0); entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private TextDisplay text(Table table, Location at, float yaw, float scale) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, 0); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setLineWidth(420); entity.setShadowed(true); entity.setSeeThrough(false); entity.setBackgroundColor(Color.fromARGB(155, 8, 10, 9)); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private TextDisplay flatText(Table table, Location at, float yaw, float scale) { return at.getWorld().spawn(at, TextDisplay.class, entity -> { display(entity, table); entity.setBillboard(Display.Billboard.FIXED); entity.setRotation(yaw, -90); entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setLineWidth(520); entity.setShadowed(true); entity.setSeeThrough(true); entity.setDefaultBackground(false); entity.setBackgroundColor(Color.fromARGB(0,0,0,0)); entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf())); }); }
    private TextDisplay flatLabel(Table table, Location at, float yaw, float scale, String label, NamedTextColor color) { TextDisplay display = flatText(table, at, yaw, scale); display.text(Component.text(label, color).decorate(TextDecoration.BOLD)); return display; }
    private NamedTextColor rouletteColor(String color) { return "red".equalsIgnoreCase(color) ? NamedTextColor.RED : "green".equalsIgnoreCase(color) ? NamedTextColor.GREEN : NamedTextColor.WHITE; }
    private void display(Display display, Table table) { display.setPersistent(false); display.setViewRange(1.5f); display.setGlowing(table.glowing); if (table.glowing) display.setGlowColorOverride(table.style.glow); }

    private View available(Player player, String id) { View view = find(id); if (view == null) { player.sendMessage("§cNo Roulette table exists with ID §f" + id + "§c."); return null; } if (activePlayers.containsKey(view.table.id)) { player.sendMessage("§cWait for the active Roulette spin to finish first."); return null; } return view; }
    private void replace(View old, Table table) { old.remove(); tables.remove(old.table.id); spawn(table); save(); }
    private View find(String id) { return id == null ? null : tables.get(id.toLowerCase(Locale.ROOT)); }
    private void clearDynamic(View view) { view.animationGeneration++; view.dynamic.forEach(this::removeEntity); view.dynamic.clear(); }
    private void removeEntity(Entity entity) { if (entity != null && !entity.isDead()) entity.remove(); }
    private Location placement(Player player) { Location location = player.getLocation().clone(); location.setPitch(0); location.setYaw(Math.round(location.getYaw() / 22.5f) * 22.5f); location.setY(Math.floor(location.getY()) + .03); return location; }
    private Location targetPlacement(Player player) { var target=player.getTargetBlockExact(30,org.bukkit.FluidCollisionMode.NEVER); if(target==null)return null; Location location=target.getLocation().add(.5,1.03,.5); location.setYaw(Math.round(facingYaw(location,player.getLocation())/22.5f)*22.5f); return location; }
    private float facingYaw(Location from,Location to) { return(float)Math.toDegrees(Math.atan2(-(to.getX()-from.getX()),to.getZ()-from.getZ())); }
    private Location point(Location origin, double right, double up, double forward) { double radians = Math.toRadians(origin.getYaw()); return origin.clone().add(-Math.cos(radians) * right + Math.sin(radians) * forward, up, -Math.sin(radians) * right - Math.cos(radians) * forward); }
    private org.bukkit.util.Vector direction(float yaw) { double radians = Math.toRadians(yaw); return new org.bukkit.util.Vector(-Math.sin(radians), 0, Math.cos(radians)); }
    private double number(Object value) { return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value)); }
    private String cleanName(String name) { return name == null || name.isBlank() || name.equals("null") ? "ROULETTE" : name.substring(0, Math.min(28, name.length())); }
    private String nextId() { String id; do { id = UUID.randomUUID().toString().substring(0, 6).toLowerCase(Locale.ROOT); } while (tables.containsKey(id)); return id; }
    private String format(double value) { return new java.text.DecimalFormat("#,##0.##").format(value); }

    private void save() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (View view : tables.values()) { Table table = view.table; Map<String, Object> row = new LinkedHashMap<>(); row.put("id", table.id); row.put("world", table.location.getWorld().getName()); row.put("x", table.location.getX()); row.put("y", table.location.getY()); row.put("z", table.location.getZ()); row.put("yaw", table.location.getYaw()); row.put("style", table.style.id); row.put("name", table.name); row.put("glowing", table.glowing); rows.add(row); }
        plugin.getDataStore().set("physicalGames.roulette.tables", rows);
    }

    private static final class View { final Table table; final TextDisplay title, status; final List<Entity> structure, dynamic = new ArrayList<>(); Interaction opener; int animationGeneration; View(Table table, TextDisplay title, TextDisplay status, List<Entity> structure) { this.table = table; this.title = title; this.status = status; this.structure = structure; } void remove() { animationGeneration++; structure.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); dynamic.forEach(entity -> { if (entity != null && !entity.isDead()) entity.remove(); }); } }
    private record Table(String id, Location location, Style style, String name, boolean glowing) { Table { id = id.toLowerCase(Locale.ROOT); } TableSummary summary() { return new TableSummary(id, name, style.id, glowing, location.getYaw(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); } }
    public record TableSummary(String id, String name, String style, boolean glowing, float yaw, String world, int x, int y, int z) { public String locationText() { return world + " · " + x + ", " + y + ", " + z; } }
    private enum Style {
        CLASSIC("classic", Material.DARK_OAK_PLANKS, Material.POLISHED_BLACKSTONE, Material.GREEN_CONCRETE, Material.DARK_OAK_LOG, Material.GOLD_BLOCK, Material.GOLD_NUGGET, Material.IRON_NUGGET, NamedTextColor.GOLD, Color.YELLOW),
        ROYAL("royal", Material.PURPLE_CONCRETE, Material.GILDED_BLACKSTONE, Material.BLUE_CONCRETE, Material.POLISHED_BLACKSTONE, Material.AMETHYST_BLOCK, Material.AMETHYST_SHARD, Material.GOLD_NUGGET, NamedTextColor.LIGHT_PURPLE, Color.PURPLE),
        MODERN("modern", Material.SMOOTH_QUARTZ, Material.IRON_BLOCK, Material.CYAN_CONCRETE, Material.QUARTZ_PILLAR, Material.SEA_LANTERN, Material.DIAMOND, Material.IRON_NUGGET, NamedTextColor.AQUA, Color.AQUA),
        NETHER("nether", Material.CRIMSON_PLANKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.RED_NETHER_BRICKS, Material.CRIMSON_HYPHAE, Material.GILDED_BLACKSTONE, Material.GOLD_NUGGET, Material.NETHER_BRICK, NamedTextColor.RED, Color.RED);
        final String id; final Material base, rail, felt, leg, trim, chipA, chipB; final NamedTextColor accent; final Color glow;
        Style(String id, Material base, Material rail, Material felt, Material leg, Material trim, Material chipA, Material chipB, NamedTextColor accent, Color glow) { this.id = id; this.base = base; this.rail = rail; this.felt = felt; this.leg = leg; this.trim = trim; this.chipA = chipA; this.chipB = chipB; this.accent = accent; this.glow = glow; }
        static Style parse(String value) { for (Style style : values()) if (style.id.equalsIgnoreCase(value)) return style; return null; }
    }
}
