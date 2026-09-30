package me.cobbleBet.visuals;

import me.cobbleBet.Main;
import me.cobbleBet.gui.CoinflipController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.*;

public final class CoinflipBoardManager implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private final Main plugin;
    private final NamespacedKey boardKey;
    private final Map<String, BoardView> boards = new LinkedHashMap<>();
    private List<CoinflipController.Listing> listings = List.of();

    public CoinflipBoardManager(Main plugin) {
        this.plugin = plugin;
        boardKey = new NamespacedKey(plugin, "coinflip_board");
    }

    public void load() {
        clearEntities();
        boards.clear();
        for (Map<?, ?> map : plugin.getConfig().getMapList("physicalGames.coinflip.boards")) {
            try {
                World world = Bukkit.getWorld(String.valueOf(map.get("world")));
                Style style = Style.parse(String.valueOf(map.get("style")));
                if (world == null || style == null) continue;
                double savedY=number(map.get("y"));
                int modelVersion=map.get("modelVersion") instanceof Number version?version.intValue():1;
                if(modelVersion==1)savedY=Math.floor(savedY)+.03;
                else if(modelVersion==2)savedY-=1.52;
                spawn(new Board(String.valueOf(map.get("id")),
                        new Location(world, number(map.get("x")), savedY, number(map.get("z")), (float)number(map.get("yaw")), 0),
                        style, String.valueOf(map.get("name"))));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Skipped an invalid Coinflip board: " + error.getMessage());
            }
        }
    }

    public void create(Player player, String styleName, String name) {
        Style style = Style.parse(styleName);
        if (style == null) { player.sendMessage("§cUnknown style. Choose: " + String.join(", ", styles())); return; }
        Board board = new Board(nextId(), placement(player), style,
                name == null || name.isBlank() ? "COINFLIP" : name.substring(0, Math.min(28, name.length())));
        spawn(board); save();
        player.sendMessage("§aCreated Coinflip board §f" + board.id + "§a. Click it in §f/cobblebet game coinflip §ato manage it.");
    }

    public boolean moveHere(Player player, String id) {
        BoardView view = find(id); if (view == null) return missing(player, id);
        respawn(view, new Board(view.board.id, placement(player), view.board.style, view.board.name));
        player.sendMessage("§aMoved board §f" + view.board.id + " §ato your position."); return true;
    }

    public boolean changeStyle(Player player, String id, String styleName) {
        BoardView view = find(id); if (view == null) return missing(player, id);
        Style style = Style.parse(styleName);
        if (style == null) { player.sendMessage("§cUnknown style. Choose: " + String.join(", ", styles())); return false; }
        respawn(view, new Board(view.board.id, view.board.location.clone(), style, view.board.name));
        player.sendMessage("§aBoard §f" + view.board.id + " §anow uses §f" + style.id + "§a."); return true;
    }

    public boolean cycleStyle(Player player, String id) {
        BoardView view = find(id); if (view == null) return missing(player, id);
        Style[] values = Style.values(); return changeStyle(player, id, values[(view.board.style.ordinal()+1)%values.length].id);
    }

    public boolean rename(Player player, String id, String name) {
        BoardView view = find(id); if (view == null) return missing(player, id);
        String clean = name == null ? "" : name.trim(); if (clean.isEmpty()) { player.sendMessage("§cEnter a board name."); return false; }
        respawn(view, new Board(view.board.id, view.board.location.clone(), view.board.style, clean.substring(0, Math.min(28,clean.length()))));
        player.sendMessage("§aRenamed board §f" + view.board.id + "§a."); return true;
    }

    public boolean remove(Player player, String id) {
        BoardView view = find(id); if (view == null) return missing(player, id);
        view.remove(); boards.remove(view.board.id); save();
        player.sendMessage("§aRemoved Coinflip board §f" + view.board.id + "§a."); return true;
    }

    public void updateListings(List<CoinflipController.Listing> next) { listings=List.copyOf(next);boards.values().forEach(BoardView::update); }
    public Set<String> styles(){return new LinkedHashSet<>(Arrays.stream(Style.values()).map(style->style.id).toList());}
    public List<BoardSummary> summaries(){return boards.values().stream().map(view->view.board.summary()).toList();}
    public BoardSummary summary(String id){BoardView view=find(id);return view==null?null:view.board.summary();}
    public void clearAll(){clearEntities();boards.clear();}

    private void respawn(BoardView old,Board replacement){old.remove();boards.remove(old.board.id);spawn(replacement);save();}
    private boolean missing(Player player,String id){player.sendMessage("§cNo Coinflip board exists with ID §f"+id+"§c.");return false;}
    private BoardView find(String id){return id==null?null:boards.get(id.toLowerCase(Locale.ROOT));}
    private void clearEntities(){boards.values().forEach(BoardView::remove);}

    private void spawn(Board board) {
        World world=board.location.getWorld();if(world==null)return;
        List<Entity> entities=new ArrayList<>();float face=board.location.getYaw()+180;

        // Precise display geometry keeps the model straight and properly joined.
        // Invisible armor stands carry the decorative coins above the frame.
        entities.add(block(world,point(board.location,0,2.28,0),face,board.style.panel,3.72f,2.18f,.16f));
        entities.add(block(world,point(board.location,0,3.43,.01),face,board.style.frame,4.02f,.20f,.28f));
        entities.add(block(world,point(board.location,0,1.13,.01),face,board.style.frame,4.02f,.20f,.28f));
        entities.add(block(world,point(board.location,-1.91,2.28,.01),face,board.style.frame,.20f,2.50f,.28f));
        entities.add(block(world,point(board.location,1.91,2.28,.01),face,board.style.frame,.20f,2.50f,.28f));
        for(double side:new double[]{-1.28,1.28}){
            entities.add(block(world,point(board.location,side,.57,.04),face,board.style.post,.20f,1.14f,.20f));
            entities.add(block(world,point(board.location,side,.08,.04),face,board.style.frame,.72f,.16f,.54f));
            entities.add(decoration(world,point(board.location,side,3.71,.10),face,board.style.icon,side<0));
        }

        TextDisplay header=text(world,point(board.location,0,3.12,.19),face,Color.fromARGB(145,5,5,5),1.08f);entities.add(header);
        List<TextDisplay> rows=new ArrayList<>();
        for(int i=0;i<4;i++){
            Location at=point(board.location,0,2.72-i*.43,.20);
            TextDisplay row=text(world,at,face,board.style.textBackground,1f);entities.add(row);rows.add(row);
            Interaction hit=world.spawn(at,Interaction.class,e->{e.setInteractionWidth(3.15f);e.setInteractionHeight(.39f);e.setResponsive(true);e.setPersistent(false);e.getPersistentDataContainer().set(boardKey,PersistentDataType.STRING,board.id);});
            entities.add(hit);
        }
        TextDisplay footer=text(world,point(board.location,0,1.24,.19),face,Color.fromARGB(145,5,5,5),.78f);entities.add(footer);
        BoardView view=new BoardView(board,header,rows,footer,entities);boards.put(board.id,view);view.update();
    }

    private BlockDisplay block(World world,Location center,float yaw,Material material,float width,float height,float depth){return world.spawn(center,BlockDisplay.class,e->{e.setBlock(material.createBlockData());e.setRotation(yaw,0);e.setPersistent(false);e.setViewRange(1.5f);e.setTransformation(new Transformation(new Vector3f(-width/2,-height/2,-depth/2),new Quaternionf(),new Vector3f(width,height,depth),new Quaternionf()));});}
    private ArmorStand decoration(World world,Location target,float yaw,Material material,boolean left){return world.spawn(target.clone().add(0,-.48,0),ArmorStand.class,stand->{stand.setInvisible(true);stand.setMarker(true);stand.setSmall(true);stand.setGravity(false);stand.setPersistent(false);stand.setBasePlate(false);stand.setArms(true);stand.setRotation(yaw,0);stand.setRightArmPose(new EulerAngle(Math.toRadians(-78),0,Math.toRadians(left?-18:18)));stand.getEquipment().setItemInMainHand(new ItemStack(material),true);stand.getPersistentDataContainer().set(boardKey,PersistentDataType.STRING,"model");});}
    private TextDisplay text(World world,Location location,float yaw,Color background,float scale){return world.spawn(location,TextDisplay.class,e->{e.setBillboard(Display.Billboard.FIXED);e.setRotation(yaw,0);e.setAlignment(TextDisplay.TextAlignment.CENTER);e.setBackgroundColor(background);e.setShadowed(true);e.setSeeThrough(false);e.setLineWidth(340);e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));});}
    private Location placement(Player player){Location location=player.getLocation().clone();location.setPitch(0);location.setYaw(snapYaw(location.getYaw()));location.add(direction(location.getYaw()).multiply(3));location.setY(Math.floor(location.getY())+.03);return location;}
    private Location point(Location origin,double right,double up,double forward){double r=Math.toRadians(origin.getYaw());return origin.clone().add(-Math.cos(r)*right+Math.sin(r)*forward,up,-Math.sin(r)*right-Math.cos(r)*forward);}
    private org.bukkit.util.Vector direction(float yaw){double r=Math.toRadians(yaw);return new org.bukkit.util.Vector(-Math.sin(r),0,Math.cos(r));}
    private float snapYaw(float yaw){return Math.round(yaw/22.5f)*22.5f;}
    private double number(Object value){return value instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(value));}
    private String nextId(){String id;do{id=UUID.randomUUID().toString().substring(0,6).toLowerCase(Locale.ROOT);}while(boards.containsKey(id));return id;}

    private void save(){List<Map<String,Object>> rows=new ArrayList<>();for(BoardView view:boards.values()){Board b=view.board;Map<String,Object> row=new LinkedHashMap<>();row.put("id",b.id);row.put("modelVersion",3);row.put("world",b.location.getWorld().getName());row.put("x",b.location.getX());row.put("y",b.location.getY());row.put("z",b.location.getZ());row.put("yaw",b.location.getYaw());row.put("style",b.style.id);row.put("name",b.name);rows.add(row);}plugin.getConfig().set("physicalGames.coinflip.boards",rows);plugin.getConfig().setComments("physicalGames",List.of("","PHYSICAL GAME BOARDS — managed with /cobblebet game coinflip","Locations and styles are saved here so boards return after restarts."));plugin.saveConfig();}

    @EventHandler public void interact(PlayerInteractEntityEvent event){String id=event.getRightClicked().getPersistentDataContainer().get(boardKey,PersistentDataType.STRING);if(id==null||id.equals("model"))return;event.setCancelled(true);plugin.coinflipController.openLobby(event.getPlayer(),0);}

    private final class BoardView {
        final Board board;final TextDisplay header;final List<TextDisplay> rows;final TextDisplay footer;final List<Entity> entities;
        BoardView(Board board,TextDisplay header,List<TextDisplay> rows,TextDisplay footer,List<Entity> entities){this.board=board;this.header=header;this.rows=rows;this.footer=footer;this.entities=entities;}
        void update(){header.text(Component.text("◆  "+board.name+"  ◆",board.style.accent).decorate(TextDecoration.BOLD));for(int i=0;i<rows.size();i++){if(i<listings.size()){var flip=listings.get(i);rows.get(i).text(Component.text(flip.creatorName()+"  ",NamedTextColor.WHITE).append(Component.text(MONEY.format(flip.amount()),board.style.accent)).append(Component.text("  •  PLAY",NamedTextColor.GRAY)));}else rows.get(i).text(Component.text(i==0?"No open matches — create one":"·     ·     ·",NamedTextColor.DARK_GRAY));}footer.text(Component.text("RIGHT CLICK A MATCH",NamedTextColor.GRAY));}
        void remove(){entities.forEach(entity->{if(entity!=null&&!entity.isDead())entity.remove();});}
    }
    private static final class Board {final String id;final Location location;final Style style;final String name;Board(String id,Location location,Style style,String name){this.id=id.toLowerCase(Locale.ROOT);this.location=location;this.style=style;this.name=name;}BoardSummary summary(){return new BoardSummary(id,name,style.id,location.getWorld().getName(),location.getBlockX(),location.getBlockY(),location.getBlockZ());}}
    public record BoardSummary(String id,String name,String style,String world,int x,int y,int z){public String locationText(){return world+" · "+x+", "+y+", "+z;}}
    private enum Style {
        STREET("street",Material.GRAY_CONCRETE,Material.POLISHED_BLACKSTONE,Material.DARK_OAK_LOG,Color.fromARGB(190,30,33,36),NamedTextColor.GOLD,Material.GOLD_NUGGET),
        CLASSIC("classic",Material.DARK_OAK_PLANKS,Material.STRIPPED_DARK_OAK_LOG,Material.DARK_OAK_LOG,Color.fromARGB(190,24,42,31),NamedTextColor.GREEN,Material.EMERALD),
        ROYAL("royal",Material.PURPLE_CONCRETE,Material.GILDED_BLACKSTONE,Material.POLISHED_BLACKSTONE,Color.fromARGB(190,43,29,63),NamedTextColor.LIGHT_PURPLE,Material.AMETHYST_SHARD),
        COPPER("copper",Material.WAXED_COPPER_BLOCK,Material.WAXED_CUT_COPPER,Material.DEEPSLATE_BRICK_WALL,Color.fromARGB(190,65,39,27),NamedTextColor.GOLD,Material.COPPER_INGOT);
        final String id;final Material panel,frame,post;final Color textBackground;final NamedTextColor accent;final Material icon;
        Style(String id,Material panel,Material frame,Material post,Color textBackground,NamedTextColor accent,Material icon){this.id=id;this.panel=panel;this.frame=frame;this.post=post;this.textBackground=textBackground;this.accent=accent;this.icon=icon;}
        static Style parse(String value){for(Style style:values())if(style.id.equalsIgnoreCase(value))return style;return null;}
    }
}
