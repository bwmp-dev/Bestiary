package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.expression.Attributes;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Movement.
 * <p>
 * Everything that moves an entity across a region boundary — {@code teleport},
 * {@code teleport_to}, {@code pull} — goes through the scheduler's teleport
 * rather than {@code Entity#teleport}, and re-validates both entities after the
 * hop. That is the class of bug that is invisible on Paper and fatal on Folia,
 * so it is designed in rather than fixed later.
 */
public final class MovementMechanics {

    private MovementMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("velocity", Mechanics.type(
                MechanicMeta.builder("velocity")
                        .description("Applies a velocity, absolutely or relative to the origin.")
                        .requires(TargetKind.ENTITY)
                        .param("mode", "set, add, away_from_origin, toward_origin, forward", "add", "m")
                        .param("strength", "horizontal magnitude", "1.0", "s")
                        .param("vertical", "vertical component", "0.0", "y", "v")
                        .param("x", "explicit x, for mode=set", "0")
                        .param("z", "explicit z, for mode=set", "0")
                        .build(),
                config -> {
                    String mode = config.raw("mode", "add").toLowerCase(Locale.ROOT).replace("_", "");
                    Expression strength = config.number("strength", 1.0d);
                    Expression vertical = config.number("vertical", 0.0d);
                    Expression x = config.number("x", 0.0d);
                    Expression z = config.number("z", 0.0d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        if (engine.immunity().immune(entity, "knockback")) {
                            return MechanicResult.FAIL;
                        }

                        double power = strength.asDouble(context, target) * context.power();
                        double lift = vertical.asDouble(context, target);
                        Location origin = context.origin();
                        Vector velocity;

                        switch (mode) {
                            case "set":
                                velocity = new Vector(x.asDouble(context, target), lift,
                                        z.asDouble(context, target));
                                entity.setVelocity(velocity);
                                return MechanicResult.SUCCESS;
                            case "awayfromorigin":
                                velocity = horizontal(entity.getLocation().toVector()
                                        .subtract(origin.toVector())).multiply(power).setY(lift);
                                break;
                            case "towardorigin":
                            case "towardsorigin":
                                velocity = horizontal(origin.toVector()
                                        .subtract(entity.getLocation().toVector())).multiply(power).setY(lift);
                                break;
                            case "forward":
                                velocity = entity.getLocation().getDirection().multiply(power).setY(lift);
                                break;
                            case "add":
                            default:
                                velocity = entity.getVelocity().add(
                                        entity.getLocation().getDirection().multiply(power).setY(lift));
                                break;
                        }
                        entity.setVelocity(velocity);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("leap", Mechanics.type(
                MechanicMeta.builder("leap")
                        .description("Throws the target toward a point on a ballistic arc.")
                        .requires(TargetKind.ENTITY)
                        .param("velocity", "arc strength", "1.0", "v", "strength")
                        .param("height", "peak lift", "0.6", "h")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 1.0d);
                    Expression height = config.number("height", 0.6d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        Vector direction = entity.getLocation().getDirection()
                                .multiply(velocity.asDouble(context, target));
                        direction.setY(height.asDouble(context, target));
                        entity.setVelocity(direction);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("dash", Mechanics.type(
                MechanicMeta.builder("dash")
                        .description("A flat horizontal burst in the facing direction.")
                        .requires(TargetKind.ENTITY)
                        .param("strength", "how hard", "1.4", "s")
                        .build(),
                config -> {
                    Expression strength = config.number("strength", 1.4d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        Vector direction = horizontal(entity.getLocation().getDirection())
                                .multiply(strength.asDouble(context, target) * context.power());
                        entity.setVelocity(direction.setY(0.1d));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("lunge", Mechanics.type(
                MechanicMeta.builder("lunge")
                        .description("Throws the caster at the target.")
                        .requires(TargetKind.ANY)
                        .param("strength", "horizontal magnitude", "1.2", "s")
                        .param("vertical", "vertical component", "0.4", "v", "y")
                        .build(),
                config -> {
                    Expression strength = config.number("strength", 1.2d);
                    Expression vertical = config.number("vertical", 0.4d);
                    return (context, target) -> {
                        Entity caster = context.caster();
                        Vector direction = horizontal(target.location().toVector()
                                .subtract(caster.getLocation().toVector()))
                                .multiply(strength.asDouble(context, target));
                        caster.setVelocity(direction.setY(vertical.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("pull", Mechanics.type(
                MechanicMeta.builder("pull")
                        .description("Drags the target toward the origin.")
                        .requires(TargetKind.ENTITY)
                        .param("strength", "how hard", "1.0", "s")
                        .param("vertical", "vertical component", "0.2", "v", "y")
                        .build(),
                config -> pushOrPull(engine, config, true)));

        into.put("push", Mechanics.type(
                MechanicMeta.builder("push")
                        .description("Shoves the target away from the origin.")
                        .requires(TargetKind.ENTITY)
                        .param("strength", "how hard", "1.0", "s")
                        .param("vertical", "vertical component", "0.2", "v", "y")
                        .build(),
                config -> pushOrPull(engine, config, false)));

        into.put("throw", Mechanics.type(
                MechanicMeta.builder("throw")
                        .description("Launches the target upward and outward.")
                        .requires(TargetKind.ENTITY)
                        .param("strength", "horizontal magnitude", "0.8", "s")
                        .param("vertical", "vertical magnitude", "1.0", "v", "y")
                        .build(),
                config -> {
                    Expression strength = config.number("strength", 0.8d);
                    Expression vertical = config.number("vertical", 1.0d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null || engine.immunity().immune(entity, "knockback")) {
                            return MechanicResult.FAIL;
                        }
                        Vector direction = horizontal(entity.getLocation().toVector()
                                .subtract(context.origin().toVector()))
                                .multiply(strength.asDouble(context, target));
                        entity.setVelocity(direction.setY(vertical.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("teleport", Mechanics.type(
                MechanicMeta.builder("teleport")
                        .description("Moves the target to its own resolved location.")
                        .requires(TargetKind.ANY)
                        .param("keep_direction", "preserve yaw and pitch", "true", "kd")
                        .param("offset_y", "vertical offset applied after", "0", "oy")
                        .build(),
                config -> {
                    boolean keepDirection = config.bool("keep_direction", true);
                    Expression offsetY = config.number("offset_y", 0);
                    return (context, target) -> {
                        Entity caster = context.caster();
                        Location destination = target.location().add(0, offsetY.asDouble(context, target), 0);
                        if (keepDirection) {
                            destination.setYaw(caster.getLocation().getYaw());
                            destination.setPitch(caster.getLocation().getPitch());
                        }
                        engine.scheduler().teleport(caster, destination);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("teleport_to", Mechanics.type(
                MechanicMeta.builder("teleport_to")
                        .description("Moves each target to the caster, or to a named anchor.")
                        .requires(TargetKind.ENTITY)
                        .param("anchor", "anchor id; empty means the caster", "", "a")
                        .param("offset_y", "vertical offset", "0", "oy")
                        .build(),
                config -> {
                    String anchorId = config.raw("anchor", "");
                    Expression offsetY = config.number("offset_y", 0);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        Location destination = anchorId.isEmpty()
                                ? context.caster().getLocation()
                                : engine.anchors().byId(anchorId).map(anchor -> anchor.location()).orElse(null);
                        if (destination == null) {
                            return MechanicResult.FAIL;
                        }
                        Location adjusted = destination.clone().add(0, offsetY.asDouble(context, target), 0);
                        // Both ends are re-validated after the hop: on Folia the
                        // future completes on another region's thread, by which
                        // time either entity may be gone.
                        engine.scheduler().teleport(entity, adjusted).thenAccept(moved -> {
                            if (Boolean.TRUE.equals(moved) && entity.isValid()) {
                                entity.setFallDistance(0.0f);
                            }
                        });
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("random_teleport", Mechanics.type(
                MechanicMeta.builder("random_teleport")
                        .description("Blinks the target to a nearby safe spot.")
                        .requires(TargetKind.ENTITY)
                        .param("radius", "how far", "8", "r")
                        .param("attempts", "how many spots to try before giving up", "12")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 8);
                    int attempts = config.integer("attempts", 12);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        double range = radius.asDouble(context, target);
                        for (int attempt = 0; attempt < attempts; attempt++) {
                            Location candidate = Shapes.randomNear(entity.getLocation(), range, true);
                            candidate = safeGround(candidate);
                            if (candidate != null) {
                                engine.scheduler().teleport(entity, candidate);
                                return MechanicResult.SUCCESS;
                            }
                        }
                        return MechanicResult.FAIL;
                    };
                }));

        into.put("orbit", Mechanics.type(
                MechanicMeta.builder("orbit")
                        .description("Nudges the target along a circle around the origin.")
                        .requires(TargetKind.ENTITY)
                        .param("radius", "orbit radius", "4", "r")
                        .param("speed", "radians per application", "0.2", "s")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 4);
                    Expression speed = config.number("speed", 0.2d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        Location origin = context.origin();
                        Vector offset = entity.getLocation().toVector().subtract(origin.toVector());
                        double angle = Math.atan2(offset.getZ(), offset.getX())
                                + speed.asDouble(context, target);
                        double distance = radius.asDouble(context, target);
                        Location destination = origin.clone().add(Math.cos(angle) * distance,
                                offset.getY(), Math.sin(angle) * distance);
                        entity.setVelocity(destination.toVector().subtract(entity.getLocation().toVector())
                                .multiply(0.5d));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("jump", Mechanics.type(
                MechanicMeta.builder("jump")
                        .description("A straight vertical hop.")
                        .requires(TargetKind.ENTITY)
                        .param("strength", "how high", "0.6", "s")
                        .build(),
                config -> {
                    Expression strength = config.number("strength", 0.6d);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setVelocity(entity.getVelocity().setY(strength.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("look", Mechanics.type(
                MechanicMeta.builder("look")
                        .description("Turns the caster to face the target.")
                        .requires(TargetKind.ANY)
                        .param("pitch", "also match pitch", "true")
                        .build(),
                config -> {
                    boolean matchPitch = config.bool("pitch", true);
                    return (context, target) -> {
                        Entity caster = context.caster();
                        Location from = caster.getLocation();
                        Vector direction = target.location().toVector().subtract(from.toVector());
                        if (direction.lengthSquared() < 1.0e-6d) {
                            return MechanicResult.FAIL;
                        }
                        Location facing = from.clone().setDirection(direction);
                        if (!matchPitch) {
                            facing.setPitch(from.getPitch());
                        }
                        if (caster instanceof Player) {
                            ((Player) caster).teleport(facing);
                        } else {
                            caster.setRotation(facing.getYaw(), facing.getPitch());
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("rotate", Mechanics.type(
                MechanicMeta.builder("rotate")
                        .description("Turns the caster by a fixed amount.")
                        .requires(TargetKind.NONE)
                        .param("yaw", "degrees added to yaw", "0")
                        .param("pitch", "degrees added to pitch", "0")
                        .build(),
                config -> {
                    Expression yaw = config.number("yaw", 0);
                    Expression pitch = config.number("pitch", 0);
                    return (context, target) -> {
                        Entity caster = context.caster();
                        Location location = caster.getLocation();
                        caster.setRotation(location.getYaw() + (float) yaw.asDouble(context, target),
                                location.getPitch() + (float) pitch.asDouble(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_speed", Mechanics.type(
                MechanicMeta.builder("set_speed")
                        .description("Sets the movement speed attribute.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "new base speed", "0.25", "a", "speed")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 0.25d);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        AttributeInstance instance = attribute(entity, "GENERIC_MOVEMENT_SPEED");
                        if (instance == null) {
                            return MechanicResult.FAIL;
                        }
                        instance.setBaseValue(Math.max(0.0d, amount.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_gravity", Mechanics.type(
                MechanicMeta.builder("set_gravity")
                        .description("Turns gravity on or off.")
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setGravity(value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_flying", Mechanics.type(
                MechanicMeta.builder("set_flying")
                        .description("Toggles a player's flight.")
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        player.setAllowFlight(value);
                        player.setFlying(value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("mount", Mechanics.type(
                MechanicMeta.builder("mount")
                        .description("Seats the caster on the target, or the target on the caster.")
                        .requires(TargetKind.ENTITY)
                        .param("mode", "caster_rides_target or target_rides_caster", "caster_rides_target", "m")
                        .build(),
                config -> {
                    boolean casterRides = !config.raw("mode", "caster_rides_target")
                            .toLowerCase(Locale.ROOT).replace("_", "").equals("targetridescaster");
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        return Mechanics.result(casterRides
                                ? entity.addPassenger(context.caster())
                                : context.caster().addPassenger(entity));
                    };
                }));

        into.put("dismount", Mechanics.type(
                MechanicMeta.builder("dismount")
                        .description("Removes the target from whatever it is riding, and its passengers.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    Entity entity = target.entity();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    entity.leaveVehicle();
                    entity.eject();
                    return MechanicResult.SUCCESS;
                }));

        into.put("leash_to_anchor", Mechanics.type(
                MechanicMeta.builder("leash_to_anchor")
                        .description("Pulls the caster back toward its spawn anchor when it strays too far.")
                        .requires(TargetKind.NONE)
                        .param("distance", "how far it may wander", "24", "d", "range")
                        .param("teleport", "teleport rather than nudge when beyond twice the distance", "true")
                        .build(),
                config -> {
                    Expression distance = config.number("distance", 24);
                    boolean teleport = config.bool("teleport", true);
                    return (context, target) -> {
                        LivingEntity caster = context.casterLiving();
                        if (caster == null) {
                            return MechanicResult.FAIL;
                        }
                        var instance = engine.mobs().instance(caster);
                        Location home = instance == null ? null : anchorOrSpawn(engine, instance);
                        if (home == null || !home.getWorld().equals(caster.getWorld())) {
                            return MechanicResult.FAIL;
                        }
                        double limit = distance.asDouble(context, target);
                        double actual = caster.getLocation().distance(home);
                        if (actual <= limit) {
                            return MechanicResult.PASS;
                        }
                        if (teleport && actual > limit * 2) {
                            engine.scheduler().teleport(caster, home);
                            return MechanicResult.SUCCESS;
                        }
                        caster.setVelocity(home.toVector().subtract(caster.getLocation().toVector())
                                .normalize().multiply(0.4d));
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    static Location anchorOrSpawn(Engine engine, dev.bwmp.bestiary.mob.MobInstance instance) {
        if (!instance.anchorId().isEmpty()) {
            Location anchor = engine.anchors().byId(instance.anchorId())
                    .map(record -> record.location()).orElse(null);
            if (anchor != null) {
                return anchor;
            }
        }
        return instance.spawnLocation();
    }

    private static Mechanics.Body pushOrPull(Engine engine,
                                             dev.bwmp.bestiary.api.skill.MechanicConfig config,
                                             boolean pull) {
        Expression strength = config.number("strength", 1.0d);
        Expression vertical = config.number("vertical", 0.2d);
        return (context, target) -> {
            Entity entity = target.entity();
            if (entity == null || engine.immunity().immune(entity, "knockback")) {
                return MechanicResult.FAIL;
            }
            Location origin = context.origin();
            Vector direction = pull
                    ? origin.toVector().subtract(entity.getLocation().toVector())
                    : entity.getLocation().toVector().subtract(origin.toVector());
            if (direction.lengthSquared() < 1.0e-6d) {
                return MechanicResult.FAIL;
            }
            entity.setVelocity(horizontal(direction).multiply(strength.asDouble(context, target))
                    .setY(vertical.asDouble(context, target)));
            return MechanicResult.SUCCESS;
        };
    }

    private static Vector horizontal(Vector vector) {
        Vector flat = new Vector(vector.getX(), 0, vector.getZ());
        return flat.lengthSquared() < 1.0e-6d ? new Vector(0, 0, 0) : flat.normalize();
    }

    private static AttributeInstance attribute(LivingEntity entity, String legacyName) {
        org.bukkit.attribute.Attribute attribute = Attributes.byLegacyName(legacyName);
        return attribute == null ? null : entity.getAttribute(attribute);
    }

    /** A spot with air to stand in and something under it, or null. */
    private static Location safeGround(Location candidate) {
        if (candidate.getWorld() == null) {
            return null;
        }
        for (int drop = 0; drop < 8; drop++) {
            Location probe = candidate.clone().subtract(0, drop, 0);
            if (probe.getBlock().isPassable()
                    && probe.clone().add(0, 1, 0).getBlock().isPassable()
                    && !probe.clone().subtract(0, 1, 0).getBlock().isPassable()) {
                return probe.add(0.5d, 0, 0.5d);
            }
        }
        return null;
    }

    static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }
}
