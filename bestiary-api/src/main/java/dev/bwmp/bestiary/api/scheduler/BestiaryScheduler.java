package dev.bwmp.bestiary.api.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduling for mechanics, goals and anything else that runs on a timer.
 * {@code BukkitRunnable} must not be used in Bestiary code; going through this
 * is what lets a skill work on Folia unchanged.
 * <p>
 * This deliberately mirrors Keystone's scheduler rather than exposing it.
 * Keystone is relocated into Bestiary's jar, so a third party compiling against
 * {@code bestiary-api} would resolve {@code dev.bwmp.keystone.KeystoneScheduler}
 * while the shipped jar contains {@code dev.bwmp.bestiary.libs.keystone....} —
 * a NoClassDefFoundError naming a class that looks entirely correct.
 */
public interface BestiaryScheduler {

    BestiaryTask run(Runnable task);

    BestiaryTask runLater(Runnable task, long delayTicks);

    BestiaryTask runTimer(Runnable task, long delayTicks, long periodTicks);

    /** Runs on the thread owning {@code entity}, following it between regions. */
    BestiaryTask atEntity(Entity entity, Runnable task);

    BestiaryTask atEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs once on the thread owning {@code entity}, after a delay.
     * <p>
     * Declared separately because neither Bukkit's nor Folia's entity scheduler
     * offers a delayed one-shot that follows an entity between regions, and
     * open-coding the self-cancelling timer at every call site is how one of
     * them ends up never cancelling.
     */
    BestiaryTask atEntityLater(Entity entity, Runnable task, long delayTicks);

    /** Runs on the thread owning the region containing {@code location}. */
    BestiaryTask atLocation(Location location, Runnable task);

    /** Off the server threads entirely. Must not touch world state. */
    BestiaryTask async(Runnable task);

    CompletableFuture<Boolean> teleport(Entity entity, Location target);

    /** True when the calling thread may touch blocks at {@code location}. */
    boolean ownsRegion(Location location);

    boolean isFolia();
}
