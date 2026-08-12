package dev.bwmp.bestiary.drop;

import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.skill.CompiledCondition;

import java.util.List;

/** One row of a drop table. */
public final class DropEntry {

    public enum Kind {
        ITEM,
        TABLE,
        EXP,
        CURRENCY,
        COMMAND,
        QUEST
    }

    private final Kind kind;
    private final String id;
    private final Expression amount;
    private final double chance;
    private final double weight;
    private final List<CompiledCondition> conditions;

    public DropEntry(Kind kind, String id, Expression amount, double chance, double weight,
                     List<CompiledCondition> conditions) {
        this.kind = kind;
        this.id = id;
        this.amount = amount;
        this.chance = chance;
        this.weight = weight <= 0 ? 1.0d : weight;
        this.conditions = List.copyOf(conditions);
    }

    public Kind kind() {
        return kind;
    }

    /** An item id, a nested table id, or a command line. */
    public String id() {
        return id;
    }

    public Expression amount() {
        return amount;
    }

    /** 0..1. Rolled per qualifying player under {@code per_killer}. */
    public double chance() {
        return chance;
    }

    /** Only consulted under {@code one_of} and {@code n_of}. */
    public double weight() {
        return weight;
    }

    public List<CompiledCondition> conditions() {
        return conditions;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + id;
    }
}
