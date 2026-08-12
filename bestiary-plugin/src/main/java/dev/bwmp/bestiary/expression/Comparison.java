package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.Locale;

/**
 * A condition value with an optional comparator prefix — {@code <=}, {@code >=},
 * {@code <}, {@code >}, {@code =}, {@code !=} — followed by an expression. A
 * bare number means {@code =}. This is what {@code until: { health_percent:
 * "<= 60" }} parses as.
 * <p>
 * The prefix is read before any placeholder handling, which is the only way
 * {@code <} can serve as both "less than" and the opening bracket of
 * {@code <caster.level>}: a leading {@code <} that begins a well-formed
 * placeholder in a registered namespace is not a comparator.
 */
public final class Comparison {

    public enum Operator {
        LT("<"),
        LE("<="),
        GT(">"),
        GE(">="),
        EQ("="),
        NE("!=");

        private final String written;

        Operator(String written) {
            this.written = written;
        }

        public String written() {
            return written;
        }
    }

    private final Operator operator;
    private final Expression value;
    private final String source;

    private Comparison(Operator operator, Expression value, String source) {
        this.operator = operator;
        this.value = value;
        this.source = source;
    }

    public static Comparison parse(ExpressionEngine engine, String written, String location) {
        String text = written == null ? "" : written.trim();
        Operator operator = Operator.EQ;

        if (text.startsWith("!=") || text.startsWith("<>")) {
            operator = Operator.NE;
            text = text.substring(2);
        } else if (text.startsWith("<=")) {
            operator = Operator.LE;
            text = text.substring(2);
        } else if (text.startsWith(">=")) {
            operator = Operator.GE;
            text = text.substring(2);
        } else if (text.startsWith("==")) {
            text = text.substring(2);
        } else if (text.startsWith(">")) {
            operator = Operator.GT;
            text = text.substring(1);
        } else if (text.startsWith("<") && !startsWithPlaceholder(engine, text)) {
            operator = Operator.LT;
            text = text.substring(1);
        } else if (text.startsWith("=")) {
            text = text.substring(1);
        }

        String remainder = text.trim();
        return new Comparison(operator, engine.compileNumber(remainder, location), written == null ? "" : written);
    }

    private static boolean startsWithPlaceholder(ExpressionEngine engine, String text) {
        int close = text.indexOf('>');
        if (close < 2) {
            return false;
        }
        String inner = text.substring(1, close);
        if (inner.indexOf(' ') >= 0 || inner.indexOf('<') >= 0) {
            return false;
        }
        int dot = inner.indexOf('.');
        String namespace = (dot < 0 ? inner : inner.substring(0, dot)).toLowerCase(Locale.ROOT);
        return engine.namespaces().contains(namespace);
    }

    public boolean test(double actual, SkillContext context, Target target) {
        double expected = value.asDouble(context, target);
        switch (operator) {
            case LT:
                return actual < expected;
            case LE:
                return actual <= expected;
            case GT:
                return actual > expected;
            case GE:
                return actual >= expected;
            case NE:
                // Doubles compared for equality need a tolerance, or
                // `health = 20` fails on a mob at 19.999999999999996.
                return Math.abs(actual - expected) > 1.0e-6d;
            case EQ:
            default:
                return Math.abs(actual - expected) <= 1.0e-6d;
        }
    }

    /** String comparison, for {@code name}, {@code biome} and friends. */
    public boolean testString(String actual, SkillContext context, Target target) {
        String expected = value.asString(context, target);
        boolean equal = actual != null && actual.equalsIgnoreCase(expected.trim());
        switch (operator) {
            case NE:
                return !equal;
            case LT:
                return actual != null && actual.compareToIgnoreCase(expected.trim()) < 0;
            case LE:
                return actual != null && actual.compareToIgnoreCase(expected.trim()) <= 0;
            case GT:
                return actual != null && actual.compareToIgnoreCase(expected.trim()) > 0;
            case GE:
                return actual != null && actual.compareToIgnoreCase(expected.trim()) >= 0;
            case EQ:
            default:
                return equal;
        }
    }

    public Operator operator() {
        return operator;
    }

    public Expression value() {
        return value;
    }

    @Override
    public String toString() {
        return source;
    }
}
