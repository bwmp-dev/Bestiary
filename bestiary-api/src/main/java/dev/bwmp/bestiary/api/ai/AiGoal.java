package dev.bwmp.bestiary.api.ai;

import java.util.Set;

/**
 * One custom goal, in Bestiary's vocabulary rather than Paper's.
 * <p>
 * Mirrors Paper's {@code Goal} lifecycle exactly — bestiary-ai adapts one to
 * the other — so that a third-party goal compiles against bestiary-api and
 * never against a Paper class that may not exist on the running server.
 */
public interface AiGoal {

    /** Checked every few ticks by the selector. */
    boolean shouldActivate();

    default boolean shouldStayActive() {
        return shouldActivate();
    }

    default void start() {
    }

    void tick();

    default void stop() {
    }

    /**
     * Which selector slots this goal occupies. A goal claiming MOVE blocks
     * other MOVE goals of lower priority, exactly as vanilla does.
     */
    Set<GoalCategory> categories();
}
