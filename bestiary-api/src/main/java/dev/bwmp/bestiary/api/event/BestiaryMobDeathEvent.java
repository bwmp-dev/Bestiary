package dev.bwmp.bestiary.api.event;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/** Fired when a Bestiary mob dies, before drops are rolled. */
public class BestiaryMobDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BestiaryMob mob;
    private final Player killer;
    private final List<Player> contributors;

    public BestiaryMobDeathEvent(BestiaryMob mob, Player killer, List<Player> contributors) {
        this.mob = mob;
        this.killer = killer;
        this.contributors = List.copyOf(contributors);
    }

    public BestiaryMob mob() {
        return mob;
    }

    /** May be null — a boss can die to fall damage. */
    public Player killer() {
        return killer;
    }

    /** Every player who dealt damage over the fight, for drop shares. */
    public List<Player> contributors() {
        return contributors;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
