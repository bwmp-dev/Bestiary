package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.SkillCompiler;
import dev.bwmp.bestiary.spawn.RandomSpawnRule;
import dev.bwmp.bestiary.spawn.SpawnRegion;
import dev.bwmp.bestiary.spawn.SpawnerDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code spawners/} entries.
 * <p>
 * One directory carries all three of the non-anchor spawn sources, told apart
 * by a {@code kind:} field. They produce the same {@code SpawnRequest} at
 * runtime, so keeping them together is what makes the difference between them a
 * matter of filtering rather than of plumbing.
 */
public final class SpawnParser {

    /** What one file's entries parsed into. */
    public static final class Result {
        public final Map<String, SpawnerDefinition> spawners = new LinkedHashMap<>();
        public final List<RandomSpawnRule> randomSpawns = new ArrayList<>();
        public final Map<String, SpawnRegion> regions = new LinkedHashMap<>();
    }

    private SpawnParser() {
    }

    public static void parse(String id, Map<String, Object> section, String source,
                             SkillCompiler compiler, Result into) {
        String location = source + " -> " + id;
        String kind = MobParser.string(section, "kind", "spawner").toLowerCase(Locale.ROOT);

        List<ConditionNode> nodes = SkillParser.parseConditions(
                SkillParser.lookup(section, "conditions"), location);
        List<CompiledCondition> conditions =
                compiler.compileConditions(nodes, location, TargetKind.ANY);

        switch (kind) {
            case "random":
            case "random_spawn":
                into.randomSpawns.add(randomSpawn(id, section, location, conditions));
                break;
            case "region":
            case "spawn_region":
                into.regions.put(id.toLowerCase(Locale.ROOT), region(id, section, location, conditions));
                break;
            case "spawner":
            default:
                into.spawners.put(id.toLowerCase(Locale.ROOT), spawner(id, section, location, conditions));
                break;
        }
    }

    private static SpawnerDefinition spawner(String id, Map<String, Object> section, String location,
                                             List<CompiledCondition> conditions) {
        NamespacedKey mob = mob(section, location);
        String worldName = MobParser.string(section, "world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ParseException(location, "world '" + worldName + "' is not loaded");
        }
        Location where = new Location(world,
                MobParser.number(section, "x", 0, location),
                MobParser.number(section, "y", 64, location),
                MobParser.number(section, "z", 0, location));

        return new SpawnerDefinition(id.toLowerCase(Locale.ROOT), mob, where,
                (int) MobParser.number(section, "level", 1, location),
                MobParser.number(section, "radius", 3, location),
                ticks(section, "cooldown", "30s", location),
                MobParser.number(section, "activation_range", 32, location),
                (int) MobParser.number(section, "max_concurrent", 4, location),
                (int) MobParser.number(section, "amount", 1, location),
                MobParser.bool(section, "enabled", true),
                conditions);
    }

    private static RandomSpawnRule randomSpawn(String id, Map<String, Object> section, String location,
                                               List<CompiledCondition> conditions) {
        NamespacedKey mob = mob(section, location);
        String time = MobParser.string(section, "time", "any").toLowerCase(Locale.ROOT);
        boolean day = !time.equals("night");
        boolean night = !time.equals("day");

        return new RandomSpawnRule(id.toLowerCase(Locale.ROOT), mob,
                lowerSet(section, "worlds"),
                lowerSet(section, "biomes"),
                lowerSet(section, "replaces"),
                (int) MobParser.number(section, "min_y", -64, location),
                (int) MobParser.number(section, "max_y", 320, location),
                (int) MobParser.number(section, "min_light", 0, location),
                (int) MobParser.number(section, "max_light", 15, location),
                day, night,
                MobParser.number(section, "chance", 0.05d, location),
                (int) MobParser.number(section, "level", 1, location),
                (int) MobParser.number(section, "budget", 40, location),
                conditions);
    }

    private static SpawnRegion region(String id, Map<String, Object> section, String location,
                                      List<CompiledCondition> conditions) {
        Map<NamespacedKey, Double> weights = new LinkedHashMap<>();
        Object rawMobs = SkillParser.lookup(section, "mobs");
        if (rawMobs instanceof Map) {
            for (Map.Entry<String, Object> entry : SkillParser.asMap(rawMobs).entrySet()) {
                NamespacedKey key = MobManager.parseKey(entry.getKey());
                if (key == null) {
                    throw new ParseException(location, "'" + entry.getKey() + "' is not a mob id");
                }
                weights.put(key, Double.parseDouble(String.valueOf(entry.getValue())));
            }
        } else if (rawMobs instanceof List) {
            for (Object entry : (List<?>) rawMobs) {
                NamespacedKey key = MobManager.parseKey(String.valueOf(entry));
                if (key != null) {
                    weights.put(key, 1.0d);
                }
            }
        }
        if (weights.isEmpty()) {
            throw new ParseException(location, "a spawn region needs a 'mobs' map or list");
        }

        return new SpawnRegion(id.toLowerCase(Locale.ROOT),
                MobParser.string(section, "world", ""),
                (int) MobParser.number(section, "min_x", 0, location),
                (int) MobParser.number(section, "min_y", 0, location),
                (int) MobParser.number(section, "min_z", 0, location),
                (int) MobParser.number(section, "max_x", 0, location),
                (int) MobParser.number(section, "max_y", 0, location),
                (int) MobParser.number(section, "max_z", 0, location),
                weights,
                (int) MobParser.number(section, "max_concurrent", 6, location),
                ticks(section, "cooldown", "30s", location),
                MobParser.number(section, "activation_range", 48, location),
                (int) MobParser.number(section, "level", 1, location),
                conditions);
    }

    private static NamespacedKey mob(Map<String, Object> section, String location) {
        Object raw = SkillParser.lookup(section, "mob");
        if (raw == null) {
            raw = SkillParser.lookup(section, "type");
        }
        NamespacedKey key = raw == null ? null : MobManager.parseKey(String.valueOf(raw));
        if (key == null) {
            throw new ParseException(location, "entry has no 'mob'");
        }
        return key;
    }

    private static long ticks(Map<String, Object> section, String key, String fallback, String location) {
        String written = MobParser.string(section, key, fallback);
        try {
            return Durations.parseTicks(written);
        } catch (IllegalArgumentException exception) {
            throw new ParseException(location, "'" + key + "': " + exception.getMessage());
        }
    }

    private static Set<String> lowerSet(Map<String, Object> section, String key) {
        Object raw = SkillParser.lookup(section, key);
        Set<String> values = new HashSet<>();
        if (raw instanceof List) {
            for (Object element : (List<?>) raw) {
                values.add(String.valueOf(element).trim().toLowerCase(Locale.ROOT));
            }
        } else if (raw != null) {
            for (String part : String.valueOf(raw).split(",")) {
                if (!part.isBlank()) {
                    values.add(part.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return values;
    }
}
