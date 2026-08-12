package dev.bwmp.bestiary.stats;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kill counts and last-kill times, held in memory alongside the writes to
 * storage.
 * <p>
 * <b>Every placeholder must be answerable without a database round-trip.</b>
 * PlaceholderAPI resolves on the main thread, often once per player per tick
 * for a TAB header, so a query on demand would put SQLite on the tick loop.
 * This is the view those placeholders read; storage is written asynchronously
 * behind it.
 */
public final class StatsView {

    private static final class PlayerStats {
        private final Map<String, Integer> kills = new ConcurrentHashMap<>();
        private final Map<String, Long> lastKill = new ConcurrentHashMap<>();
        private volatile int total;
    }

    private final Map<UUID, PlayerStats> byPlayer = new ConcurrentHashMap<>();

    public void recordKill(UUID player, NamespacedKey mob) {
        PlayerStats stats = byPlayer.computeIfAbsent(player, id -> new PlayerStats());
        stats.kills.merge(mob.toString(), 1, Integer::sum);
        stats.lastKill.put(mob.toString(), System.currentTimeMillis());
        synchronized (stats) {
            stats.total++;
        }
    }

    /** Used when storage loads a player's history at join. */
    public void seed(UUID player, String mob, int kills, long lastKillMillis) {
        PlayerStats stats = byPlayer.computeIfAbsent(player, id -> new PlayerStats());
        stats.kills.put(mob, kills);
        if (lastKillMillis > 0) {
            stats.lastKill.put(mob, lastKillMillis);
        }
        synchronized (stats) {
            stats.total = stats.kills.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public int kills(Player player, NamespacedKey mob) {
        PlayerStats stats = byPlayer.get(player.getUniqueId());
        return stats == null ? 0 : stats.kills.getOrDefault(mob.toString(), 0);
    }

    public int total(Player player) {
        PlayerStats stats = byPlayer.get(player.getUniqueId());
        return stats == null ? 0 : stats.total;
    }

    /** Milliseconds since the last kill of that mob, or -1. */
    public long sinceLastKill(Player player, NamespacedKey mob) {
        PlayerStats stats = byPlayer.get(player.getUniqueId());
        if (stats == null) {
            return -1L;
        }
        Long last = stats.lastKill.get(mob.toString());
        return last == null ? -1L : System.currentTimeMillis() - last;
    }

    public Map<String, Integer> allKills(UUID player) {
        PlayerStats stats = byPlayer.get(player);
        return stats == null ? Map.of() : Map.copyOf(stats.kills);
    }

    public void forget(UUID player) {
        byPlayer.remove(player);
    }
}
