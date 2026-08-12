package dev.bwmp.bestiary.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.Locale;
import java.util.UUID;

/**
 * Where a boss belongs, persisted independently of whether it currently exists.
 * <p>
 * That separation is the whole point: worldgen decides the position once and
 * immutably, the runtime decides existence cheaply and
 * repeatedly. It is also what makes every removal Bestiary did not intend —
 * peaceful difficulty, a fall into the void, another plugin's {@code remove()}
 * — self-healing on the next player visit.
 */
public final class AnchorRecord {

    private final String id;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final NamespacedKey mob;
    private final int level;
    private volatile long lastKillMillis;
    private volatile UUID currentMob;

    public AnchorRecord(String id, String world, double x, double y, double z,
                        NamespacedKey mob, int level, long lastKillMillis, UUID currentMob) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mob = mob;
        this.level = Math.max(1, level);
        this.lastKillMillis = lastKillMillis;
        this.currentMob = currentMob;
    }

    /** Derived from the position, so re-adopting the same marker is idempotent. */
    public static String idFor(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName().toLowerCase(Locale.ROOT))
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    public NamespacedKey mob() {
        return mob;
    }

    public int level() {
        return level;
    }

    public long lastKillMillis() {
        return lastKillMillis;
    }

    public void lastKillMillis(long value) {
        this.lastKillMillis = value;
    }

    public UUID currentMob() {
        return currentMob;
    }

    public void currentMob(UUID value) {
        this.currentMob = value;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /** Null when the world is not loaded, which is a normal state, not an error. */
    public Location location() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public boolean offCooldown(long cooldownMillis) {
        return lastKillMillis <= 0L || System.currentTimeMillis() - lastKillMillis >= cooldownMillis;
    }

    public long remainingCooldownMillis(long cooldownMillis) {
        if (lastKillMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, cooldownMillis - (System.currentTimeMillis() - lastKillMillis));
    }

    @Override
    public String toString() {
        return id + " -> " + mob;
    }
}
