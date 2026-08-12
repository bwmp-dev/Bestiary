package dev.bwmp.bestiary.spawn;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure anchors: where a boss belongs, decoupled from whether it exists.
 * <p>
 * A world generator places an anchor entity; Bestiary adopts it on chunk load,
 * records the position, deletes the entity, and from then on spawns the boss on
 * player proximity with a persisted respawn cooldown. Nothing spawns in the
 * thousands of temples nobody has visited.
 * <p>
 * Identity is read from scoreboard tags first and from the entity type second.
 * Tags are the better format and the type map is the fallback, because a
 * TerraScript {@code entity()} call carrying NBT most likely spawns nothing at
 * all — so the design works whichever way that check comes out.
 */
public final class AnchorService {

    private final Engine engine;
    private final Map<String, AnchorRecord> anchors = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, List<AnchorRecord>>> index = new ConcurrentHashMap<>();

    public AnchorService(Engine engine) {
        this.engine = engine;
    }

    public void load() {
        anchors.clear();
        index.clear();
        for (AnchorRecord record : engine.storage().loadAnchors()) {
            if (record.mob() == null) {
                continue;
            }
            anchors.put(record.id(), record);
            indexAnchor(record);
        }
        engine.logger().info("Loaded " + anchors.size() + " structure anchor(s).");
    }

