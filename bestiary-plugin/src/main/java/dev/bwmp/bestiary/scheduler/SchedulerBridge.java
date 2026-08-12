package dev.bwmp.bestiary.scheduler;

import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.scheduler.KeystoneTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Presents Keystone's scheduler as Bestiary's own.
 * <p>
 * This thin layer exists because Keystone is relocated into Bestiary's jar. A
 * third party compiling a mechanic against {@code bestiary-api} must not be
 * handed a {@code dev.bwmp.keystone.*} type: they would compile against that
 * name while the shipped jar contains {@code dev.bwmp.bestiary.libs.keystone.*},
 * giving a NoClassDefFoundError that names a class which looks entirely correct.
 */
public final class SchedulerBridge implements BestiaryScheduler {

    private static final long NEVER_AGAIN = 20L * 60 * 60 * 24 * 7;

    private final KeystoneScheduler delegate;

    public SchedulerBridge(KeystoneScheduler delegate) {
        this.delegate = delegate;
    }

    @Override
    public BestiaryTask run(Runnable task) {
        return wrap(delegate.run(task));
    }

    @Override
    public BestiaryTask runLater(Runnable task, long delayTicks) {
        return wrap(delegate.runLater(task, delayTicks));
    }

    @Override
    public BestiaryTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(delegate.runTimer(task, delayTicks, periodTicks));
    }

    @Override
    public BestiaryTask atEntity(Entity entity, Runnable task) {
        return wrap(delegate.atEntity(entity, task));
    }

    @Override
    public BestiaryTask atEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return wrap(delegate.atEntityTimer(entity, task, delayTicks, periodTicks));
    }

    /**
     * A delayed one-shot that follows an entity between regions.
     * <p>
     * Neither backend offers one, so it is a timer that cancels itself on first
     * run. The period is a week rather than something small: if the cancel ever
     * failed, a long period is a stray task rather than a busy loop.
     */
    @Override
    public BestiaryTask atEntityLater(Entity entity, Runnable task, long delayTicks) {
        AtomicReference<KeystoneTask> holder = new AtomicReference<>();
        KeystoneTask handle = delegate.atEntityTimer(entity, () -> {
            KeystoneTask running = holder.get();
            if (running != null) {
                running.cancel();
            }
            task.run();
        }, Math.max(1L, delayTicks), NEVER_AGAIN);
        holder.set(handle);
        return wrap(handle);
    }

    @Override
    public BestiaryTask atLocation(Location location, Runnable task) {
        return wrap(delegate.atLocation(location, task));
    }

    @Override
    public BestiaryTask async(Runnable task) {
        return wrap(delegate.async(task));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        return delegate.teleport(entity, target);
    }

    @Override
    public boolean ownsRegion(Location location) {
        return delegate.ownsRegion(location);
    }

    @Override
    public boolean isFolia() {
        return delegate.isFolia();
    }

    private static BestiaryTask wrap(KeystoneTask task) {
        return new BestiaryTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
