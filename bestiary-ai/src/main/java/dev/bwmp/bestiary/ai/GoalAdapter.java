package dev.bwmp.bestiary.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import dev.bwmp.bestiary.api.ai.AiGoal;
import org.bukkit.entity.Mob;

import java.util.EnumSet;

/**
 * Presents a Bestiary {@link AiGoal} as a Paper {@code Goal}.
 * <p>
 * The lifecycles are deliberately identical, so this is a rename rather than an
 * adaptation — which is what keeps a third-party goal compiling against
 * bestiary-api and never against a Paper class that may not exist on the
 * running server.
 */
final class GoalAdapter implements Goal<Mob> {

    private final AiGoal goal;
    private final GoalKey<Mob> key;
    private final EnumSet<GoalType> types;

    GoalAdapter(AiGoal goal, GoalKey<Mob> key) {
        this.goal = goal;
        this.key = key;
        this.types = EnumSet.copyOf(PaperAiController.toGoalTypes(goal.categories()));
    }

    @Override
    public boolean shouldActivate() {
        return goal.shouldActivate();
    }

    @Override
    public boolean shouldStayActive() {
        return goal.shouldStayActive();
    }

    @Override
    public void start() {
        goal.start();
    }

    @Override
    public void stop() {
        goal.stop();
    }

    @Override
    public void tick() {
        goal.tick();
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return types;
    }
}
