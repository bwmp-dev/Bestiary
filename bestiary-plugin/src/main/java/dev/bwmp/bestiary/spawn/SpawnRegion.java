package dev.bwmp.bestiary.spawn;

import dev.bwmp.bestiary.skill.CompiledCondition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;

public final class SpawnRegion {

    private final String id;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Map<NamespacedKey, Double> weights;
    private final int maxConcurrent;
    private final long cooldownTicks;
    private final double activationRange;
    private final int level;
    private final List<CompiledCondition> conditions;

    public SpawnRegion(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       Map<NamespacedKey, Double> weights, int maxConcurrent, long cooldownTicks,
                       double activationRange, int level, List<CompiledCondition> conditions) {
        this.id = id;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.weights = Map.copyOf(weights);
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.cooldownTicks = Math.max(1L, cooldownTicks);
        this.activationRange = activationRange;
        this.level = Math.max(1, level);
        this.conditions = List.copyOf(conditions);
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    public Map<NamespacedKey, Double> weights() {
        return weights;
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    public double activationRange() {
        return activationRange;
    }

    public int level() {
        return level;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    public boolean contains(Location location) {
        return location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public Location centre() {
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        return bukkitWorld == null ? null
                : new Location(bukkitWorld, (minX + maxX) / 2.0d, (minY + maxY) / 2.0d, (minZ + maxZ) / 2.0d);
    }

    public Location randomPoint() {
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        return new Location(bukkitWorld,
                random.nextInt(minX, maxX + 1) + 0.5d,
                random.nextInt(minY, maxY + 1),
                random.nextInt(minZ, maxZ + 1) + 0.5d);
    }

    @Override
    public String toString() {
        return id;
    }
}
