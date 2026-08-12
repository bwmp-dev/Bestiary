package dev.bwmp.bestiary.spawn;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.storage.BestiaryStorage;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Placed spawners, spawn regions and random spawns.
 * <p>
 * All three share one scan, one activation-range test and one concurrency
 * count, because they only ever differed in how a position and a mob id are
 * chosen. The per-world budget on random spawns is what keeps a filter written
 * slightly too wide from being indistinguishable from a denial of service.
 */
public final class SpawnService {

    private final Engine engine;
    private final Map<String, Long> lastSpawn = new ConcurrentHashMap<>();
    private final Map<String, List<UUID>> spawned = new ConcurrentHashMap<>();

    public SpawnService(Engine engine) {
        this.engine = engine;
    }

    public void load() {
        lastSpawn.clear();
        lastSpawn.putAll(engine.storage().loadSpawnerCooldowns());
    }

    /** Runs on the configured spawner period, not per tick. */
    public void scan() {
        var content = engine.content();
        for (SpawnerDefinition spawner : content.spawners().values()) {
            if (spawner.enabled()) {
                considerSpawner(spawner);
            }
        }
        for (SpawnRegion region : content.regions().values()) {
            considerRegion(region);
        }
    }

    private void considerSpawner(SpawnerDefinition spawner) {
        if (!offCooldown(spawner.id(), spawner.cooldownTicks())) {
            return;
        }
        Location where = spawner.location();
        if (where.getWorld() == null || !where.getChunk().isLoaded()) {
            return;
        }
        if (!playerWithin(where, spawner.activationRange())) {
            return;
        }
        if (liveCount(spawner.id()) >= spawner.maxConcurrent()) {
            return;
        }
        if (!conditionsPass(spawner.conditions(), where)) {
            return;
        }

        engine.scheduler().atLocation(where, () -> {
            List<UUID> ids = spawned.computeIfAbsent(spawner.id(), key -> new ArrayList<>());
            for (int index = 0; index < spawner.amountPerSpawn(); index++) {
                if (ids.size() >= spawner.maxConcurrent()) {
                    break;
                }
                Location point = spawner.radius() <= 0
                        ? where
                        : Shapes.randomNear(where, spawner.radius(), true);
                engine.mobs().spawn(spawner.mob(), point, spawner.level())
                        .ifPresent(mob -> ids.add(mob.uniqueId()));
            }
            markSpawned(spawner.id());
        });
    }

    private void considerRegion(SpawnRegion region) {
        if (!offCooldown(region.id(), region.cooldownTicks())) {
            return;
        }
        Location centre = region.centre();
        if (centre == null || centre.getWorld() == null) {
            return;
        }
        if (!playerWithin(centre, region.activationRange())) {
            return;
        }
        if (liveCount(region.id()) >= region.maxConcurrent()) {
            return;
        }
        if (!conditionsPass(region.conditions(), centre)) {
            return;
        }

        NamespacedKey mob = pickWeighted(region.weights());
        if (mob == null) {
            return;
        }
        Location point = region.randomPoint();
        if (point == null) {
            return;
        }
        engine.scheduler().atLocation(point, () -> {
            engine.mobs().spawn(mob, point, region.level()).ifPresent(spawnedMob ->
                    spawned.computeIfAbsent(region.id(), key -> new ArrayList<>())
                            .add(spawnedMob.uniqueId()));
            markSpawned(region.id());
        });
    }

