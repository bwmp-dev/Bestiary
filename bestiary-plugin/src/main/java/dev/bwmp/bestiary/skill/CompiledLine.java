package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.config.TriggerNode;
import dev.bwmp.bestiary.api.skill.Mechanic;

import java.util.List;

public final class CompiledLine {

    private final String id;
    private final Mechanic mechanic;
    private final CompiledTargeter targeter;
    private final List<CompiledCondition> conditions;
    private final TriggerNode trigger;
    private final String path;

    public CompiledLine(String id, Mechanic mechanic, CompiledTargeter targeter,
                        List<CompiledCondition> conditions, TriggerNode trigger, String path) {
        this.id = id;
        this.mechanic = mechanic;
        this.targeter = targeter;
        this.conditions = List.copyOf(conditions);
        this.trigger = trigger;
        this.path = path;
    }

    public String id() {
        return id;
    }

    public Mechanic mechanic() {
        return mechanic;
    }

    /** Null when the line inherits the enclosing target list. */
    public CompiledTargeter targeter() {
        return targeter;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    /** Null outside mob definitions. */
    public TriggerNode trigger() {
        return trigger;
    }

    /** File and YAML path, named in every guard breach and every failure. */
    public String path() {
        return path;
    }

    @Override
    public String toString() {
        return id + (targeter == null ? "" : " " + targeter) + (trigger == null ? "" : " " + trigger);
    }
}
