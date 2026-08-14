package dev.bwmp.bestiary.ai;

import dev.bwmp.bestiary.api.ai.AiContext;
import dev.bwmp.bestiary.api.ai.AiGoal;
import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.ai.GoalCategory;
import dev.bwmp.bestiary.api.config.Args;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The built-in goal library.
 * <p>
 * {@code avoid_void} and {@code return_to_anchor} are not generic filler. On a
 * floating-island world every boss arena is an island, and a boss that walks
 * off the edge is unkillable and — because its anchor's respawn cooldown never
 * clears — permanently gone. Arena leashing is a correctness requirement there,
 * not a nicety.
 */
public final class BuiltinGoals {

    private BuiltinGoals() {
    }

    public static Map<String, AiGoalType> all() {
        Map<String, AiGoalType> goals = new LinkedHashMap<>();

        goals.put("meleeattack", (context, args) -> new AiGoal() {
            private final double speed = number(args, "speed", 1.0d);
            private final double reach = number(args, "reach", 3.0d);
            private int cooldown;

            @Override
            public boolean shouldActivate() {
                LivingEntity target = context.target();
                return target != null && target.isValid() && !target.isDead();
            }

            @Override
            public void tick() {
                LivingEntity target = context.target();
                if (target == null) {
                    return;
                }
                context.controller().moveTo(context.mob(), target.getLocation(), speed);
                if (cooldown > 0) {
                    cooldown--;
                    return;
                }
                if (context.mob().getLocation().distanceSquared(target.getLocation()) <= reach * reach) {
                    context.mob().attack(target);
                    cooldown = 20;
                }
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE, GoalCategory.LOOK);
            }
        });

