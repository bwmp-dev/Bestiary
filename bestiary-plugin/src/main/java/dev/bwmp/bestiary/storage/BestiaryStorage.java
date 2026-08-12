package dev.bwmp.bestiary.storage;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.config.BestiarySettings;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.spawn.AnchorRecord;
import dev.bwmp.bestiary.util.Json;
import dev.bwmp.keystone.storage.Database;
import dev.bwmp.keystone.storage.Migration;
import org.bukkit.NamespacedKey;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite by default, MariaDB opt-in, matching AetherCore.
 * <p>
 * Only {@code anchors} and {@code spawners} are authoritative — losing
 * {@code mob_state} costs an in-progress fight, not a world. Anchor cooldowns
 * are written on kill rather than on a timer, so a crash cannot hand out a free
 * respawn.
 * <p>
 * Every call into Keystone's {@code Database} blocks, and nothing there hops
 * threads on your behalf. So every write here goes through
 * {@code scheduler.async}, and the one blocking read — startup — happens before
 * anything is ticking.
 */
public final class BestiaryStorage {

    private final Engine engine;
    private Database database;
    private volatile boolean ready;

    public BestiaryStorage(Engine engine) {
        this.engine = engine;
    }

    public void open() {
        BestiarySettings settings = engine.settings();
        try {
            database = settings.storageType().equals("mysql") || settings.storageType().equals("mariadb")
                    ? Database.mysql(engine.plugin(), settings.storageHost(), settings.storagePort(),
                    settings.storageDatabase(), settings.storageUser(), settings.storagePassword())
                    : Database.sqlite(engine.plugin(), settings.storageFile());
            database.migrate(migrations());
            ready = true;
        } catch (SQLException exception) {
            engine.logger().log(Level.SEVERE,
                    "Storage unavailable; anchors and statistics will not persist this session", exception);
            ready = false;
        }
    }

