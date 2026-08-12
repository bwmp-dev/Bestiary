package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The {@code random} and {@code math} namespaces.
 * <p>
 * {@code <random.AtoB>} is a uniform integer in {@code [A, B]} inclusive,
 * rolled per evaluation — so two mechanics reading it on the same line get two
 * different numbers, which is what "rolled per evaluation" has to mean for
 * {@code damage{amount=<random.4to9>}} to be a damage range rather than a
 * constant chosen at load.
 */
public final class MathPlaceholders implements PlaceholderResolver {

    @Override
    public Set<String> namespaces() {
        return Set.of("random", "math");
    }

    @Override
    public String resolve(String key, SkillContext context, Target target) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("random.")) {
            return random(lower.substring(7));
        }
        if (lower.startsWith("math.")) {
            return math(lower.substring(5));
        }
        return null;
    }

    private String random(String path) {
        int to = path.indexOf("to");
        if (to > 0 && to + 2 < path.length()) {
            try {
                long low = Long.parseLong(path.substring(0, to).trim());
                long high = Long.parseLong(path.substring(to + 2).trim());
                if (low > high) {
                    long swap = low;
                    low = high;
                    high = swap;
                }
                return Long.toString(ThreadLocalRandom.current().nextLong(low, high + 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        switch (path) {
            case "float":
            case "double":
                return Double.toString(ThreadLocalRandom.current().nextDouble());
            case "sign":
                return ThreadLocalRandom.current().nextBoolean() ? "1" : "-1";
            case "bool":
            case "boolean":
                return Boolean.toString(ThreadLocalRandom.current().nextBoolean());
            case "angle":
                return Double.toString(ThreadLocalRandom.current().nextDouble() * 360.0d);
            default:
                try {
                    long bound = Long.parseLong(path.trim());
                    return Long.toString(ThreadLocalRandom.current().nextLong(0, Math.max(1, bound) + 1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
        }
    }

    private String math(String path) {
        switch (path) {
            case "pi":
                return Double.toString(Math.PI);
            case "e":
                return Double.toString(Math.E);
            case "tau":
                return Double.toString(Math.PI * 2);
            case "random":
                return Double.toString(ThreadLocalRandom.current().nextDouble());
            default:
                return null;
        }
    }
}