        goals.put("rangedattack", (context, args) -> new AiGoal() {
            private final String skill = string(args, "skill", "");
            private final double range = number(args, "range", 16.0d);
            private final int interval = (int) number(args, "interval", 40);
            private int cooldown;

            @Override
            public boolean shouldActivate() {
                LivingEntity target = context.target();
                return target != null && target.isValid()
                        && target.getWorld().equals(context.mob().getWorld())
                        && target.getLocation().distanceSquared(context.mob().getLocation())
                        <= range * range;
            }

            @Override
            public void tick() {
                if (cooldown-- > 0 || skill.isEmpty()) {
                    return;
                }
                cooldown = interval;
                context.castSkill(skill);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.LOOK);
            }
        });

        goals.put("strafe", (context, args) -> new OrbitGoal(context,
                number(args, "distance", 6.0d), number(args, "speed", 1.0d), 0.0d,
                number(args, "step", 0.25d)));

        goals.put("hoverstrafe", (context, args) -> new OrbitGoal(context,
                number(args, "distance", 6.0d), number(args, "speed", 1.0d),
                number(args, "height", 3.0d), number(args, "step", 0.2d)));

        goals.put("circletarget", (context, args) -> new OrbitGoal(context,
                number(args, "radius", 5.0d), number(args, "speed", 1.0d), 0.0d,
                number(args, "step", 0.3d)));

        goals.put("keepdistance", (context, args) -> new AiGoal() {
            private final double distance = number(args, "distance", 8.0d);
            private final double speed = number(args, "speed", 1.1d);

            @Override
            public boolean shouldActivate() {
                LivingEntity target = context.target();
                return target != null && target.isValid()
                        && target.getWorld().equals(context.mob().getWorld())
                        && target.getLocation().distance(context.mob().getLocation()) < distance;
            }

            @Override
            public void tick() {
                LivingEntity target = context.target();
                if (target == null) {
                    return;
                }
                Vector away = context.mob().getLocation().toVector()
                        .subtract(target.getLocation().toVector());
                if (away.lengthSquared() < 1.0e-6d) {
                    return;
                }
                Location destination = context.mob().getLocation()
                        .add(away.normalize().multiply(distance));
                context.controller().moveTo(context.mob(), destination, speed);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("charge", (context, args) -> new AiGoal() {
            private final double speed = number(args, "speed", 1.6d);
            private final double minimum = number(args, "min_distance", 6.0d);
            private final int interval = (int) number(args, "interval", 100);
            private int cooldown;

            @Override
            public boolean shouldActivate() {
                LivingEntity target = context.target();
                return target != null && target.isValid()
                        && target.getWorld().equals(context.mob().getWorld())
                        && target.getLocation().distance(context.mob().getLocation()) >= minimum;
            }

            @Override
            public void start() {
                cooldown = 0;
            }

            @Override
            public void tick() {
                LivingEntity target = context.target();
                if (target == null || cooldown-- > 0) {
                    return;
                }
                cooldown = interval;
                Vector direction = target.getLocation().toVector()
                        .subtract(context.mob().getLocation().toVector());
                if (direction.lengthSquared() < 1.0e-6d) {
                    return;
                }
                context.mob().setVelocity(direction.normalize().multiply(speed).setY(0.25d));
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("fleebelowhealth", (context, args) -> new AiGoal() {
            private final double percent = number(args, "percent", 20.0d);
            private final double speed = number(args, "speed", 1.4d);

            @Override
            public boolean shouldActivate() {
                LivingEntity mob = context.mob();
                double max = maxHealth(mob);
                return max > 0 && mob.getHealth() / max * 100.0d <= percent && context.target() != null;
            }

            @Override
            public void tick() {
                LivingEntity target = context.target();
                if (target == null) {
                    return;
                }
                Vector away = context.mob().getLocation().toVector()
                        .subtract(target.getLocation().toVector());
                if (away.lengthSquared() < 1.0e-6d) {
                    return;
                }
                context.controller().moveTo(context.mob(),
                        context.mob().getLocation().add(away.normalize().multiply(12)), speed);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("patrolanchor", (context, args) -> new AiGoal() {
            private final double radius = number(args, "radius", 10.0d);
            private final double speed = number(args, "speed", 0.9d);
            private final int interval = (int) number(args, "interval", 100);
            private int cooldown;

            @Override
            public boolean shouldActivate() {
                return context.anchor() != null && context.target() == null;
            }

            @Override
            public void tick() {
                if (cooldown-- > 0) {
                    return;
                }
                cooldown = interval;
                Location anchor = context.anchor();
                if (anchor == null || !anchor.getWorld().equals(context.mob().getWorld())) {
                    return;
                }
                double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double distance = ThreadLocalRandom.current().nextDouble() * radius;
                context.controller().moveTo(context.mob(),
                        anchor.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), speed);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("returntoanchor", (context, args) -> new AiGoal() {
            private final double distance = number(args, "distance", 24.0d);
            private final double speed = number(args, "speed", 1.2d);
            private final boolean teleport = bool(args, "teleport", true);

            @Override
            public boolean shouldActivate() {
                Location anchor = context.anchor();
                if (anchor == null || anchor.getWorld() == null
                        || !anchor.getWorld().equals(context.mob().getWorld())) {
                    return false;
                }
                return context.mob().getLocation().distance(anchor) > distance;
            }

            @Override
            public void tick() {
                Location anchor = context.anchor();
                if (anchor == null) {
                    return;
                }
                double actual = context.mob().getLocation().distance(anchor);
                if (teleport && actual > distance * 2) {
                    context.mob().teleport(anchor);
                    return;
                }
                context.controller().moveTo(context.mob(), anchor, speed);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("lookattarget", (context, args) -> new AiGoal() {
            private final double range = number(args, "range", 24.0d);

            @Override
            public boolean shouldActivate() {
                LivingEntity target = context.target();
                return target != null && target.isValid()
                        && target.getWorld().equals(context.mob().getWorld())
                        && target.getLocation().distanceSquared(context.mob().getLocation())
                        <= range * range;
            }

            @Override
            public void tick() {
                LivingEntity target = context.target();
                if (target == null) {
                    return;
                }
                Location facing = context.mob().getLocation()
                        .setDirection(target.getEyeLocation().toVector()
                                .subtract(context.mob().getEyeLocation().toVector()));
                context.mob().setRotation(facing.getYaw(), facing.getPitch());
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.LOOK);
            }
        });

        goals.put("float", (context, args) -> new AiGoal() {
            @Override
            public boolean shouldActivate() {
                return context.mob().isInWater() || context.mob().getLocation().getBlock()
                        .getType() == org.bukkit.Material.LAVA;
            }

            @Override
            public void tick() {
                context.mob().setVelocity(context.mob().getVelocity().setY(0.15d));
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.JUMP);
            }
        });

        goals.put("avoidvoid", (context, args) -> new AiGoal() {
            private final double margin = number(args, "margin", 4.0d);
            private final double minimumY = number(args, "min_y", Double.NaN);

            private double floorY() {
                if (!Double.isNaN(minimumY)) {
                    return minimumY;
                }
                Location anchor = context.anchor();
                // Without an anchor the world's own floor is the only honest
                // answer; with one, the arena's height is far more useful than
                // the bottom of the world.
                return anchor != null ? anchor.getY() - 8.0d
                        : context.mob().getWorld().getMinHeight() + 4.0d;
            }

            @Override
            public boolean shouldActivate() {
                LivingEntity mob = context.mob();
                if (mob.getLocation().getY() < floorY() + margin) {
                    return true;
                }
                // Also fires while still on the ledge: the ground under the mob
                // being air for the next several blocks is the moment to stop,
                // not the moment after it has already stepped off.
                Location probe = mob.getLocation();
                for (int drop = 1; drop <= 6; drop++) {
                    if (!probe.clone().subtract(0, drop, 0).getBlock().isPassable()) {
                        return false;
                    }
                }
                return !mob.isOnGround();
            }

            @Override
            public void tick() {
                LivingEntity mob = context.mob();
                Location anchor = context.anchor();
                Location destination = anchor != null && anchor.getWorld().equals(mob.getWorld())
                        ? anchor
                        : mob.getLocation().add(0, 2, 0);
                if (mob.getLocation().getY() < floorY()) {
                    mob.teleport(destination);
                    mob.setVelocity(new Vector(0, 0, 0));
                    mob.setFallDistance(0.0f);
                    return;
                }
                Vector back = destination.toVector().subtract(mob.getLocation().toVector());
                if (back.lengthSquared() > 1.0e-6d) {
                    mob.setVelocity(back.normalize().multiply(0.35d).setY(0.25d));
                }
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE, GoalCategory.JUMP);
            }
        });

        goals.put("followowner", (context, args) -> new AiGoal() {
            private final double distance = number(args, "distance", 6.0d);
            private final double speed = number(args, "speed", 1.1d);

            private Player nearestPlayer() {
                Player nearest = null;
                double best = 32 * 32;
                for (Player player : context.mob().getWorld().getPlayers()) {
                    double squared = player.getLocation()
                            .distanceSquared(context.mob().getLocation());
                    if (squared < best) {
                        best = squared;
                        nearest = player;
                    }
                }
                return nearest;
            }

            @Override
            public boolean shouldActivate() {
                Player owner = nearestPlayer();
                return owner != null
                        && owner.getLocation().distance(context.mob().getLocation()) > distance;
            }

            @Override
            public void tick() {
                Player owner = nearestPlayer();
                if (owner != null) {
                    context.controller().moveTo(context.mob(), owner.getLocation(), speed);
                }
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.MOVE);
            }
        });

        goals.put("guardarea", (context, args) -> new AiGoal() {
            private final double radius = number(args, "radius", 16.0d);

            @Override
            public boolean shouldActivate() {
                Location anchor = context.anchor();
                return anchor != null && anchor.getWorld().equals(context.mob().getWorld());
            }

            @Override
            public void tick() {
                Location anchor = context.anchor();
                LivingEntity current = context.target();
                if (current != null && current.isValid()
                        && current.getLocation().distance(anchor) <= radius) {
                    return;
                }
                // A guard drops a target that has left its area rather than
                // chasing it out, which is what stops an arena boss following a
                // player off the island.
                Player intruder = null;
                double best = radius * radius;
                for (Player player : context.mob().getWorld().getPlayers()) {
                    double squared = player.getLocation().distanceSquared(anchor);
                    if (squared <= best) {
                        best = squared;
                        intruder = player;
                    }
                }
                context.setTarget(intruder);
            }

            @Override
            public Set<GoalCategory> categories() {
                return Set.of(GoalCategory.TARGET);
            }
        });

        return goals;
    }

    private static final class OrbitGoal implements AiGoal {

        private final AiContext context;
        private final double distance;
        private final double speed;
        private final double height;
        private final double step;
        private double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;

        private OrbitGoal(AiContext context, double distance, double speed, double height, double step) {
            this.context = context;
            this.distance = distance;
            this.speed = speed;
            this.height = height;
            this.step = step;
        }

        @Override
        public boolean shouldActivate() {
            LivingEntity target = context.target();
            return target != null && target.isValid()
                    && target.getWorld().equals(context.mob().getWorld());
        }

        @Override
        public void tick() {
            LivingEntity target = context.target();
            if (target == null) {
                return;
            }
            angle += step;
            Location destination = target.getLocation().clone().add(
                    Math.cos(angle) * distance, height, Math.sin(angle) * distance);
            if (height > 0) {
                // A hovering mob cannot be pathed to a point in the air, so it
                // is nudged rather than navigated.
                Vector toward = destination.toVector()
                        .subtract(context.mob().getLocation().toVector());
                if (toward.lengthSquared() > 1.0e-6d) {
                    context.mob().setVelocity(toward.normalize().multiply(0.2d * speed));
                }
                return;
            }
            context.controller().moveTo(context.mob(), destination, speed);
        }

        @Override
        public Set<GoalCategory> categories() {
            return Set.of(GoalCategory.MOVE, GoalCategory.LOOK);
        }
    }

    private static double maxHealth(LivingEntity entity) {
        var attribute = org.bukkit.attribute.Attribute.class;
        try {
            Object value = attribute.getMethod("valueOf", String.class)
                    .invoke(null, "GENERIC_MAX_HEALTH");
            var instance = entity.getAttribute((org.bukkit.attribute.Attribute) value);
            return instance == null ? entity.getHealth() : instance.getValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return entity.getHealth();
        }
    }

    private static double number(Args args, String key, double fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Args args, String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equals("1");
    }

    private static String string(Args args, String key, String fallback) {
        Object value = args.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
