package dev.bwmp.bestiary.util;

import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import org.bukkit.Location;

import java.util.List;
import java.util.Locale;

/**
 * A {@code shape={type=ring;radius=6;points=48}} block, parsed once and
 * evaluated per execution.
 * <p>
 * One parser for the nested block means {@code particle}, {@code block_wave},
 * {@code summon_area} and the {@code @ring} targeter all accept the same
 * spelling, which is the difference between a shape system and eight mechanics
 * that each invented their own radius parameter.
 */
public final class ShapeSpec {

    public enum Kind {
        RING,
        DISC,
        SPHERE,
        CONE,
        LINE,
        SPIRAL,
        HELIX,
        CUBE,
        BOX,
        POINT
    }

    private final Kind kind;
    private final Expression radius;
    private final Expression points;
    private final Expression height;
    private final Expression length;
    private final Expression angle;
    private final Expression turns;
    private final Expression strands;
    private final Expression spacing;

    private ShapeSpec(Kind kind, Expression radius, Expression points, Expression height, Expression length,
                      Expression angle, Expression turns, Expression strands, Expression spacing) {
        this.kind = kind;
        this.radius = radius;
        this.points = points;
        this.height = height;
        this.length = length;
        this.angle = angle;
        this.turns = turns;
        this.strands = strands;
        this.spacing = spacing;
    }

    public static ShapeSpec of(MechanicConfig config) {
        Kind kind = Kind.POINT;
        String written = config.raw("type", "");
        if (!written.isBlank()) {
            try {
                kind = Kind.valueOf(written.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown shape '" + written + "'");
            }
        }
        return new ShapeSpec(kind,
                config.number("radius", 3.0d),
                config.number("points", 24),
                config.number("height", 0.0d),
                config.number("length", 6.0d),
                config.number("angle", 30.0d),
                config.number("turns", 3.0d),
                config.number("strands", 2),
                config.number("spacing", 0.5d));
    }

    public Kind kind() {
        return kind;
    }

    /** Generates the points for one execution, around {@code origin}. */
    public List<Location> points(SkillContext context, Target target, Location origin) {
        int count = Math.max(1, Math.min(2048, points.asInt(context, target)));
        double radiusValue = radius.asDouble(context, target);
        double heightValue = height.asDouble(context, target);

        switch (kind) {
            case RING:
                return Shapes.ring(origin, radiusValue, count, heightValue);
            case DISC:
                return Shapes.disc(origin, radiusValue, count, heightValue);
            case SPHERE:
                return Shapes.sphere(origin, radiusValue, count);
            case CONE:
                return Shapes.cone(origin, origin.getDirection(), length.asDouble(context, target),
                        angle.asDouble(context, target), count);
            case LINE:
                return Shapes.line(origin, origin.clone().add(origin.getDirection()
                        .multiply(length.asDouble(context, target))), spacing.asDouble(context, target));
            case SPIRAL:
                return Shapes.spiral(origin, radiusValue, heightValue == 0 ? 3.0d : heightValue, count,
                        turns.asDouble(context, target));
            case HELIX:
                return Shapes.helix(origin, radiusValue, heightValue == 0 ? 3.0d : heightValue, count,
                        turns.asDouble(context, target), strands.asInt(context, target));
            case CUBE:
                return Shapes.cube(origin, radiusValue * 2, spacing.asDouble(context, target));
            case BOX:
                return Shapes.box(origin, radiusValue * 2, spacing.asDouble(context, target));
            case POINT:
            default:
                return List.of(origin.clone().add(0, heightValue, 0));
        }
    }
}
