package me.cobbleBet.events.mysteriousGambler;

import me.cobbleBet.Main;
import me.cobbleBet.events.CobbleEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public final class WanderingTraderEvent extends CobbleEvent implements Listener {
    private final World world;
    private final GeneratedStructure village;
    private WanderingTrader gambler;
    private final List<UUID> inEvent = new ArrayList<>();
    private final List<UUID> eventDone = new ArrayList<>();
    private boolean finished;

    public WanderingTraderEvent(World world, GeneratedStructure village) {
        this.world = world;
        this.village = village;
        startEvent();
    }

    private void startEvent() {
        summonWanderingGambler();
        announce();
    }


    private void summonWanderingGambler() {
        Vector vector = village.getBoundingBox().getCenter();
        int x = (int) Math.floor(vector.getX());
        int z = (int) Math.floor(vector.getZ());
        Location loc = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        WanderingTrader gambler = (WanderingTrader) world.spawnEntity(loc, EntityType.WANDERING_TRADER);
        gambler.customName(Component.text("Mysterious Gambler", NamedTextColor.GOLD));
        gambler.setWanderingTowards(loc);
        long durationMinutes = Math.max(1L, Main.getInstance().getConfig()
                .getLong("events.wanderingGambler.durationMinutes", 5L));
        gambler.setDespawnDelay((int) Math.min(Integer.MAX_VALUE, durationMinutes * 60L * 20L + 20L));
        gambler.setPersistent(false);
        gambler.clearActivePotionEffects();
        gambler.clearActiveItem();
        gambler.setCanDrinkPotion(false);
        gambler.setCanPickupItems(false);
        gambler.setRecipes(List.of());

        this.gambler = gambler;
    }
    private void announce() {
        Component message = Component.text("A Mysterious Gambler has arrived in your village!", NamedTextColor.GOLD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)
                    && village.getBoundingBox().contains(player.getX(), player.getY(), player.getZ())) {
                player.sendMessage(message);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interactEntityListener(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (!(entity instanceof WanderingTrader clicked) || gambler == null
                || !gambler.getUniqueId().equals(clicked.getUniqueId())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;

        UUID playerId = e.getPlayer().getUniqueId();
        if (eventDone.contains(playerId)) {
            e.getPlayer().sendMessage(
                    Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                            .append(Component.text("Easy there... you've already had your shot.", NamedTextColor.GRAY))
            );
            e.getPlayer().sendMessage(
                    Component.text("One bet per traveler. Those are the rules.", NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.ITALIC)
            );
            return;
        }

                    if(!inEvent.contains(playerId)) {
                        inEvent.add(playerId);
                        e.getPlayer().sendMessage(
                                Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                        .append(Component.text("Psst... I have a deal for you.", NamedTextColor.WHITE))
                        );

                        e.getPlayer().sendMessage(
                                Component.text("Talk to me again if you're willing to wager ", NamedTextColor.GRAY)
                                        .append(Component.text("$1,000", NamedTextColor.GOLD))
                                        .append(Component.text(".", NamedTextColor.GRAY))
                        );

                        e.getPlayer().sendMessage(
                                Component.text("You either lose it all... or walk away with ", NamedTextColor.GRAY)
                                        .append(Component.text("$4,000", NamedTextColor.GREEN))
                                        .append(Component.text(".", NamedTextColor.GRAY))
                        );

                        e.getPlayer().sendMessage(
                                Component.text("Think you've got the luck?", NamedTextColor.DARK_GRAY)
                                        .decorate(TextDecoration.ITALIC)
                        );
                        e.getPlayer().closeInventory();
                        return;
                    }
                    else {
                        if (Main.getWallet(playerId).getBalance() < 1000) {
                            e.getPlayer().sendMessage(
                                    Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                                            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                            .append(Component.text("Come back when you have $1,000.", NamedTextColor.RED))
                            );
                            return;
                        }
                        eventDone.add(playerId);
                        Main.getWallet(playerId).removeCurrency(1000);
                        e.getPlayer().sendMessage(
                                Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                        .append(Component.text("Heh... you've got guts.", NamedTextColor.WHITE))
                        );

                        e.getPlayer().sendMessage(
                                Component.text("$1,000", NamedTextColor.RED)
                                        .append(Component.text(" has been taken from your wallet.", NamedTextColor.GRAY))
                        );

                        e.getPlayer().sendMessage(
                                Component.text("Let's see if fortune favors you...", NamedTextColor.YELLOW)
                                        .decorate(TextDecoration.ITALIC)
                        );


                        // - 50/50 chance to win
                        Random rand = new Random();
                        if (rand.nextBoolean()) {
                            // WIN
                            e.getPlayer().sendMessage(
                                    Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                                            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                            .append(Component.text("HAHA! You actually did it!", NamedTextColor.GREEN))
                            );

                            e.getPlayer().sendMessage(
                                    Component.text("You won ", NamedTextColor.GRAY)
                                            .append(Component.text("$4,000", NamedTextColor.GREEN, TextDecoration.BOLD))
                                            .append(Component.text("!", NamedTextColor.GRAY))
                            );

                            Main.getWallet(playerId).addCurrency(4000);
                        } else {
// LOSS
                            e.getPlayer().sendMessage(
                                    Component.text("Mysterious Gambler", NamedTextColor.GOLD, TextDecoration.BOLD)
                                            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                            .append(Component.text("Ouch... better luck next time.", NamedTextColor.RED))
                            );

                            e.getPlayer().sendMessage(
                                    Component.text("You lost your ", NamedTextColor.GRAY)
                                            .append(Component.text("$1,000", NamedTextColor.RED, TextDecoration.BOLD))
                                            .append(Component.text(".", NamedTextColor.GRAY))
                            );

                        }

                    }
                    e.getPlayer().closeInventory();


    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void blockTradeScreen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;
        if (!(merchantInventory.getMerchant() instanceof WanderingTrader trader)) return;
        if (gambler != null && gambler.getUniqueId().equals(trader.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public void finish() {
        if (finished) return;
        finished = true;
        HandlerList.unregisterAll(this);
        Main.activeEvents.remove(id(), this);
        if (gambler != null && gambler.isValid()) gambler.remove();

        Component message = Component.text("The Mysterious Gambler has moved on.", NamedTextColor.GRAY);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)
                    && village.getBoundingBox().contains(player.getX(), player.getY(), player.getZ())) {
                player.sendMessage(message);
            }
        }
    }

}