    private List<Migration> migrations() {
        return List.of(
                Migration.of(1, "anchors, spawners, mob state, globals and kills", (connection, dialect) -> {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS bestiary_anchors ("
                                + "id VARCHAR(160) NOT NULL PRIMARY KEY,"
                                + "world VARCHAR(64) NOT NULL,"
                                + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                                + "mob VARCHAR(128) NOT NULL,"
                                + "level INTEGER NOT NULL,"
                                + "last_kill BIGINT NOT NULL,"
                                + "current_mob VARCHAR(40))");
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS bestiary_spawners ("
                                + "id VARCHAR(160) NOT NULL PRIMARY KEY,"
                                + "world VARCHAR(64) NOT NULL,"
                                + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,"
                                + "mob VARCHAR(128) NOT NULL,"
                                + "level INTEGER NOT NULL,"
                                + "radius DOUBLE NOT NULL,"
                                + "cooldown BIGINT NOT NULL,"
                                + "activation DOUBLE NOT NULL,"
                                + "max_concurrent INTEGER NOT NULL,"
                                + "amount INTEGER NOT NULL,"
                                + "enabled INTEGER NOT NULL,"
                                + "last_spawn BIGINT NOT NULL)");
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS bestiary_mob_state ("
                                + "uuid VARCHAR(40) NOT NULL PRIMARY KEY,"
                                + "mob VARCHAR(128) NOT NULL,"
                                + "vars TEXT,"
                                + "threat TEXT,"
                                + "damage TEXT,"
                                + "phase VARCHAR(64),"
                                + "updated BIGINT NOT NULL)");
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS bestiary_globals ("
                                + "name VARCHAR(128) NOT NULL PRIMARY KEY,"
                                + "value TEXT)");
                        statement.executeUpdate("CREATE TABLE IF NOT EXISTS bestiary_kills ("
                                + "player VARCHAR(40) NOT NULL,"
                                + "mob VARCHAR(128) NOT NULL,"
                                + "count INTEGER NOT NULL,"
                                + "last_kill BIGINT NOT NULL,"
                                + "PRIMARY KEY (player, mob))");
                    }
                }));
    }

    public void close() {
        if (database != null) {
            database.close();
        }
        ready = false;
    }

    public boolean ready() {
        return ready;
    }

    // --- anchors ----------------------------------------------------------

    public List<AnchorRecord> loadAnchors() {
        if (!ready) {
            return List.of();
        }
        try {
            return database.query("SELECT id, world, x, y, z, mob, level, last_kill, current_mob "
                            + "FROM bestiary_anchors",
                    results -> new AnchorRecord(
                            results.getString(1), results.getString(2),
                            results.getDouble(3), results.getDouble(4), results.getDouble(5),
                            MobManager.parseKey(results.getString(6)), results.getInt(7),
                            results.getLong(8), parseUuid(results.getString(9))));
        } catch (SQLException exception) {
            engine.logger().log(Level.WARNING, "Failed to load anchors", exception);
            return List.of();
        }
    }

    public void saveAnchor(AnchorRecord anchor) {
        run(() -> database.update(
                "REPLACE INTO bestiary_anchors (id, world, x, y, z, mob, level, last_kill, current_mob) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                anchor.id(), anchor.world(), anchor.x(), anchor.y(), anchor.z(),
                anchor.mob().toString(), anchor.level(), anchor.lastKillMillis(),
                anchor.currentMob() == null ? null : anchor.currentMob().toString()));
    }

    public void deleteAnchor(String id) {
        run(() -> database.update("DELETE FROM bestiary_anchors WHERE id = ?", id));
    }

    // --- spawner state ----------------------------------------------------

    public Map<String, Long> loadSpawnerCooldowns() {
        if (!ready) {
            return Map.of();
        }
        try {
            Map<String, Long> cooldowns = new HashMap<>();
            database.query("SELECT id, last_spawn FROM bestiary_spawners", results -> {
                cooldowns.put(results.getString(1), results.getLong(2));
                return null;
            });
            return cooldowns;
        } catch (SQLException exception) {
            engine.logger().log(Level.WARNING, "Failed to load spawner state", exception);
            return Map.of();
        }
    }

    public void saveSpawnerCooldown(String id, long lastSpawnMillis) {
        run(() -> database.update(
                "UPDATE bestiary_spawners SET last_spawn = ? WHERE id = ?", lastSpawnMillis, id));
    }

    public void saveSpawner(SpawnerRow row) {
        run(() -> database.update(
                "REPLACE INTO bestiary_spawners (id, world, x, y, z, mob, level, radius, cooldown, "
                        + "activation, max_concurrent, amount, enabled, last_spawn) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.id, row.world, row.x, row.y, row.z, row.mob, row.level, row.radius,
                row.cooldownTicks, row.activationRange, row.maxConcurrent, row.amount,
                row.enabled ? 1 : 0, row.lastSpawnMillis));
    }

    public List<SpawnerRow> loadSpawners() {
        if (!ready) {
            return List.of();
        }
        try {
            return database.query("SELECT id, world, x, y, z, mob, level, radius, cooldown, activation, "
                            + "max_concurrent, amount, enabled, last_spawn FROM bestiary_spawners",
                    results -> {
                        SpawnerRow row = new SpawnerRow();
                        row.id = results.getString(1);
                        row.world = results.getString(2);
                        row.x = results.getDouble(3);
                        row.y = results.getDouble(4);
                        row.z = results.getDouble(5);
                        row.mob = results.getString(6);
                        row.level = results.getInt(7);
                        row.radius = results.getDouble(8);
                        row.cooldownTicks = results.getLong(9);
                        row.activationRange = results.getDouble(10);
                        row.maxConcurrent = results.getInt(11);
                        row.amount = results.getInt(12);
                        row.enabled = results.getInt(13) != 0;
                        row.lastSpawnMillis = results.getLong(14);
                        return row;
                    });
        } catch (SQLException exception) {
            engine.logger().log(Level.WARNING, "Failed to load spawners", exception);
            return List.of();
        }
    }

    public void deleteSpawner(String id) {
        run(() -> database.update("DELETE FROM bestiary_spawners WHERE id = ?", id));
    }

    // --- mob state --------------------------------------------------------

    public void saveMobState(MobInstance instance) {
        String uuid = instance.uniqueId().toString();
        String mob = instance.definition().id().toString();
        String vars = Json.write(instance.variables());
        String threat = instance.threatTable() == null ? "" : encodeDoubles(instance.threatTable().snapshot());
        String damage = encodeDoubles(instance.ledger().snapshot());
        String phase = instance.phase();
        run(() -> database.update(
                "REPLACE INTO bestiary_mob_state (uuid, mob, vars, threat, damage, phase, updated) "
                        + "VALUES (?,?,?,?,?,?,?)",
                uuid, mob, vars, threat, damage, phase, System.currentTimeMillis()));
    }

    public void restoreMobState(MobInstance instance) {
        if (!ready) {
            return;
        }
        try {
            database.queryOne("SELECT vars, threat, damage, phase FROM bestiary_mob_state WHERE uuid = ?",
                    results -> {
                        instance.variables().putAll(Json.read(results.getString(1)));
                        if (instance.threatTable() != null) {
                            instance.threatTable().restore(decodeDoubles(results.getString(2)));
                        }
                        instance.ledger().restore(decodeDoubles(results.getString(3)));
                        return null;
                    }, instance.uniqueId().toString());
        } catch (SQLException exception) {
            engine.logger().log(Level.WARNING, "Failed to restore mob state", exception);
        }
    }

    public void deleteMobState(UUID uuid) {
        run(() -> database.update("DELETE FROM bestiary_mob_state WHERE uuid = ?", uuid.toString()));
    }

    /** Rows older than a day belong to mobs that no longer exist. */
    public void purgeStaleMobState() {
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        run(() -> database.update("DELETE FROM bestiary_mob_state WHERE updated < ?", cutoff));
    }

    // --- globals ----------------------------------------------------------

    public Map<String, Object> loadGlobals() {
        if (!ready) {
            return Map.of();
        }
        try {
            Map<String, Object> values = new HashMap<>();
            database.query("SELECT name, value FROM bestiary_globals", results -> {
                values.put(results.getString(1), results.getString(2));
                return null;
            });
            return values;
        } catch (SQLException exception) {
            engine.logger().log(Level.WARNING, "Failed to load global variables", exception);
            return Map.of();
        }
    }

    public void saveGlobals(Map<String, Object> values) {
        Map<String, Object> copy = new HashMap<>(values);
        run(() -> {
            for (Map.Entry<String, Object> entry : copy.entrySet()) {
                database.update("REPLACE INTO bestiary_globals (name, value) VALUES (?,?)",
                        entry.getKey(), String.valueOf(entry.getValue()));
            }
        });
    }

    // --- kills ------------------------------------------------------------

    public void recordKill(UUID player, NamespacedKey mob) {
        String playerId = player.toString();
        String mobId = mob.toString();
        long now = System.currentTimeMillis();
        run(() -> {
            int updated = database.update(
                    "UPDATE bestiary_kills SET count = count + 1, last_kill = ? WHERE player = ? AND mob = ?",
                    now, playerId, mobId);
            if (updated == 0) {
                database.update("INSERT INTO bestiary_kills (player, mob, count, last_kill) VALUES (?,?,?,?)",
                        playerId, mobId, 1, now);
            }
        });
    }

    /** Loaded once per player at join, straight into the in-memory view. */
    public void loadKills(UUID player) {
        if (!ready) {
            return;
        }
        engine.scheduler().async(() -> {
            try {
                List<String[]> rows = database.query(
                        "SELECT mob, count, last_kill FROM bestiary_kills WHERE player = ?",
                        results -> new String[]{results.getString(1),
                                Integer.toString(results.getInt(2)),
                                Long.toString(results.getLong(3))},
                        player.toString());
                for (String[] row : rows) {
                    engine.stats().seed(player, row[0], Integer.parseInt(row[1]), Long.parseLong(row[2]));
                }
            } catch (SQLException exception) {
                engine.logger().log(Level.WARNING, "Failed to load kill counts", exception);
            }
        });
    }

    // --- plumbing ---------------------------------------------------------

    private interface Work {
        void run() throws SQLException;
    }

    private void run(Work work) {
        if (!ready) {
            return;
        }
        engine.scheduler().async(() -> {
            try {
                work.run();
            } catch (SQLException exception) {
                engine.logger().log(Level.WARNING, "Storage write failed", exception);
            }
        });
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String encodeDoubles(Map<UUID, Double> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<UUID, Double> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static Map<UUID, Double> decodeDoubles(String encoded) {
        Map<UUID, Double> values = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return values;
        }
        for (String pair : encoded.split(";")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            UUID id = parseUuid(pair.substring(0, equals));
            if (id == null) {
                continue;
            }
            try {
                values.put(id, Double.parseDouble(pair.substring(equals + 1)));
            } catch (NumberFormatException ignored) {
                // A corrupted row costs one threat entry, not the fight.
            }
        }
        return values;
    }

    /** A spawner as it is stored, kept separate from the compiled definition. */
    public static final class SpawnerRow {
        public String id;
        public String world;
        public double x;
        public double y;
        public double z;
        public String mob;
        public int level;
        public double radius;
        public long cooldownTicks;
        public double activationRange;
        public int maxConcurrent;
        public int amount;
        public boolean enabled;
        public long lastSpawnMillis;

        public List<String> describe() {
            List<String> lines = new ArrayList<>();
            lines.add(id + " -> " + mob + " (level " + level + ")");
            lines.add("  at " + world + " " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z));
            return lines;
        }
    }
}
