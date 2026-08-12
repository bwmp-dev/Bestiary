package dev.bwmp.bestiary.api.ai;

import dev.bwmp.bestiary.api.config.Args;

/**
 * A factory that builds an {@link AiGoal} from a {@code goals:} entry.
 * <p>
 * Registered under a namespaced key, so third-party plugins contribute goals
 * the same way they contribute mechanics.
 */
public interface AiGoalType {

    /** One line describing the parameters, for {@code /bestiary info}. */
    default String describeParameters() {
        return "";
    }

    AiGoal create(AiContext context, Args args);
}
