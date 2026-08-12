package dev.bwmp.bestiary.api.ai;

import dev.bwmp.bestiary.api.config.Args;

import java.util.List;
import java.util.Set;

/**
 * One entry of a mob's {@code goals:} list: either a goal to add, or a set of
 * vanilla categories to strip.
 * <p>
 * The list is applied in order at spawn, which is how {@code - clear: [MOVE,
 * TARGET]} followed by three goals makes a Ravager stop behaving like a
 * Ravager.
 */
public final class AiGoalNode {

    private final String type;
    private final Args args;
    private final Set<GoalCategory> clears;
    private final int priority;

    private AiGoalNode(String type, Args args, Set<GoalCategory> clears, int priority) {
        this.type = type == null ? "" : type;
        this.args = args == null ? Args.EMPTY : args;
        this.clears = clears == null ? Set.of() : Set.copyOf(clears);
        this.priority = priority;
    }

    public static AiGoalNode goal(String type, Args args, int priority) {
        return new AiGoalNode(type, args, Set.of(), priority);
    }

    public static AiGoalNode clear(Set<GoalCategory> categories) {
        return new AiGoalNode("", Args.EMPTY, categories, 0);
    }

    public static AiGoalNode clearAll() {
        return clear(Set.of(GoalCategory.values()));
    }

    public boolean isClear() {
        return type.isEmpty();
    }

    /** Namespaced, e.g. {@code bestiary:melee_attack}. */
    public String type() {
        return type;
    }

    public Args args() {
        return args;
    }

    public Set<GoalCategory> clears() {
        return clears;
    }

    /** Lower runs first, as in the vanilla selector. Defaults to list position. */
    public int priority() {
        return priority;
    }

    public static List<GoalCategory> allCategories() {
        return List.of(GoalCategory.values());
    }

    @Override
    public String toString() {
        return isClear() ? "clear " + clears : type + args.toShorthand() + " @" + priority;
    }
}
