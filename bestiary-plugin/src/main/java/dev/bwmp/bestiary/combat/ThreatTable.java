package dev.bwmp.bestiary.combat;

import dev.bwmp.bestiary.api.mob.ThreatSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One mob's threat table.
 * <p>
 * Reading target selection from here rather than from vanilla's last-attacker
 * heuristic is what makes a multi-player boss fight a fight rather than a race
 * to hit it first.
 * <p>
 * {@link ThreatSettings#switchThreshold()} is the detail that matters in
 * practice: without hysteresis a boss flickers between two players on alternate
 * hits, which looks like a bug and plays like one.
 */
public final class ThreatTable {

    private final ThreatSettings settings;
    private final Map<UUID, Double> threat = new ConcurrentHashMap<>();
    private volatile UUID current;
    private volatile long lastDecay = System.currentTimeMillis();

    public ThreatTable(ThreatSettings settings) {
        this.settings = settings;
    }

    public void add(Player player, double amount) {
        if (player == null || amount == 0.0d) {
            return;
        }
        threat.merge(player.getUniqueId(), amount, Double::sum);
    }

    public void addDamage(Player player, double damage) {
        add(player, damage * settings.damageFactor());
    }

    public void addHealing(Player player, double healed) {
        add(player, healed * settings.healingFactor());
    }

    public void multiply(Player player, double factor) {
        threat.computeIfPresent(player.getUniqueId(), (id, value) -> value * factor);
    }

    public void taunt(Player player) {
        double top = threat.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
        threat.put(player.getUniqueId(), Math.max(top * settings.tauntMultiplier(), 1.0d));
        current = player.getUniqueId();
    }

    public void clear(Player player) {
        threat.remove(player.getUniqueId());
        if (player.getUniqueId().equals(current)) {
            current = null;
        }
    }

    public void clearAll() {
        threat.clear();
        current = null;
    }

    public double of(Player player) {
        return threat.getOrDefault(player.getUniqueId(), 0.0d);
    }

    public boolean isEmpty() {
        return threat.isEmpty();
    }

    public void decay() {
        long now = System.currentTimeMillis();
        double seconds = (now - lastDecay) / 1000.0d;
        lastDecay = now;
        if (settings.decayPerSecond() <= 0.0d || seconds <= 0.0d) {
            return;
        }
        double retained = Math.pow(1.0d - Math.min(0.99d, settings.decayPerSecond()), seconds);
        threat.replaceAll((id, value) -> value * retained);
        threat.entrySet().removeIf(entry -> entry.getValue() < 0.01d);
    }

    public void prune(LivingEntity mob, double range) {
        threat.keySet().removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isValid() || player.isDead()) {
                return true;
            }
            if (!player.getWorld().equals(mob.getWorld())) {
                return true;
            }
            return player.getLocation().distanceSquared(mob.getLocation()) > range * range;
        });
        if (current != null && !threat.containsKey(current)) {
            current = null;
        }
    }

    /**
     * The player the mob should be attacking, or null.
     * <p>
     * The current holder keeps aggro until a challenger exceeds it by
     * {@code switchThreshold}, which is the hysteresis.
     */
    public Player select() {
        UUID leader = null;
        double best = 0.0d;
        for (Map.Entry<UUID, Double> entry : threat.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                leader = entry.getKey();
            }
        }
        if (leader == null) {
            current = null;
            return null;
        }
        if (current != null && !current.equals(leader)) {
            double held = threat.getOrDefault(current, 0.0d);
            if (best < held * settings.switchThreshold()) {
                leader = current;
            }
        }
        current = leader;
        Player player = Bukkit.getPlayer(leader);
        if (player == null || !player.isValid()) {
            threat.remove(leader);
            current = null;
            return null;
        }
        return player;
    }

    public List<Player> ranked() {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(threat.entrySet());
        entries.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> entry) -> entry.getValue()).reversed());
        List<Player> players = new ArrayList<>(entries.size());
        for (Map.Entry<UUID, Double> entry : entries) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isValid()) {
                players.add(player);
            }
        }
        return players;
    }

    public Map<UUID, Double> snapshot() {
        return Map.copyOf(threat);
    }

    public void restore(Map<UUID, Double> saved) {
        threat.clear();
        threat.putAll(saved);
    }
}
