package me.cobbleBet.events.mysteriousGambler;

import me.cobbleBet.Main;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public final class MysteriousGamblerDeadLine extends BukkitRunnable {
    private final UUID uuid;

    public MysteriousGamblerDeadLine(UUID eventUUID) {

        this.uuid=eventUUID;
    }

    @Override
    public void run() {
        if (Main.activeEvents.get(uuid) instanceof WanderingTraderEvent event) {
            event.finish();
        }
    }
}
