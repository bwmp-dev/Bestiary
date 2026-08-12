package dev.bwmp.bestiary.combat;

import org.bukkit.entity.Entity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named, timed immunity windows applied by the {@code immunity} mechanic.
 * <p>
 * This is how a boss becomes briefly unstunnable after a phase transition
 * without hard-coding it: the transition skill applies
 * {@code immunity{table=knockback;duration=3s}}, and the knockback mechanic
 * asks here before doing anything.
 */
public final class ImmunityService {

    private final Map<UUID, Map<String, Long>> windows = new ConcurrentHashMap<>();

    public void grant(Entity entity, String table, long durationTicks) {
        if (entity == null || table == null || table.isBlank()) {
            return;
        }
        long expiry = System.currentTimeMillis() + durationTicks * 50L;
        windows.computeIfAbsent(entity.getUniqueId(), id -> new ConcurrentHashMap<>())
                .merge(key(table), expiry, Math::max);
    }

    public boolean immune(Entity entity, String table) {
        if (entity == null || table == null) {
            return false;
        }
        Map<String, Long> tables = windows.get(entity.getUniqueId());
        if (tables == null) {
            return false;
        }
        Long expiry = tables.get(key(table));
        if (expiry == null) {
            return false;
        }
        if (expiry <= System.currentTimeMillis()) {
            tables.remove(key(table));
            return false;
        }
        return true;
    }

    public void revoke(Entity entity, String table) {
        Map<String, Long> tables = windows.get(entity.getUniqueId());
        if (tables != null) {
            tables.remove(key(table));
        }
    }

    public void forget(Entity entity) {
        windows.remove(entity.getUniqueId());
    }

    /** Keeps the map from growing for the lifetime of the server. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        windows.values().forEach(tables -> tables.values().removeIf(expiry -> expiry <= now));
        windows.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static String key(String table) {
        return table.trim().toLowerCase(Locale.ROOT);
    }
}
