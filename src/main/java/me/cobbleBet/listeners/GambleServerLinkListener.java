package me.cobbleBet.listeners;

import me.cobbleBet.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ServerLinks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLinksSendEvent;

import java.net.URI;

/** Adds a CobbleBet entry to Minecraft's Escape-menu server links screen. */
public final class GambleServerLinkListener implements Listener {
    @EventHandler
    public void onLinksSent(PlayerLinksSendEvent event) {
        addGambleLink(event.getLinks());
    }

    private void addGambleLink(ServerLinks links) {
        String baseUrl = Main.testMode ? "http://localhost:8908" : "https://cobblebet.com";
        URI panelUrl = URI.create(baseUrl + "/panel");
        if (links.getLinks().stream().anyMatch(link -> panelUrl.equals(link.getUrl()))) return;
        links.addLink(
                Component.text("Gamble", NamedTextColor.GOLD),
                panelUrl
        );
    }

    /**
     * Paper sends server links while a player is joining. Sending the current
     * links one tick later makes the entry visible even if another plugin or
     * the initial login packet replaced the first set of links.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Main.getInstance().getServer().getScheduler().runTaskLater(
                Main.getInstance(),
                () -> {
                    ServerLinks links = Main.getInstance().getServer().getServerLinks().copy();
                    addGambleLink(links);
                    event.getPlayer().sendLinks(links);
                },
                1L
        );
    }
}
