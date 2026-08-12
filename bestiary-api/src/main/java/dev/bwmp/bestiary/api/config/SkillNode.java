package dev.bwmp.bestiary.api.config;

import dev.bwmp.bestiary.api.skill.ParameterSpec;

import java.util.List;

/**
 * One parsed mechanic line: what happens, to whom, only if, and when.
 * <p>
 * Both definition forms produce this, which is the whole point of carrying two
 * front-ends over one AST: one validator, one set of error messages, and a
 * graph the in-game editor can edit structurally.
 * <p>
 * A skill is a tree rather than a list because {@code skill} is itself a
 * mechanic, so this node's {@link #children()} carry inline sub-skills for the
 * flow mechanics that take them ({@code repeat}, {@code random_skill}).
 */
public final class SkillNode {

    private final String type;
    private final Args args;
    private final TargeterNode targeter;
    private final List<ConditionNode> conditions;
    private final TriggerNode trigger;
    private final List<SkillNode> children;
    private final String source;

    public SkillNode(String type, Args args, TargeterNode targeter, List<ConditionNode> conditions,
                     TriggerNode trigger, List<SkillNode> children, String source) {
        this.type = ParameterSpec.normalize(type);
        this.args = args == null ? Args.EMPTY : args;
        this.targeter = targeter;
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.trigger = trigger;
        this.children = children == null ? List.of() : List.copyOf(children);
        this.source = source == null ? "" : source;
    }

    public String type() {
        return type;
    }

    public Args args() {
        return args;
    }

    /** Null when the line inherits the enclosing target list. */
    public TargeterNode targeter() {
        return targeter;
    }

    public List<ConditionNode> conditions() {
        return conditions;
    }

    /** Null outside mob files. */
    public TriggerNode trigger() {
        return trigger;
    }

    /** Inline sub-skills, for flow mechanics that take a block. */
    public List<SkillNode> children() {
        return children;
    }

    /** File and YAML path, so an error names where it came from. */
    public String source() {
        return source;
    }

    public SkillNode withSource(String source) {
        return new SkillNode(type, args, targeter, conditions, trigger, children, source);
    }

    public SkillNode withChildren(List<SkillNode> children) {
        return new SkillNode(type, args, targeter, conditions, trigger, children, source);
    }

    public String toShorthand() {
        StringBuilder builder = new StringBuilder(type).append(args.toShorthand());
        if (targeter != null) {
            builder.append(' ').append(targeter.toShorthand());
        }
        if (trigger != null) {
            builder.append(' ').append(trigger.toShorthand());
        }
        for (ConditionNode condition : conditions) {
            builder.append(' ').append(condition.toShorthand());
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toShorthand();
    }
}
