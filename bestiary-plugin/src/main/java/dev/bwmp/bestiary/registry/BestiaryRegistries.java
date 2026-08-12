package dev.bwmp.bestiary.registry;

import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.keystone.config.Snapshot;
import dev.bwmp.keystone.registry.OwnedRegistry;

/**
 * The four extension registries and the content snapshot, in one place.
 * <p>
 * Extension registries are Keystone's {@code OwnedRegistry}, so an addon that
 * disables has its mechanics dropped centrally rather than leaving handlers
 * pointing at a dead classloader.
 */
public final class BestiaryRegistries {

    private final OwnedRegistry<MechanicType> mechanics;
    private final OwnedRegistry<TargeterType> targeters;
    private final OwnedRegistry<ConditionType> conditions;
    private final OwnedRegistry<AiGoalType> goals;

    private final TypeIndex<MechanicType> mechanicIndex;
    private final TypeIndex<TargeterType> targeterIndex;
    private final TypeIndex<ConditionType> conditionIndex;
    private final TypeIndex<AiGoalType> goalIndex;

    private final Snapshot<ContentSnapshot> content = new Snapshot<>(ContentSnapshot.EMPTY);

    public BestiaryRegistries(OwnedRegistry<MechanicType> mechanics,
                              OwnedRegistry<TargeterType> targeters,
                              OwnedRegistry<ConditionType> conditions,
                              OwnedRegistry<AiGoalType> goals) {
        this.mechanics = mechanics;
        this.targeters = targeters;
        this.conditions = conditions;
        this.goals = goals;
        this.mechanicIndex = new TypeIndex<>(mechanics, "mechanic");
        this.targeterIndex = new TypeIndex<>(targeters, "targeter");
        this.conditionIndex = new TypeIndex<>(conditions, "condition");
        this.goalIndex = new TypeIndex<>(goals, "AI goal");
    }

    public OwnedRegistry<MechanicType> mechanics() {
        return mechanics;
    }

    public OwnedRegistry<TargeterType> targeters() {
        return targeters;
    }

    public OwnedRegistry<ConditionType> conditions() {
        return conditions;
    }

    public OwnedRegistry<AiGoalType> goals() {
        return goals;
    }

    public TypeIndex<MechanicType> mechanicIndex() {
        return mechanicIndex;
    }

    public TypeIndex<TargeterType> targeterIndex() {
        return targeterIndex;
    }

    public TypeIndex<ConditionType> conditionIndex() {
        return conditionIndex;
    }

    public TypeIndex<AiGoalType> goalIndex() {
        return goalIndex;
    }

    public ContentSnapshot content() {
        return content.get();
    }

    public void publish(ContentSnapshot snapshot) {
        content.set(snapshot);
    }
}
