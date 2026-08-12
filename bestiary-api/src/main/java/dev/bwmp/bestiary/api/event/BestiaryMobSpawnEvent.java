package dev.bwmp.bestiary.api.event;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a Bestiary mob is spawned and fully configured. */
public class BestiaryMobSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BestiaryMob mob;
    private boolean cancelled;

    public BestiaryMobSpawnEvent(BestiaryMob mob) {
        this.mob = mob;
    }

    public BestiaryMob mob() {
        return mob;
    }

    /** Cancelling removes the entity again. */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
