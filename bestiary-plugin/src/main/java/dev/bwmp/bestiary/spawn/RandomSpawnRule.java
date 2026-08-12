package dev.bwmp.bestiary.spawn;

import dev.bwmp.bestiary.skill.CompiledCondition;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Natural spawn replacement or augmentation.
 * <p>
 * Every rule carries a per-world budget, because a filter written slightly too
 * wide is otherwise indistinguishable from a denial of service — and the person
 * who wrote it will be the last to suspect it.
 */
public final class RandomSpawnRule {

    private final String id;
    private final NamespacedKey mob;
    private final Set<String> worlds;
    private final Set<String> biomes;
    private final Set<String> replaces;
    private final int minY;
    private final int maxY;
    private final int minLight;
    private final int maxLight;
    private final boolean day;
    private final boolean night;
    private final double chance;
    private final int level;
    private final int perWorldBudget;
    private final List<CompiledCondition> conditions;

    public RandomSpawnRule(String id, NamespacedKey mob, Set<String> worlds, Set<String> biomes,
                           Set<String> replaces, int minY, int maxY, int minLight, int maxLight,
                           boolean day, boolean night, double chance, int level, int perWorldBudget,
                           List<CompiledCondition> conditions) {
        this.id = id;
        this.mob = mob;
        this.worlds = Set.copyOf(worlds);
        this.biomes = Set.copyOf(biomes);
        this.replaces = Set.copyOf(replaces);
        this.minY = minY;
        this.maxY = maxY;
        this.minLight = minLight;
        this.maxLight = maxLight;
        this.day = day;
        this.night = night;
        this.chance = chance;
        this.level = Math.max(1, level);
        this.perWorldBudget = Math.max(1, perWorldBudget);
        this.conditions = List.copyOf(conditions);
    }

    public String id() {
        return id;
    }

    public NamespacedKey mob() {
        return mob;
    }

    /** Empty means "the entity types this rule replaces are unrestricted". */
    public Set<String> replaces() {
        return replaces;
    }

    public double chance() {
        return chance;
    }

    public int level() {
        return level;
    }

    public int perWorldBudget() {
        return perWorldBudget;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    public boolean matches(Location location, Biome biome, boolean isDay, int light, String replacedType) {
        if (!worlds.isEmpty() && location.getWorld() != null
                && !worlds.contains(location.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!biomes.isEmpty() && (biome == null
                || !biomes.contains(biome.getKey().getKey().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (!replaces.isEmpty() && (replacedType == null
                || !replaces.contains(replacedType.toLowerCase(Locale.ROOT)))) {
            return false;
        }
        int y = location.getBlockY();
        if (y < minY || y > maxY) {
            return false;
        }
        if (light < minLight || light > maxLight) {
            return false;
        }
        return isDay ? day : night;
    }

    @Override
    public String toString() {
        return id + " -> " + mob;
    }
}
