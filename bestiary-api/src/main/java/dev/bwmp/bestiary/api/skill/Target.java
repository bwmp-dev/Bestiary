package dev.bwmp.bestiary.api.skill;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * One resolved target: an entity, or a bare location.
 * <p>
 * Both kinds carry a location, because a mechanic that only cares about
 * "where" should not have to branch on which targeter produced its input. An
 * entity target's location is read live, so a mechanic that runs a tick after
 * resolution sees where the entity is now rather than where it was.
 */
public final class Target {

    private final Entity entity;
    private final Location location;

    private Target(Entity entity, Location location) {
        this.entity = entity;
        this.location = location;
    }

    public static Target of(Entity entity) {
        return new Target(Objects.requireNonNull(entity, "entity"), null);
    }

    public static Target of(Location location) {
        return new Target(null, Objects.requireNonNull(location, "location").clone());
    }

    /** Null for a location target. */
    public Entity entity() {
        return entity;
    }

    /** Null unless this target is an entity that is also living. */
    public LivingEntity living() {
        return entity instanceof LivingEntity ? (LivingEntity) entity : null;
    }

    /** Null unless this target is a player. */
    public Player player() {
        return entity instanceof Player ? (Player) entity : null;
    }

    /** Never null. Read live for entity targets. */
    public Location location() {
        return entity != null ? entity.getLocation() : location.clone();
    }

    /** The eye location for living entities, the plain location otherwise. */
    public Location eyeLocation() {
        LivingEntity living = living();
        return living != null ? living.getEyeLocation() : location();
    }

    public boolean isEntity() {
        return entity != null;
    }

    public boolean isLiving() {
        return entity instanceof LivingEntity;
    }

    /** True when the entity behind this target has since died or been removed. */
    public boolean isStale() {
        return entity != null && !entity.isValid();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Target)) {
            return false;
        }
        Target target = (Target) other;
        return entity != null
                ? entity.equals(target.entity)
                : target.entity == null && location.equals(target.location);
    }

    @Override
    public int hashCode() {
        return entity != null ? entity.hashCode() : location.hashCode();
    }

    @Override
    public String toString() {
        if (entity != null) {
            return "Target(" + entity.getType() + " " + entity.getUniqueId() + ")";
        }
        return "Target(" + location.getWorld().getName() + " "
                + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()) + ")";
    }
}
