package dev.bwmp.bestiary.api.config;

import java.util.List;

/** A named skill as written in config: a cooldown, its own conditions, and lines. */
public final class SkillDefinition {

    private final String id;
    private final long cooldownTicks;
    private final List<ConditionNode> conditions;
    private final List<SkillNode> lines;
    private final String source;
    private final int revision;

    public SkillDefinition(String id, long cooldownTicks, List<ConditionNode> conditions,
                           List<SkillNode> lines, String source, int revision) {
        this.id = id;
        this.cooldownTicks = Math.max(0L, cooldownTicks);
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.source = source == null ? "" : source;
        this.revision = revision;
    }

    public String id() {
        return id;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    /** Evaluated against the caster, deciding whether the skill runs at all. */
    public List<ConditionNode> conditions() {
        return conditions;
    }

    public List<SkillNode> lines() {
        return lines;
    }

    public String source() {
        return source;
    }

    /** Bumped whenever the definition is reloaded with different content. */
    public int revision() {
        return revision;
    }

    @Override
    public String toString() {
        return id + " (" + lines.size() + " line(s))";
    }
}
