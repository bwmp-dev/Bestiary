package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.util.ShapeSpec;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Shapes as first-class mechanics.
 * <p>
 * Each one paints particles by default and runs a skill at every point when one
 * is named, so {@code ring{radius=6;skill=scorch}} is a ring of scorch marks
 * without a second mechanic. The same generators back the location targeters,
 * which is what keeps a telegraph aligned with the attack it telegraphs.
 */
public final class ShapeMechanics {

    private ShapeMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {
        into.put("ring", shape(engine, "ring", "A horizontal circle of points.",
                (config, context) -> null));
        into.put("circle", shape(engine, "circle", "An alias of ring.",
                (config, context) -> null));
        into.put("disc", shape(engine, "disc", "A filled horizontal circle.",
                (config, context) -> null));
        into.put("sphere", shape(engine, "sphere", "A hollow sphere.",
                (config, context) -> null));
        into.put("cone", shape(engine, "cone", "A cone opening along the caster's facing.",
                (config, context) -> null));
        into.put("line", shape(engine, "line", "A straight line from the origin.",
                (config, context) -> null));
        into.put("spiral", shape(engine, "spiral", "A rising spiral.",
                (config, context) -> null));
        into.put("helix", shape(engine, "helix", "Several intertwined spirals.",
                (config, context) -> null));
        into.put("cube", shape(engine, "cube", "The twelve edges of a cube.",
                (config, context) -> null));

        into.put("beam", Mechanics.type(
                MechanicMeta.builder("beam")
                        .description("A line from the caster to the target, with a skill at each point.")
                        .requires(TargetKind.ANY)
                        .param("particle", "trail particle", "end_rod", "p")
                        .param("spacing", "distance between points", "0.5", "step")
                        .param("skill", "skill run at each point", "", "s")
                        .param("from_eyes", "start at eye height rather than at the feet", "true")
                        .build(),
                config -> {
                    Particle particle = PresentationMechanics.resolveParticle(config.raw("particle", "end_rod"));
                    Expression spacing = config.number("spacing", 0.5d);
                    String skill = config.raw("skill", "");
                    boolean fromEyes = config.bool("from_eyes", true);
                    return (context, target) -> {
                        Location from = fromEyes && context.casterLiving() != null
                                ? context.casterLiving().getEyeLocation()
                                : context.origin();
                        Location to = target.isLiving() ? target.eyeLocation() : target.location();
                        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                            return MechanicResult.FAIL;
                        }
                        List<Location> points = Shapes.line(from, to, spacing.asDouble(context, target));
                        for (Location point : points) {
                            PresentationMechanics.emit(engine, point, particle, 1, 0, 0, 0, 0, null);
                        }
                        runAtPoints(engine, context, skill, points);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("mesh", Mechanics.type(
                MechanicMeta.builder("mesh")
                        .description("A grid of points on a horizontal plane, for large telegraphs.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "flame", "p")
                        .param("size", "edge length", "8", "s")
                        .param("spacing", "distance between points", "1", "step")
                        .param("height", "vertical offset", "0", "h")
                        .param("skill", "skill run at each point", "")
                        .build(),
                config -> {
                    Particle particle = PresentationMechanics.resolveParticle(config.raw("particle", "flame"));
                    Expression size = config.number("size", 8);
                    Expression spacing = config.number("spacing", 1);
                    Expression height = config.number("height", 0);
                    String skill = config.raw("skill", "");
                    return (context, target) -> {
                        Location centre = target.location().add(0, height.asDouble(context, target), 0);
                        double half = size.asDouble(context, target) / 2.0d;
                        double step = Math.max(0.25d, spacing.asDouble(context, target));
                        List<Location> points = new ArrayList<>();
                        for (double x = -half; x <= half; x += step) {
                            for (double z = -half; z <= half; z += step) {
                                points.add(centre.clone().add(x, 0, z));
                            }
                        }
                        for (Location point : points) {
                            PresentationMechanics.emit(engine, point, particle, 1, 0, 0, 0, 0, null);
                        }
                        runAtPoints(engine, context, skill, points);
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static MechanicType shape(Engine engine, String id, String description,
                                      BiFunction<MechanicConfig, Object, Void> unused) {
        return Mechanics.type(
                MechanicMeta.builder(id)
                        .description(description + " Paints particles, and runs a skill at each point when named.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name; empty paints nothing", "flame", "p")
                        .param("radius", "shape radius", "3", "r")
                        .param("points", "how many points", "24", "n")
                        .param("height", "vertical offset, or height for spiral and helix", "0", "h")
                        .param("length", "length for cone and line", "6", "l")
                        .param("angle", "cone half-angle in degrees", "30")
                        .param("turns", "revolutions for spiral and helix", "3")
                        .param("strands", "strands for helix", "2")
                        .param("spacing", "point spacing for line and cube", "0.5", "step")
                        .param("skill", "skill run at each point", "", "s")
                        .param("rotate_with_caster", "orient the shape to the caster's facing", "false")
                        .build(),
                config -> {
                    String particleName = config.raw("particle", "flame");
                    Particle particle = particleName.isEmpty()
                            ? null
                            : PresentationMechanics.resolveParticle(particleName);
                    String skill = config.raw("skill", "");
                    boolean rotate = config.bool("rotate_with_caster", false);
                    // `circle` is a synonym for `ring`; anything else uses its
                    // own name as the shape type.
                    String shapeType = id.equals("circle") ? "ring" : id;
                    ShapeSpec spec = ShapeSpec.of(new RenamedConfig(config, shapeType));
                    return (context, target) -> {
                        Location centre = target.location();
                        if (rotate) {
                            centre = centre.clone();
                            centre.setDirection(context.caster().getLocation().getDirection());
                        }
                        List<Location> points = spec.points(context, target, centre);
                        if (rotate && context.caster() != null) {
                            points = rotateAround(points, centre,
                                    context.caster().getLocation().getYaw());
                        }
                        if (particle != null) {
                            for (Location point : points) {
                                PresentationMechanics.emit(engine, point, particle, 1, 0, 0, 0, 0, null);
                            }
                        }
                        runAtPoints(engine, context, skill, points);
                        return MechanicResult.SUCCESS;
                    };
                });
    }

    private static List<Location> rotateAround(List<Location> points, Location centre, float yaw) {
        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        List<Location> rotated = new ArrayList<>(points.size());
        for (Location point : points) {
            Vector offset = point.toVector().subtract(centre.toVector());
            rotated.add(centre.clone().add(offset.getX() * cos - offset.getZ() * sin, offset.getY(),
                    offset.getX() * sin + offset.getZ() * cos));
        }
        return rotated;
    }

    private static void runAtPoints(Engine engine, dev.bwmp.bestiary.api.skill.SkillContext context,
                                    String skillId, List<Location> points) {
        if (skillId.isEmpty() || points.isEmpty()) {
            return;
        }
        List<Target> targets = new ArrayList<>(points.size());
        for (Location point : points) {
            targets.add(Target.of(point));
        }
        // One call with every point rather than one per point: the guard budget
        // counts mechanics, and a 512-point sphere would otherwise spend the
        // whole per-execution allowance on bookkeeping.
        context.runSkill(skillId, targets, 1.0d);
    }

    /**
     * Presents a mechanic's own parameters as a shape block, with {@code type}
     * fixed. Lets {@code ring{radius=6}} and
     * {@code particle{shape={type=ring;radius=6}}} share one implementation.
     */
    private static final class RenamedConfig implements MechanicConfig {

        private final MechanicConfig delegate;
        private final String type;

        private RenamedConfig(MechanicConfig delegate, String type) {
            this.delegate = delegate;
            this.type = type;
        }

        @Override
        public boolean contains(String key) {
            return key.equals("type") || delegate.contains(key);
        }

        @Override
        public java.util.Set<String> keys() {
            return delegate.keys();
        }

        @Override
        public String raw(String key, String fallback) {
            return key.equals("type") ? type : delegate.raw(key, fallback);
        }

        @Override
        public Expression number(String key, double fallback) {
            return delegate.number(key, fallback);
        }

        @Override
        public Expression text(String key, String fallback) {
            return key.equals("type")
                    ? dev.bwmp.bestiary.api.skill.Constant.of(type)
                    : delegate.text(key, fallback);
        }

        @Override
        public Expression require(String key) {
            return delegate.require(key);
        }

        @Override
        public int integer(String key, int fallback) {
            return delegate.integer(key, fallback);
        }

        @Override
        public double decimal(String key, double fallback) {
            return delegate.decimal(key, fallback);
        }

        @Override
        public boolean bool(String key, boolean fallback) {
            return delegate.bool(key, fallback);
        }

        @Override
        public long ticks(String key, long fallback) {
            return delegate.ticks(key, fallback);
        }

        @Override
        public List<String> stringList(String key) {
            return delegate.stringList(key);
        }

        @Override
        public MechanicConfig section(String key) {
            return delegate.section(key);
        }

        @Override
        public <E extends Enum<E>> E enumValue(Class<E> enumType, String key, E fallback) {
            return delegate.enumValue(enumType, key, fallback);
        }

        @Override
        public String source() {
            return delegate.source();
        }
    }
}
