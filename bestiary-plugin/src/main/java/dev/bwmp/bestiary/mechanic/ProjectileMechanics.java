package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.util.Registries;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Projectiles.
 * <p>
 * {@code projectile} and {@code missile} are Bestiary's own: a scripted point
 * that ticks along a path, runs an {@code on_tick} skill, and fires an
 * {@code on_hit} skill against whatever it reaches. That is more useful than a
 * vanilla entity for a boss, because the impact is a skill rather than a fixed
 * amount of damage — and it costs no entity at all, which matters when a
 * pattern fires forty of them.
 */
public final class ProjectileMechanics {

    private ProjectileMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("projectile", Mechanics.type(
                MechanicMeta.builder("projectile")
                        .description("A scripted point that travels and runs skills as it goes.")
                        .requires(TargetKind.ANY)
                        .param("velocity", "blocks per tick", "1.0", "v", "speed")
                        .param("max_distance", "give up after this far", "32", "range", "md")
                        .param("hit_radius", "how close counts as a hit", "1.0", "hr", "radius")
                        .param("gravity", "downward acceleration per tick", "0", "g")
                        .param("on_tick", "skill run at the projectile each step", "")
                        .param("on_hit", "skill run against what it hits", "")
                        .param("on_end", "skill run where it stops", "")
                        .param("particle", "trail particle", "crit", "p")
                        .param("hit_players", "can hit players", "true")
                        .param("hit_mobs", "can hit other mobs", "false")
                        .param("step", "steps per tick; higher is more accurate and costlier", "2")
                        .build(),
                config -> scripted(engine, config, false)));

        into.put("missile", Mechanics.type(
                MechanicMeta.builder("missile")
                        .description("A scripted projectile that curves toward its target.")
                        .requires(TargetKind.ENTITY)
                        .param("velocity", "blocks per tick", "0.8", "v", "speed")
                        .param("max_distance", "give up after this far", "48", "range", "md")
                        .param("hit_radius", "how close counts as a hit", "1.2", "hr", "radius")
                        .param("turn_rate", "0..1; 1 turns instantly", "0.15", "tr", "homing")
                        .param("on_tick", "skill run at the projectile each step", "")
                        .param("on_hit", "skill run against what it hits", "")
                        .param("on_end", "skill run where it stops", "")
                        .param("particle", "trail particle", "flame", "p")
                        .param("hit_players", "can hit players", "true")
                        .param("hit_mobs", "can hit other mobs", "false")
                        .param("step", "steps per tick", "2")
                        .build(),
                config -> scripted(engine, config, true)));

        into.put("homing_missile", Mechanics.type(
                MechanicMeta.builder("homing_missile")
                        .description("A missile that turns hard. The same mechanic, tuned.")
                        .requires(TargetKind.ENTITY)
                        .param("velocity", "blocks per tick", "0.7", "v", "speed")
                        .param("max_distance", "give up after this far", "64", "range", "md")
                        .param("hit_radius", "how close counts as a hit", "1.2", "hr")
                        .param("turn_rate", "0..1", "0.45", "tr")
                        .param("on_tick", "skill run at the projectile each step", "")
                        .param("on_hit", "skill run against what it hits", "")
                        .param("on_end", "skill run where it stops", "")
                        .param("particle", "trail particle", "soul_fire_flame", "p")
                        .param("hit_players", "can hit players", "true")
                        .param("hit_mobs", "can hit other mobs", "false")
                        .param("step", "steps per tick", "2")
                        .build(),
                config -> scripted(engine, config, true)));

