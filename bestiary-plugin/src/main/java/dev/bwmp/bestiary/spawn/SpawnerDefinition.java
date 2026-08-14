package dev.bwmp.bestiary.spawn;

import dev.bwmp.bestiary.skill.CompiledCondition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.List;

/** An admin-placed spawn point, persisted and editable in-game. */
public final class SpawnerDefinition {

    private final String id;
    private final NamespacedKey mob;
    private final Location location;
    private final int level;
    private final double radius;
    private final long cooldownTicks;
    private final double activationRange;
    private final int maxConcurrent;
    private final int amountPerSpawn;
    private final boolean enabled;
    private final List<CompiledCondition> conditions;

    public SpawnerDefinition(String id, NamespacedKey mob, Location location, int level, double radius,
                             long cooldownTicks, double activationRange, int maxConcurrent,
                             int amountPerSpawn, boolean enabled, List<CompiledCondition> conditions) {
        this.id = id;
        this.mob = mob;
        this.location = location.clone();
        this.level = Math.max(1, level);
        this.radius = Math.max(0.0d, radius);
        this.cooldownTicks = Math.max(1L, cooldownTicks);
        this.activationRange = activationRange;
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.amountPerSpawn = Math.max(1, amountPerSpawn);
        this.enabled = enabled;
        this.conditions = List.copyOf(conditions);
    }

    public String id() {
        return id;
    }

    public NamespacedKey mob() {
        return mob;
    }

    public Location location() {
        return location.clone();
    }

    public int level() {
        return level;
    }

    /** Mobs appear within this radius of the point, not exactly on it. */
    public double radius() {
        return radius;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    public double activationRange() {
        return activationRange;
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public int amountPerSpawn() {
        return amountPerSpawn;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    @Override
    public String toString() {
        return id + " -> " + mob;
    }
}
