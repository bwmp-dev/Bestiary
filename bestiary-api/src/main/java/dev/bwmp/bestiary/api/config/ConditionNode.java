package dev.bwmp.bestiary.api.config;

import dev.bwmp.bestiary.api.skill.ParameterSpec;

/** One parsed condition, before it is bound to a {@code ConditionType}. */
public final class ConditionNode {

    private final String type;
    private final Args args;
    private final boolean negated;

    public ConditionNode(String type, Args args, boolean negated) {
        this.type = ParameterSpec.normalize(type);
        this.args = args == null ? Args.EMPTY : args;
        this.negated = negated;
    }

    public String type() {
        return type;
    }

    public Args args() {
        return args;
    }

    /** Applied by the engine, so no condition implements its own negation. */
    public boolean negated() {
        return negated;
    }

    public String toShorthand() {
        return "?" + (negated ? "!" : "") + type + args.toShorthand();
    }

    @Override
    public String toString() {
        return toShorthand();
    }
}
