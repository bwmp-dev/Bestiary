package dev.bwmp.bestiary.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every point of damage dealt to one mob, per player, for the fight's lifetime.
 * <p>
 * This is what drop tables need for {@code damage_share}, and what makes "the
 * person who tagged it first" not automatically the person who gets the loot.
 */
public final class DamageLedger {

    private final Map<UUID, Double> byPlayer = new ConcurrentHashMap<>();
    private volatile double total;

    public void record(Player player, double damage) {
        if (player == null || damage <= 0.0d) {
            return;
        }
        byPlayer.merge(player.getUniqueId(), damage, Double::sum);
        synchronized (this) {
            total += damage;
        }
    }

    public double dealtBy(Player player) {
        return player == null ? 0.0d : byPlayer.getOrDefault(player.getUniqueId(), 0.0d);
    }

    public double total() {
        return total;
    }

    public double shareOf(Player player) {
        double sum = total;
        return sum <= 0.0d ? 0.0d : dealtBy(player) / sum;
    }

    /** Everyone who dealt damage and is still online, most damage first. */
    public List<Player> contributors() {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(byPlayer.entrySet());
        entries.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> entry) -> entry.getValue()).reversed());
        List<Player> players = new ArrayList<>(entries.size());
        for (Map.Entry<UUID, Double> entry : entries) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public Map<UUID, Double> snapshot() {
        return Map.copyOf(byPlayer);
    }

    public void restore(Map<UUID, Double> saved) {
        byPlayer.clear();
        byPlayer.putAll(saved);
        synchronized (this) {
            total = saved.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }
}