        into.put("bullet_shape", Mechanics.type(
                MechanicMeta.builder("bullet_shape")
                        .description("Fires one scripted projectile per point of a shape, outward.")
                        .requires(TargetKind.ANY)
                        .param("shape", "a nested shape block", "")
                        .param("velocity", "blocks per tick", "0.8", "v", "speed")
                        .param("max_distance", "give up after this far", "16", "range")
                        .param("hit_radius", "how close counts as a hit", "1.0", "hr")
                        .param("on_hit", "skill run against what each bullet hits", "")
                        .param("particle", "trail particle", "crit", "p")
                        .param("hit_players", "can hit players", "true")
                        .param("hit_mobs", "can hit other mobs", "false")
                        .build(),
                config -> {
                    dev.bwmp.bestiary.util.ShapeSpec shape =
                            dev.bwmp.bestiary.util.ShapeSpec.of(config.section("shape"));
                    Expression velocity = config.number("velocity", 0.8d);
                    Expression maxDistance = config.number("max_distance", 16);
                    Expression hitRadius = config.number("hit_radius", 1.0d);
                    String onHit = config.raw("on_hit", "");
                    Particle particle = PresentationMechanics.resolveParticle(config.raw("particle", "crit"));
                    boolean hitPlayers = config.bool("hit_players", true);
                    boolean hitMobs = config.bool("hit_mobs", false);
                    return (context, target) -> {
                        Location centre = target.location().add(0, 1, 0);
                        List<Location> points = shape.points(context, target, centre);
                        double speed = velocity.asDouble(context, target);
                        double range = maxDistance.asDouble(context, target);
                        double radius = hitRadius.asDouble(context, target);
                        for (Location point : points) {
                            if (!context.charge(1)) {
                                break;
                            }
                            Vector direction = point.toVector().subtract(centre.toVector());
                            if (direction.lengthSquared() < 1.0e-6d) {
                                continue;
                            }
                            launch(engine, context, centre.clone(), direction.normalize(), null,
                                    speed, range, radius, 0.0d, 0.0d, "", onHit, "", particle,
                                    hitPlayers, hitMobs, 2);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("shoot_arrow", Mechanics.type(
                MechanicMeta.builder("shoot_arrow")
                        .description("Fires a real arrow at the target.")
                        .requires(TargetKind.ANY)
                        .param("velocity", "launch speed", "2.0", "v", "speed")
                        .param("damage", "arrow damage; -1 keeps the vanilla value", "-1", "d")
                        .param("spread", "random deviation", "0", "inaccuracy")
                        .param("fire_ticks", "set the arrow alight", "0", "fire")
                        .param("pierce", "how many entities it passes through", "0")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 2.0d);
                    Expression damage = config.number("damage", -1);
                    Expression spread = config.number("spread", 0);
                    Expression fireTicks = config.number("fire_ticks", 0);
                    Expression pierce = config.number("pierce", 0);
                    return (context, target) -> {
                        Arrow arrow = spawnProjectile(context.caster(), target, Arrow.class,
                                velocity.asDouble(context, target), spread.asDouble(context, target));
                        if (arrow == null) {
                            return MechanicResult.FAIL;
                        }
                        double damageValue = damage.asDouble(context, target);
                        if (damageValue >= 0) {
                            arrow.setDamage(damageValue);
                        }
                        arrow.setFireTicks(fireTicks.asInt(context, target));
                        arrow.setPierceLevel(Math.max(0, Math.min(127, pierce.asInt(context, target))));
                        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("shoot_fireball", Mechanics.type(
                MechanicMeta.builder("shoot_fireball")
                        .description("Fires a ghast or blaze fireball at the target.")
                        .requires(TargetKind.ANY)
                        .param("velocity", "launch speed", "1.0", "v", "speed")
                        .param("small", "the blaze fireball rather than the ghast one", "true")
                        .param("yield", "explosion power for the large form", "1.0", "power")
                        .param("incendiary", "leave fires", "false")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 1.0d);
                    boolean small = config.bool("small", true);
                    Expression yield = config.number("yield", 1.0d);
                    boolean incendiary = config.bool("incendiary", false);
                    return (context, target) -> {
                        Fireball fireball = small
                                ? spawnProjectile(context.caster(), target, SmallFireball.class,
                                velocity.asDouble(context, target), 0)
                                : spawnProjectile(context.caster(), target, Fireball.class,
                                velocity.asDouble(context, target), 0);
                        if (fireball == null) {
                            return MechanicResult.FAIL;
                        }
                        fireball.setYield(yield.asFloat(context, target));
                        fireball.setIsIncendiary(incendiary);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("shoot_potion", Mechanics.type(
                MechanicMeta.builder("shoot_potion")
                        .description("Throws a splash potion carrying one effect.")
                        .requires(TargetKind.ANY)
                        .required("type", "effect name", "t", "effect")
                        .param("velocity", "launch speed", "0.8", "v", "speed")
                        .param("duration", "effect duration", "5s", "d")
                        .param("level", "amplifier, 1 is level I", "1", "l")
                        .param("colour", "RRGGBB of the bottle", "", "color")
                        .build(),
                config -> {
                    PotionEffectType type = Registries.potionEffect(config.raw("type", ""));
                    if (type == null) {
                        throw new IllegalArgumentException(
                                "unknown potion effect '" + config.raw("type", "") + "'");
                    }
                    Expression velocity = config.number("velocity", 0.8d);
                    long duration = config.ticks("duration", 100L);
                    Expression level = config.number("level", 1);
                    String colour = config.raw("colour", "");
                    return (context, target) -> {
                        ThrownPotion potion = spawnProjectile(context.caster(), target, ThrownPotion.class,
                                velocity.asDouble(context, target), 0);
                        if (potion == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack bottle = new ItemStack(Material.SPLASH_POTION);
                        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
                        if (meta != null) {
                            meta.addCustomEffect(new PotionEffect(type, (int) duration,
                                    Math.max(0, level.asInt(context, target) - 1)), true);
                            if (!colour.isEmpty()) {
                                meta.setColor(PresentationMechanics.parseColour(colour, org.bukkit.Color.PURPLE));
                            }
                            bottle.setItemMeta(meta);
                        }
                        potion.setItem(bottle);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("shoot_skull", Mechanics.type(
                MechanicMeta.builder("shoot_skull")
                        .description("Fires a wither skull at the target.")
                        .requires(TargetKind.ANY)
                        .param("velocity", "launch speed", "1.0", "v", "speed")
                        .param("charged", "the blue, more destructive skull", "false")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 1.0d);
                    boolean charged = config.bool("charged", false);
                    return (context, target) -> {
                        WitherSkull skull = spawnProjectile(context.caster(), target, WitherSkull.class,
                                velocity.asDouble(context, target), 0);
                        if (skull == null) {
                            return MechanicResult.FAIL;
                        }
                        skull.setCharged(charged);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("shoot_trident", Mechanics.type(
                MechanicMeta.builder("shoot_trident")
                        .description("Throws a trident at the target.")
                        .requires(TargetKind.ANY)
                        .param("velocity", "launch speed", "2.0", "v", "speed")
                        .param("damage", "trident damage; -1 keeps the vanilla value", "-1", "d")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 2.0d);
                    Expression damage = config.number("damage", -1);
                    return (context, target) -> {
                        Trident trident = spawnProjectile(context.caster(), target, Trident.class,
                                velocity.asDouble(context, target), 0);
                        if (trident == null) {
                            return MechanicResult.FAIL;
                        }
                        double value = damage.asDouble(context, target);
                        if (value >= 0) {
                            trident.setDamage(value);
                        }
                        trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static Mechanics.Body scripted(Engine engine,
                                           dev.bwmp.bestiary.api.skill.MechanicConfig config,
                                           boolean homing) {
        Expression velocity = config.number("velocity", homing ? 0.8d : 1.0d);
        Expression maxDistance = config.number("max_distance", homing ? 48 : 32);
        Expression hitRadius = config.number("hit_radius", homing ? 1.2d : 1.0d);
        Expression gravity = config.number("gravity", 0);
        Expression turnRate = config.number("turn_rate", 0.15d);
        String onTick = config.raw("on_tick", "");
        String onHit = config.raw("on_hit", "");
        String onEnd = config.raw("on_end", "");
        Particle particle = PresentationMechanics.resolveParticle(
                config.raw("particle", homing ? "flame" : "crit"));
        boolean hitPlayers = config.bool("hit_players", true);
        boolean hitMobs = config.bool("hit_mobs", false);
        int steps = Math.max(1, Math.min(8, config.integer("step", 2)));

        return (context, target) -> {
            Location start = context.caster() instanceof LivingEntity
                    ? ((LivingEntity) context.caster()).getEyeLocation()
                    : context.caster().getLocation().add(0, 1, 0);
            Vector direction = target.location().toVector().subtract(start.toVector());
            if (direction.lengthSquared() < 1.0e-6d) {
                direction = start.getDirection();
            }
            launch(engine, context, start, direction.normalize(),
                    homing ? target.entity() : null,
                    velocity.asDouble(context, target),
                    maxDistance.asDouble(context, target),
                    hitRadius.asDouble(context, target),
                    gravity.asDouble(context, target),
                    homing ? turnRate.asDouble(context, target) : 0.0d,
                    onTick, onHit, onEnd, particle, hitPlayers, hitMobs, steps);
            return MechanicResult.SUCCESS;
        };
    }

    /**
     * Ticks a point along a path at the caster, so the whole flight stays on
     * the thread that owns the caster — the Folia-correct placement.
     */
    private static void launch(Engine engine, dev.bwmp.bestiary.api.skill.SkillContext context,
                               Location start, Vector direction, Entity homingTarget,
                               double speed, double maxDistance, double hitRadius, double gravity,
                               double turnRate, String onTick, String onHit, String onEnd,
                               Particle particle, boolean hitPlayers, boolean hitMobs, int steps) {
        World world = start.getWorld();
        Entity caster = context.caster();
        if (world == null || caster == null) {
            return;
        }

        Location cursor = start.clone();
        Vector heading = direction.clone().multiply(speed / steps);
        double[] travelled = {0.0d};
        Vector[] velocity = {heading};

        var holder = new Object() {
            BestiaryTask task;
        };
        holder.task = engine.scheduler().atEntityTimer(caster, () -> {
            if (!caster.isValid()) {
                holder.task.cancel();
                return;
            }
            for (int step = 0; step < steps; step++) {
                if (travelled[0] >= maxDistance) {
                    finish(engine, caster, context, cursor, onEnd, null);
                    holder.task.cancel();
                    return;
                }

                if (homingTarget != null && homingTarget.isValid() && turnRate > 0) {
                    Vector desired = homingTarget.getLocation().add(0, 0.8d, 0).toVector()
                            .subtract(cursor.toVector());
                    if (desired.lengthSquared() > 1.0e-6d) {
                        desired.normalize().multiply(velocity[0].length());
                        velocity[0] = velocity[0].multiply(1 - turnRate).add(desired.multiply(turnRate));
                    }
                }
                if (gravity != 0.0d) {
                    velocity[0] = velocity[0].clone().setY(velocity[0].getY() - gravity / steps);
                }

                cursor.add(velocity[0]);
                travelled[0] += velocity[0].length();

                if (!cursor.getBlock().isPassable()) {
                    finish(engine, caster, context, cursor, onEnd, null);
                    holder.task.cancel();
                    return;
                }

                PresentationMechanics.emit(engine, cursor, particle, 1, 0, 0, 0, 0, null);

                Entity hit = firstHit(world, cursor, hitRadius, caster, hitPlayers, hitMobs);
                if (hit != null) {
                    finish(engine, caster, context, cursor, onHit, hit);
                    holder.task.cancel();
                    return;
                }
            }

            if (!onTick.isEmpty()) {
                run(engine, caster, context, cursor, onTick, List.of(Target.of(cursor.clone())));
            }
        }, 1L, 1L);
    }

    private static Entity firstHit(World world, Location cursor, double radius, Entity caster,
                                   boolean hitPlayers, boolean hitMobs) {
        for (Entity candidate : world.getNearbyEntities(cursor, radius, radius, radius)) {
            if (candidate.equals(caster) || !(candidate instanceof LivingEntity)) {
                continue;
            }
            boolean isPlayer = candidate instanceof org.bukkit.entity.Player;
            if (isPlayer && !hitPlayers) {
                continue;
            }
            if (!isPlayer && !hitMobs) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static void finish(Engine engine, Entity caster, dev.bwmp.bestiary.api.skill.SkillContext context,
                               Location where, String skillId, Entity hit) {
        if (skillId.isEmpty()) {
            return;
        }
        List<Target> targets = hit != null ? List.of(Target.of(hit)) : List.of(Target.of(where.clone()));
        run(engine, caster, context, where, skillId, targets);
    }

    private static void run(Engine engine, Entity caster, dev.bwmp.bestiary.api.skill.SkillContext context,
                            Location origin, String skillId, List<Target> targets) {
        var skill = engine.content().skill(skillId);
        if (skill == null) {
            return;
        }
        engine.executor().cast(skill, caster, context.trigger(), origin.clone(), targets,
                context.power(), null, null);
    }

    private static <T extends Projectile> T spawnProjectile(Entity caster, Target target, Class<T> type,
                                                            double speed, double spread) {
        if (!(caster instanceof LivingEntity)) {
            return null;
        }
        LivingEntity shooter = (LivingEntity) caster;
        Vector direction = target.location().add(0, 0.8d, 0).toVector()
                .subtract(shooter.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0e-6d) {
            direction = shooter.getEyeLocation().getDirection();
        }
        direction.normalize().multiply(speed);
        if (spread > 0) {
            direction.add(Shapes.randomNear(new Location(shooter.getWorld(), 0, 0, 0), spread, false)
                    .toVector());
        }
        T projectile = shooter.launchProjectile(type, direction);
        projectile.setShooter(shooter);
        return projectile;
    }
}
