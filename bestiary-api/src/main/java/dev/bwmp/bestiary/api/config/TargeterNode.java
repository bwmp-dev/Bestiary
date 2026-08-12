package dev.bwmp.bestiary.api.config;

import dev.bwmp.bestiary.api.skill.ParameterSpec;

/**
 * One parsed targeter, possibly composed over another.
 * <p>
 * {@code @ring{radius=4} of @playersInRadius{r=10}} is this node with
 * {@code type = ring} and {@link #source()} pointing at the inner one, so
 * composition needs no special case anywhere downstream.
 */
public final class TargeterNode {

    private final String type;
    private final Args args;
    private final TargeterNode source;

    public TargeterNode(String type, Args args, TargeterNode source) {
        this.type = ParameterSpec.normalize(type);
        this.args = args == null ? Args.EMPTY : args;
        this.source = source;
    }

    public String type() {
        return type;
    }

    public Args args() {
        return args;
    }

    /** The targeter this one resolves relative to, or null. */
    public TargeterNode source() {
        return source;
    }

    public String toShorthand() {
        return "@" + type + args.toShorthand() + (source == null ? "" : " of " + source.toShorthand());
    }

    @Override
    public String toString() {
        return toShorthand();
    }
}