    private void indexAnchor(AnchorRecord record) {
        index.computeIfAbsent(record.world().toLowerCase(Locale.ROOT), world -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey((int) record.x() >> 4, (int) record.z() >> 4),
                        key -> new ArrayList<>())
                .add(record);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    // --- adoption ---------------------------------------------------------

    /**
     * Turns an anchor entity into a persisted anchor and deletes it.
     *
     * @return true when the entity was an anchor, so the caller knows not to
     *         treat it as an ordinary entity afterwards
     */
    public boolean adoptEntity(Entity entity) {
        NamespacedKey mob = identify(entity);
        if (mob == null) {
            return false;
        }
        if (engine.content().compiledMob(mob) == null) {
            engine.logger().warning("Anchor at " + entity.getLocation() + " names unknown mob '" + mob + "'.");
            return false;
        }

        Location location = entity.getLocation();
        String id = AnchorRecord.idFor(location);
        entity.remove();

        if (anchors.containsKey(id)) {
            return true;
        }
        AnchorRecord record = new AnchorRecord(id,
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                mob, 1, 0L, null);
        anchors.put(id, record);
        indexAnchor(record);
        engine.storage().saveAnchor(record);
        return true;
    }

    /** Tags first, entity type second. */
    private NamespacedKey identify(Entity entity) {
        String prefix = engine.settings().anchorTagPrefix();
        for (String tag : entity.getScoreboardTags()) {
            if (tag.regionMatches(true, 0, prefix, 0, prefix.length())) {
                NamespacedKey parsed = dev.bwmp.bestiary.mob.MobManager
                        .parseKey(tag.substring(prefix.length()).replace('/', ':'));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return engine.settings().anchorTypes().get(entity.getType());
    }

    /** Admin-placed, for worlds that were generated before the pack change. */
    public AnchorRecord create(Location location, NamespacedKey mob, int level) {
        String id = AnchorRecord.idFor(location);
        AnchorRecord record = new AnchorRecord(id,
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), mob, level, 0L, null);
        AnchorRecord previous = anchors.put(id, record);
        if (previous != null) {
            removeFromIndex(previous);
        }
        indexAnchor(record);
        engine.storage().saveAnchor(record);
        return record;
    }

    public boolean remove(String id) {
        AnchorRecord record = anchors.remove(id);
        if (record == null) {
            return false;
        }
        removeFromIndex(record);
        engine.storage().deleteAnchor(id);
        return true;
    }

    private void removeFromIndex(AnchorRecord record) {
        Map<Long, List<AnchorRecord>> world = index.get(record.world().toLowerCase(Locale.ROOT));
        if (world == null) {
            return;
        }
        List<AnchorRecord> bucket = world.get(chunkKey((int) record.x() >> 4, (int) record.z() >> 4));
        if (bucket != null) {
            bucket.remove(record);
        }
    }

    public Collection<AnchorRecord> all() {
        return List.copyOf(anchors.values());
    }

    public Optional<AnchorRecord> byId(String id) {
        return Optional.ofNullable(anchors.get(id));
    }

    /** Clears the cooldown, so the next scan respawns immediately. */
    public boolean reset(String id) {
        AnchorRecord record = anchors.get(id);
        if (record == null) {
            return false;
        }
        record.lastKillMillis(0L);
        record.currentMob(null);
        engine.storage().saveAnchor(record);
        return true;
    }

    /** Called when an anchored mob dies: cooldown starts now, and is written now. */
    public void onKilled(String anchorId) {
        AnchorRecord record = anchors.get(anchorId);
        if (record == null) {
            return;
        }
        record.lastKillMillis(System.currentTimeMillis());
        record.currentMob(null);
        engine.storage().saveAnchor(record);
    }

    /** The mob is gone but not killed — a plugin removed it, or it fell out of the world. */
    public void forget(String anchorId) {
        AnchorRecord record = anchors.get(anchorId);
        if (record == null) {
            return;
        }
        record.currentMob(null);
        engine.storage().saveAnchor(record);
    }

    public long cooldownRemaining(String anchorId) {
        AnchorRecord record = anchors.get(anchorId);
        return record == null ? 0L
                : record.remainingCooldownMillis(engine.settings().anchorRespawnCooldownTicks() * 50L);
    }

    // --- the scan ---------------------------------------------------------

    /**
     * Runs against a spatial index of loaded anchors, on a one-second period,
     * driven by where players actually are rather than by sweeping every anchor
     * on the server.
     */
    public void scan() {
        double range = engine.settings().anchorActivationRange();
        long cooldownMillis = engine.settings().anchorRespawnCooldownTicks() * 50L;
        int chunkRadius = (int) Math.ceil(range / 16.0d);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<Long, List<AnchorRecord>> world = index.get(player.getWorld().getName().toLowerCase(Locale.ROOT));
            if (world == null || world.isEmpty()) {
                continue;
            }
            Chunk chunk = player.getLocation().getChunk();
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    List<AnchorRecord> bucket = world.get(chunkKey(chunk.getX() + dx, chunk.getZ() + dz));
                    if (bucket == null || bucket.isEmpty()) {
                        continue;
                    }
                    for (AnchorRecord record : List.copyOf(bucket)) {
                        considerSpawn(record, player, range, cooldownMillis);
                    }
                }
            }
        }
    }

    private void considerSpawn(AnchorRecord record, Player player, double range, long cooldownMillis) {
        if (!record.offCooldown(cooldownMillis)) {
            return;
        }
        UUID current = record.currentMob();
        if (current != null) {
            Entity existing = Bukkit.getEntity(current);
            if (existing != null && existing.isValid()) {
                return;
            }
            // The mob is gone and nothing told us. Self-healing is the point of
            // persisting position separately from existence.
            record.currentMob(null);
        }

        Location location = record.location();
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (player.getLocation().distanceSquared(location) > range * range) {
            return;
        }

        engine.scheduler().atLocation(location, () -> {
            Optional<BestiaryMob> spawned = engine.mobs()
                    .spawn(record.mob(), location, record.level(), record.id());
            spawned.ifPresent(mob -> {
                record.currentMob(mob.uniqueId());
                engine.storage().saveAnchor(record);
            });
        });
    }

    /** Worlds whose anchors are currently indexed, for {@code /bestiary anchor list}. */
    public List<String> worlds() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (index.containsKey(world.getName().toLowerCase(Locale.ROOT))) {
                names.add(world.getName());
            }
        }
        return names;
    }
}
