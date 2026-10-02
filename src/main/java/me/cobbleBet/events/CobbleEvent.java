package me.cobbleBet.events;

import java.time.Instant;
import java.util.UUID;

public abstract class CobbleEvent {
    private final UUID id = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    public UUID id() {
        return id;
    }

    public Instant startedAt() {
        return startedAt;
    }
}
