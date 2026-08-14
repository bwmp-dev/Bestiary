package dev.bwmp.bestiary.skill;

import java.util.List;

public final class CompiledSkill {

    private final String id;
    private final long cooldownTicks;
    private final List<CompiledCondition> conditions;
    private final List<CompiledLine> lines;
    private final String source;
    private final boolean anonymous;

    public CompiledSkill(String id, long cooldownTicks, List<CompiledCondition> conditions,
                         List<CompiledLine> lines, String source, boolean anonymous) {
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.conditions = List.copyOf(conditions);
        this.lines = List.copyOf(lines);
        this.source = source;
        this.anonymous = anonymous;
    }

    public String id() {
        return id;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    /** Evaluated against the caster before any line runs. */
    public List<CompiledCondition> conditions() {
        return conditions;
    }

    public List<CompiledLine> lines() {
        return lines;
    }

    public String source() {
        return source;
    }

    /**
     * True for an inline {@code skills:} block lifted out of a flow mechanic.
     * <p>
     * Inline children become real skills under a synthetic id rather than
     * living inside their parent mechanic's config. That keeps every skill call
     * — nested or not — going through the same executor path, so the guards
     * see the whole tree, and it means {@code /bestiary info}
     * can show a nested block like any other skill.
     */
    public boolean anonymous() {
        return anonymous;
    }

    @Override
    public String toString() {
        return id;
    }
}
