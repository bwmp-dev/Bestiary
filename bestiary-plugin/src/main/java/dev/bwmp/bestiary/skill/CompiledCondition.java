package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.skill.Condition;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.List;

/** A condition bound to its type, with negation applied by the engine. */
public final class CompiledCondition {

    private final String id;
    private final Condition condition;
    private final boolean negated;

    public CompiledCondition(String id, Condition condition, boolean negated) {
        this.id = id;
        this.condition = condition;
        this.negated = negated;
    }

    public boolean test(SkillContext context, Target target) {
        boolean result;
        try {
            result = condition.test(context, target);
        } catch (RuntimeException exception) {
            // A condition that throws is a broken condition, and the safe
            // reading of "only if" is "not". Reporting is the executor's job.
            throw new SkillFailure("condition '" + id + "' failed: " + exception, exception);
        }
        return negated != result;
    }

    public String id() {
        return id;
    }

    public Condition condition() {
        return condition;
    }

    public boolean negated() {
        return negated;
    }

    /** All of them, which is what a bare condition list means. */
    public static boolean allPass(List<CompiledCondition> conditions, SkillContext context, Target target) {
        for (CompiledCondition condition : conditions) {
            if (!condition.test(context, target)) {
                if (context != null && context.tracing()) {
                    context.trace("  condition " + condition.describe() + " -> false");
                }
                return false;
            }
        }
        return true;
    }

    public String describe() {
        return (negated ? "!" : "") + id;
    }

    @Override
    public String toString() {
        return describe();
    }
}
