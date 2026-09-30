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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public final class BlackjackTableManager implements Listener {
    private final Main plugin;private final NamespacedKey key;private final Map<String,View> tables=new LinkedHashMap<>();
    public BlackjackTableManager(Main plugin){this.plugin=plugin;key=new NamespacedKey(plugin,"blackjack_table");}
    public void load(){clearAll();for(Map<?,?> map:plugin.getConfig().getMapList("physicalGames.blackjack.tables"))try{World world=Bukkit.getWorld(String.valueOf(map.get("world")));Style style=Style.parse(String.valueOf(map.get("style")));if(world==null||style==null)continue;spawn(new Table(String.valueOf(map.get("id")),new Location(world,num(map.get("x")),num(map.get("y")),num(map.get("z")),(float)num(map.get("yaw")),0),style,String.valueOf(map.get("name"))));}catch(RuntimeException e){plugin.getLogger().warning("Skipped an invalid Blackjack table: "+e.getMessage());}}
    public void create(Player player,String styleName,String name){Style style=Style.parse(styleName);if(style==null){player.sendMessage("§cUnknown style. Choose: "+String.join(", ",styles()));return;}Table table=new Table(nextId(),placement(player),style,name==null||name.isBlank()?"BLACKJACK":name.substring(0,Math.min(28,name.length())));spawn(table);save();player.sendMessage("§aCreated Blackjack table §f"+table.id+"§a.");}
    public boolean moveHere(Player player,String id){View view=find(id);if(view==null)return missing(player,id);replace(view,new Table(view.table.id,placement(player),view.table.style,view.table.name));player.sendMessage("§aMoved Blackjack table §f"+id+"§a.");return true;}
    public boolean changeStyle(Player player,String id,String styleName){View view=find(id);if(view==null)return missing(player,id);Style style=Style.parse(styleName);if(style==null){player.sendMessage("§cUnknown style. Choose: "+String.join(", ",styles()));return false;}replace(view,new Table(view.table.id,view.table.location.clone(),style,view.table.name));player.sendMessage("§aBlackjack table §f"+id+" §anow uses §f"+style.id+"§a.");return true;}
    public boolean rename(Player player,String id,String name){View view=find(id);if(view==null)return missing(player,id);if(name==null||name.isBlank()){player.sendMessage("§cEnter a table name.");return false;}String clean=name.substring(0,Math.min(28,name.length()));replace(view,new Table(view.table.id,view.table.location.clone(),view.table.style,clean));player.sendMessage("§aRenamed Blackjack table §f"+id+" §ato §f"+clean+"§a.");return true;}
    public boolean remove(Player player,String id){View view=find(id);if(view==null)return missing(player,id);view.remove();tables.remove(view.table.id);save();player.sendMessage("§aRemoved Blackjack table §f"+id+"§a.");return true;}
    public Set<String> styles(){return new LinkedHashSet<>(Arrays.stream(Style.values()).map(s->s.id).toList());}public List<TableSummary> summaries(){return tables.values().stream().map(v->v.table.summary()).toList();}public TableSummary summary(String id){View v=find(id);return v==null?null:v.table.summary();}
    public void clearAll(){tables.values().forEach(View::remove);tables.clear();}
    private void replace(View old,Table table){old.remove();tables.remove(old.table.id);spawn(table);save();}private View find(String id){return id==null?null:tables.get(id.toLowerCase(Locale.ROOT));}private boolean missing(Player p,String id){p.sendMessage("§cNo Blackjack table exists with ID §f"+id+"§c.");return false;}

    private void spawn(Table table){
        World world=table.location.getWorld();if(world==null)return;
        float face=table.location.getYaw()+180;List<Entity> entities=new ArrayList<>();
        // A compact waist-high card table: slim top, inset felt, four joined rails and grounded legs.
        entities.add(block(world,point(table.location,0,.75,0),face,table.style.base,3.0f,.18f,1.72f));
        entities.add(block(world,point(table.location,0,.855,0),face,table.style.felt,2.68f,.045f,1.40f));
        entities.add(block(world,point(table.location,0,.89,-.77),face,table.style.rail,3.08f,.12f,.16f));
        entities.add(block(world,point(table.location,0,.89,.77),face,table.style.rail,3.08f,.12f,.16f));
        entities.add(block(world,point(table.location,-1.46,.89,0),face,table.style.rail,.16f,.12f,1.42f));
        entities.add(block(world,point(table.location,1.46,.89,0),face,table.style.rail,.16f,.12f,1.42f));
        for(double x:new double[]{-1.22,1.22})for(double z:new double[]{-.57,.57})entities.add(block(world,point(table.location,x,.36,z),face,table.style.leg,.18f,.72f,.18f));
        for(double x:new double[]{-1.42,1.42})for(double z:new double[]{-.72,.72})entities.add(block(world,point(table.location,x,.955,z),face,table.style.trim,.17f,.055f,.17f));
        TextDisplay title=text(world,point(table.location,0,.73,.91),face,.72f);title.text(Component.text("♠  "+table.name+"  ♠",table.style.accent).decorate(TextDecoration.BOLD));entities.add(title);
        TextDisplay prompt=text(world,point(table.location,0,.48,.92),face,.58f);prompt.text(Component.text("RIGHT CLICK TO PLAY",NamedTextColor.WHITE));entities.add(prompt);
        TextDisplay feltMark=flatText(world,point(table.location,0,.91,.04),face,.58f);feltMark.text(Component.text("♠    ♥    21    ♦    ♣",table.style.accent).decorate(TextDecoration.BOLD));entities.add(feltMark);
        Interaction interaction=world.spawn(point(table.location,0,.95,.12),Interaction.class,e->{e.setInteractionWidth(2.95f);e.setInteractionHeight(.72f);e.setResponsive(true);e.setPersistent(false);e.getPersistentDataContainer().set(key,PersistentDataType.STRING,table.id);});entities.add(interaction);
        entities.add(card(world,point(table.location,-.25,.955,-.34),face,Material.PAPER,-13));
        entities.add(card(world,point(table.location,0,.958,-.32),face,Material.MAP,0));
        entities.add(card(world,point(table.location,.25,.961,-.34),face,Material.PAPER,13));
        entities.add(card(world,point(table.location,-.16,.957,.38),face,Material.MAP,-8));
        entities.add(card(world,point(table.location,.16,.960,.39),face,Material.PAPER,9));
        for(double side:new double[]{-.82,.82}){
            Material first=side<0?table.style.chipA:table.style.chipB,second=side<0?table.style.chipB:table.style.chipA;
            entities.add(chip(world,point(table.location,side,.966,.27),face,first,.25f));
            entities.add(chip(world,point(table.location,side-.10,.969,.38),face,second,.21f));
            entities.add(chip(world,point(table.location,side+.10,.972,.38),face,first,.21f));
        }
        tables.put(table.id,new View(table,entities));
    }
    private BlockDisplay block(World world,Location center,float yaw,Material material,float w,float h,float d){return world.spawn(center,BlockDisplay.class,e->{e.setBlock(material.createBlockData());e.setRotation(yaw,0);e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(-w/2,-h/2,-d/2),new Quaternionf(),new Vector3f(w,h,d),new Quaternionf()));});}
    private TextDisplay text(World world,Location at,float yaw,float scale){return world.spawn(at,TextDisplay.class,e->{e.setBillboard(Display.Billboard.FIXED);e.setRotation(yaw,0);e.setAlignment(TextDisplay.TextAlignment.CENTER);e.setShadowed(true);e.setSeeThrough(false);e.setBackgroundColor(Color.fromARGB(165,7,10,8));e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));});}
    private TextDisplay flatText(World world,Location at,float yaw,float scale){return world.spawn(at,TextDisplay.class,e->{e.setBillboard(Display.Billboard.FIXED);e.setRotation(yaw,90);e.setAlignment(TextDisplay.TextAlignment.CENTER);e.setShadowed(false);e.setSeeThrough(false);e.setBackgroundColor(Color.fromARGB(0,0,0,0));e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));});}
    private ItemDisplay card(World world,Location at,float yaw,Material material,float tilt){return world.spawn(at,ItemDisplay.class,e->{e.setItemStack(new ItemStack(material));e.setBillboard(Display.Billboard.FIXED);e.setRotation(yaw,90);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateZ((float)Math.toRadians(tilt)),new Vector3f(.27f),new Quaternionf()));});}
    private ItemDisplay chip(World world,Location at,float yaw,Material material,float scale){return world.spawn(at,ItemDisplay.class,e->{e.setItemStack(new ItemStack(material));e.setBillboard(Display.Billboard.FIXED);e.setRotation(yaw,0);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);e.setPersistent(false);e.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(scale),new Quaternionf()));});}
    private Location placement(Player p){Location l=p.getLocation().clone();l.setPitch(0);l.setYaw(Math.round(l.getYaw()/22.5f)*22.5f);l.add(direction(l.getYaw()).multiply(3));l.setY(Math.floor(l.getY())+.03);return l;}private org.bukkit.util.Vector direction(float yaw){double r=Math.toRadians(yaw);return new org.bukkit.util.Vector(-Math.sin(r),0,Math.cos(r));}private Location point(Location o,double right,double up,double forward){double r=Math.toRadians(o.getYaw());return o.clone().add(-Math.cos(r)*right+Math.sin(r)*forward,up,-Math.sin(r)*right-Math.cos(r)*forward);}private double num(Object v){return v instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(v));}private String nextId(){String id;do{id=UUID.randomUUID().toString().substring(0,6).toLowerCase(Locale.ROOT);}while(tables.containsKey(id));return id;}
    private void save(){List<Map<String,Object>> rows=new ArrayList<>();for(View view:tables.values()){Table t=view.table;Map<String,Object> row=new LinkedHashMap<>();row.put("id",t.id);row.put("world",t.location.getWorld().getName());row.put("x",t.location.getX());row.put("y",t.location.getY());row.put("z",t.location.getZ());row.put("yaw",t.location.getYaw());row.put("style",t.style.id);row.put("name",t.name);rows.add(row);}plugin.getConfig().set("physicalGames.blackjack.tables",rows);plugin.saveConfig();}
    @EventHandler public void interact(PlayerInteractEntityEvent e){if(e.getRightClicked().getPersistentDataContainer().get(key,PersistentDataType.STRING)==null)return;e.setCancelled(true);plugin.blackjackController.open(e.getPlayer());}
    private record View(Table table,List<Entity> entities){void remove(){entities.forEach(e->{if(e!=null&&!e.isDead())e.remove();});}}private record Table(String id,Location location,Style style,String name){Table{ id=id.toLowerCase(Locale.ROOT);}TableSummary summary(){return new TableSummary(id,name,style.id,location.getWorld().getName(),location.getBlockX(),location.getBlockY(),location.getBlockZ());}}public record TableSummary(String id,String name,String style,String world,int x,int y,int z){public String locationText(){return world+" · "+x+", "+y+", "+z;}}
    private enum Style{CASINO("casino",Material.DARK_OAK_PLANKS,Material.POLISHED_BLACKSTONE,Material.GREEN_CONCRETE,Material.DARK_OAK_LOG,Material.GOLD_BLOCK,Material.GOLD_NUGGET,Material.EMERALD,NamedTextColor.GOLD),SALOON("saloon",Material.SPRUCE_PLANKS,Material.DARK_OAK_PLANKS,Material.GREEN_WOOL,Material.SPRUCE_LOG,Material.COPPER_BLOCK,Material.COPPER_INGOT,Material.GOLD_NUGGET,NamedTextColor.YELLOW),ROYAL("royal",Material.PURPLE_CONCRETE,Material.GILDED_BLACKSTONE,Material.BLUE_CONCRETE,Material.POLISHED_BLACKSTONE,Material.AMETHYST_BLOCK,Material.AMETHYST_SHARD,Material.GOLD_NUGGET,NamedTextColor.LIGHT_PURPLE),MODERN("modern",Material.SMOOTH_QUARTZ,Material.IRON_BLOCK,Material.CYAN_CONCRETE,Material.QUARTZ_PILLAR,Material.SEA_LANTERN,Material.DIAMOND,Material.IRON_NUGGET,NamedTextColor.AQUA);final String id;final Material base,rail,felt,leg,trim,chipA,chipB;final NamedTextColor accent;Style(String id,Material base,Material rail,Material felt,Material leg,Material trim,Material chipA,Material chipB,NamedTextColor accent){this.id=id;this.base=base;this.rail=rail;this.felt=felt;this.leg=leg;this.trim=trim;this.chipA=chipA;this.chipB=chipB;this.accent=accent;}static Style parse(String value){for(Style s:values())if(s.id.equalsIgnoreCase(value))return s;return null;}}
}
