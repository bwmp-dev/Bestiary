package dev.bwmp.bestiary.listener;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.mob.MobInstance;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

/**
 * Bestiary mobs breed true: two parents sharing a definition produce a baby of
 * that definition rather than the plain vanilla animal underneath it.
 * <p>
 * Kept apart from {@link SpawnListener}, which only ever looks at
 * {@code NATURAL} spawns — a bred baby arrives as {@code BREEDING} and would
 * never be seen there.
 */
public final class BreedListener implements Listener {

    private final Engine engine;

    public BreedListener(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        NamespacedKey id = engine.mobs().idOf(event.getMother());
        if (id == null || !id.equals(engine.mobs().idOf(event.getFather()))) {
            // Ordinary animals, or a mixed pair where neither parent has a
            // better claim than the other. Vanilla keeps its baby.
            return;
        }

        Entity baby = event.getEntity();
        if (!(baby instanceof Ageable)) {
            return;
        }

        MobInstance mother = engine.mobs().instance(event.getMother());
        int level = mother == null ? 1 : mother.level();

        // The swap waits a tick because the baby is not in the world yet:
        // EntityBreedEvent is cancellable precisely because it fires before
        // vanilla adds it. Cancelling and spawning our own instead would be
        // wrong — the same vanilla call resets both parents out of love mode
        // and drops the breeding experience, and neither happens if the event
        // never completes, so the pair would breed again immediately.
        engine.scheduler().atEntityLater(baby, () -> {
            if (!baby.isValid()) {
                return;
            }
            Location where = baby.getLocation();
            baby.remove();
            engine.mobs().spawn(id, where, level).ifPresent(spawned -> {
                LivingEntity entity = spawned.entity();
                if (entity instanceof Ageable ageable) {
                    ageable.setBaby();
                }
            });
        }, 1L);
    }
}
