package dev.bwmp.bestiary.api.skill;

/**
 * A parameter value resolved at execution time.
 * <p>
 * Every numeric and string parameter is one of these rather than a constant,
 * because {@code damage{amount=<caster.level> * 2.5 + <random.1to4>}} has to
 * work. Constants are just expressions that ignore their input, so a mechanic
 * never branches on whether its parameter happened to be literal.
 * <p>
 * Resolution order is pinned: every {@code <...>} placeholder is substituted to
 * its string value first, then — in numeric contexts only — the result is
 * parsed as an infix expression. String contexts get substitution only.
 */
public interface Expression {

    double asDouble(SkillContext context, Target target);

    default int asInt(SkillContext context, Target target) {
        return (int) Math.round(asDouble(context, target));
    }

    default long asLong(SkillContext context, Target target) {
        return Math.round(asDouble(context, target));
    }

    default float asFloat(SkillContext context, Target target) {
        return (float) asDouble(context, target);
    }

    /** Substitution only; never arithmetic. */
    String asString(SkillContext context, Target target);

    default boolean asBoolean(SkillContext context, Target target) {
        String value = asString(context, target).trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
            return false;
        }
        return asDouble(context, target) != 0.0d;
    }

    /** True when this expression contains no placeholders and no arithmetic. */
    boolean isConstant();

    /** The source text, for error messages and for the GUI's re-serialisation. */
    String source();
}
