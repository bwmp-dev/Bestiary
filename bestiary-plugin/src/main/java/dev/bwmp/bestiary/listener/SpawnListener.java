package dev.bwmp.bestiary.listener;

import dev.bwmp.bestiary.Engine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Natural spawn replacement, kept apart from the mob triggers it does not touch. */
public final class SpawnListener implements Listener {

    private final Engine engine;

    public SpawnListener(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        // Bestiary's own spawns arrive as CUSTOM, so this cannot recurse. The
        // reason check is also what stops a spawner-driven mob being replaced
        // by a random rule that happens to match its position.
        if (engine.mobs().instance(event.getEntity()) != null) {
            return;
        }
        if (engine.spawns().replaceNaturalSpawn(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
