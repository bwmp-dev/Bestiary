package dev.bwmp.bestiary.api.event;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a mob advances from one declared phase to the next. */
public class BestiaryPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BestiaryMob mob;
    private final String from;
    private final String to;

    public BestiaryPhaseChangeEvent(BestiaryMob mob, String from, String to) {
        this.mob = mob;
        this.from = from;
        this.to = to;
    }

    public BestiaryMob mob() {
        return mob;
    }

    /** Empty on the first transition into the opening phase. */
    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