    /**
     * Natural spawn replacement.
     *
     * @return true when the vanilla spawn was replaced and should be cancelled
     */
    public boolean replaceNaturalSpawn(LivingEntity natural) {
        List<RandomSpawnRule> rules = engine.content().randomSpawns();
        if (rules.isEmpty()) {
            return false;
        }
        Location where = natural.getLocation();
        World world = where.getWorld();
        if (world == null) {
            return false;
        }
        boolean day = world.getTime() < 13000L;
        int light = where.getBlock().getLightLevel();
        String type = natural.getType().name().toLowerCase(Locale.ROOT);

        for (RandomSpawnRule rule : rules) {
            if (!rule.matches(where, where.getBlock().getBiome(), day, light, type)) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() > rule.chance()) {
                continue;
            }
            if (countInWorld(rule.mob(), world) >= rule.perWorldBudget()) {
                continue;
            }
            if (!conditionsPass(rule.conditions(), where)) {
                continue;
            }
            return engine.mobs().spawn(rule.mob(), where, rule.level()).isPresent();
        }
        return false;
    }

    // --- helpers ----------------------------------------------------------

    private boolean offCooldown(String id, long cooldownTicks) {
        Long last = lastSpawn.get(id);
        return last == null || System.currentTimeMillis() - last >= cooldownTicks * 50L;
    }

    private void markSpawned(String id) {
        long now = System.currentTimeMillis();
        lastSpawn.put(id, now);
        engine.storage().saveSpawnerCooldown(id, now);
    }

    private int liveCount(String id) {
        List<UUID> ids = spawned.get(id);
        if (ids == null) {
            return 0;
        }
        ids.removeIf(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            return entity == null || !entity.isValid();
        });
        return ids.size();
    }

    private int countInWorld(NamespacedKey mob, World world) {
        int count = 0;
        for (MobInstance instance : engine.mobs().instances()) {
            if (instance.definition().id().equals(mob)
                    && instance.entity().getWorld().equals(world)) {
                count++;
            }
        }
        return count;
    }

    private boolean playerWithin(Location where, double range) {
        World world = where.getWorld();
        if (world == null) {
            return false;
        }
        double squared = range * range;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(where) <= squared) {
                return true;
            }
        }
        return false;
    }

    private boolean conditionsPass(List<CompiledCondition> conditions, Location where) {
        if (conditions.isEmpty()) {
            return true;
        }
        // Conditions on a spawn have no caster, so the location stands in as
        // both. Entity conditions in this slot simply never pass, which the
        // load-time slot check already warns about.
        Player nearest = nearestPlayer(where);
        if (nearest == null) {
            return false;
        }
        var execution = engine.executor().newExecution(nearest, null, where, null);
        return CompiledCondition.allPass(conditions, execution.contextForConditions(), Target.of(where));
    }

    private Player nearestPlayer(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return null;
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distanceSquared(where);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private NamespacedKey pickWeighted(Map<NamespacedKey, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0d) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (Map.Entry<NamespacedKey, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll <= 0.0d) {
                return entry.getKey();
            }
        }
        return null;
    }

    // --- editing ----------------------------------------------------------

    /** Writes a spawner row created by {@code /bestiary spawner create}. */
    public void persist(SpawnerDefinition spawner) {
        BestiaryStorage.SpawnerRow row = new BestiaryStorage.SpawnerRow();
        row.id = spawner.id();
        Location where = spawner.location();
        row.world = where.getWorld() == null ? "" : where.getWorld().getName();
        row.x = where.getX();
        row.y = where.getY();
        row.z = where.getZ();
        row.mob = spawner.mob().toString();
        row.level = spawner.level();
        row.radius = spawner.radius();
        row.cooldownTicks = spawner.cooldownTicks();
        row.activationRange = spawner.activationRange();
        row.maxConcurrent = spawner.maxConcurrent();
        row.amount = spawner.amountPerSpawn();
        row.enabled = spawner.enabled();
        row.lastSpawnMillis = lastSpawn.getOrDefault(spawner.id(), 0L);
        engine.storage().saveSpawner(row);
    }

    public void forget(String id) {
        lastSpawn.remove(id);
        spawned.remove(id);
        engine.storage().deleteSpawner(id);
    }

    /** Spawner rows kept in storage rather than in a file, for the command tree. */
    public List<BestiaryStorage.SpawnerRow> storedSpawners() {
        return engine.storage().loadSpawners();
    }
}
