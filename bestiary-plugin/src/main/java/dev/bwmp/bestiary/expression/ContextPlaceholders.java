package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.VariableScope;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

/**
 * The {@code caster}, {@code target}, {@code trigger}, {@code origin} and
 * {@code skill} namespaces.
 * <p>
 * Everything here is read live. A skill that delays four seconds and then reads
 * {@code <target.hp.percent>} must see the health the target has now, not the
 * one it had when the line was resolved.
 */
public final class ContextPlaceholders implements PlaceholderResolver {

    private final PlaceholderServices services;

    public ContextPlaceholders(PlaceholderServices services) {
        this.services = services;
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("caster", "target", "trigger", "origin", "skill");
    }

    @Override
    public String resolve(String key, SkillContext context, Target target) {
        int dot = key.indexOf('.');
        String namespace = (dot < 0 ? key : key.substring(0, dot)).toLowerCase(Locale.ROOT);
        String path = dot < 0 ? "" : key.substring(dot + 1).toLowerCase(Locale.ROOT);

        switch (namespace) {
            case "caster":
                return context == null ? null : entity(context.caster(), path, context, target);
            case "target":
                return target == null ? null : entity(target.entity(), path, context, target,
                        target.location());
            case "trigger":
                return context == null ? null : entity(context.trigger(), path, context, target);
            case "origin":
                return context == null ? null : location(context.origin(), path);
            case "skill":
                return skill(path, context);
            default:
                return null;
        }
    }

    private String skill(String path, SkillContext context) {
        if (context == null) {
            return null;
        }
        if (path.equals("id")) {
            return context.skillId();
        }
        if (path.equals("power")) {
            return number(context.power());
        }
        if (path.equals("depth")) {
            return Integer.toString(context.depth());
        }
        if (path.equals("targets")) {
            return Integer.toString(context.targets().size());
        }
        if (path.startsWith("var.")) {
            Object value = context.variable(VariableScope.SKILL, path.substring(4));
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private String entity(Entity entity, String path, SkillContext context, Target target) {
        return entity(entity, path, context, target, entity == null ? null : entity.getLocation());
    }

    private String entity(Entity entity, String path, SkillContext context, Target target, Location fallback) {
        if (entity == null) {
            // A location target has no entity, but its position is still a fair
            // answer to <target.x>. Anything entity-specific stays unresolved.
            return fallback == null ? null : location(fallback, path);
        }

        if (path.startsWith("var.")) {
            BestiaryMob mob = services.mobOf(entity);
            if (mob == null) {
                return null;
            }
            Object value = mob.variables().get(path.substring(4));
            return value == null ? null : String.valueOf(value);
        }

        LivingEntity living = entity instanceof LivingEntity ? (LivingEntity) entity : null;
        BestiaryMob mob = services.mobOf(entity);

        switch (path) {
            case "name":
                return plainName(entity);
            case "displayname":
            case "display":
                return mob != null && !mob.definition().display().isEmpty()
                        ? mob.definition().display()
                        : plainName(entity);
            case "uuid":
                return entity.getUniqueId().toString();
            case "type":
                return entity.getType().name().toLowerCase(Locale.ROOT);
            case "id":
                return mob == null ? null : mob.definition().id().toString();
            case "level":
                return mob == null ? "1" : Integer.toString(mob.level());
            case "phase":
                return mob == null ? "" : mob.phase();
            case "faction":
                return mob == null ? "" : mob.definition().faction();
            case "hp":
            case "health":
                return living == null ? null : number(living.getHealth());
            case "maxhp":
            case "maxhealth":
                return living == null ? null : number(maxHealth(living));
            case "hp.percent":
            case "health.percent":
                if (living == null) {
                    return null;
                }
                double max = maxHealth(living);
                return number(max <= 0 ? 0 : living.getHealth() / max * 100.0d);
            case "hp.missing":
            case "health.missing":
                return living == null ? null : number(maxHealth(living) - living.getHealth());
            case "armor":
                return living == null ? null : number(attribute(living, "GENERIC_ARMOR"));
            case "air":
                return living == null ? null : Integer.toString(living.getRemainingAir());
            case "food":
                return entity instanceof Player ? Integer.toString(((Player) entity).getFoodLevel()) : null;
            case "exp":
                return entity instanceof Player ? Integer.toString(((Player) entity).getTotalExperience()) : null;
            case "distance":
                if (context == null || context.caster() == null
                        || !entity.getWorld().equals(context.caster().getWorld())) {
                    return null;
                }
                return number(entity.getLocation().distance(context.caster().getLocation()));
            case "threat":
                if (mob == null || !(entity instanceof Player)) {
                    return null;
                }
                return number(mob.threat((Player) entity).orElse(0.0d));
            default:
                return location(entity.getLocation(), path);
        }
    }

    private String location(Location location, String path) {
        if (location == null) {
            return null;
        }
        switch (path) {
            case "x":
                return number(location.getX());
            case "y":
                return number(location.getY());
            case "z":
                return number(location.getZ());
            case "bx":
                return Integer.toString(location.getBlockX());
            case "by":
                return Integer.toString(location.getBlockY());
            case "bz":
                return Integer.toString(location.getBlockZ());
            case "yaw":
                return number(location.getYaw());
            case "pitch":
                return number(location.getPitch());
            case "world":
                return location.getWorld() == null ? null : location.getWorld().getName();
            case "biome":
                return location.getWorld() == null ? null
                        : location.getBlock().getBiome().name().toLowerCase(Locale.ROOT);
            default:
                return null;
        }
    }

    private String plainName(Entity entity) {
        String custom = entity.getCustomName();
        if (custom != null && !custom.isEmpty()) {
            return services.plainText(custom);
        }
        if (entity instanceof Player) {
            return entity.getName();
        }
        return entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Looked up by name rather than by static field, because the
     * {@code Attribute} constants were renamed at 1.20.5 and referencing one
     * directly would not link on half the supported band.
     */
    static double maxHealth(LivingEntity living) {
        double value = attribute(living, "GENERIC_MAX_HEALTH");
        return value > 0 ? value : living.getHealth();
    }

    static double attribute(LivingEntity living, String legacyName) {
        Attribute attribute = Attributes.byLegacyName(legacyName);
        if (attribute == null) {
            return 0.0d;
        }
        var instance = living.getAttribute(attribute);
        return instance == null ? 0.0d : instance.getValue();
    }

    private static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
