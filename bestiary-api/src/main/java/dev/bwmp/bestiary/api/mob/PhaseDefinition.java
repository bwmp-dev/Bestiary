package dev.bwmp.bestiary.api.mob;

import dev.bwmp.bestiary.api.config.ConditionNode;

import java.util.List;

/**
 * One named phase of a fight.
 * <p>
 * Phases are entered in declaration order: a mob sits in a phase until its
 * {@code until} conditions hold, then advances. {@code on_enter} and
 * {@code on_exit} name skills, which is how a transition gets an animation
 * without a bespoke trigger.
 */
public final class PhaseDefinition {

    private final String name;
    private final List<ConditionNode> until;
    private final String onEnter;
    private final String onExit;
    private final String bossbarTitle;

    public PhaseDefinition(String name, List<ConditionNode> until, String onEnter, String onExit,
                           String bossbarTitle) {
        this.name = name;
        this.until = until == null ? List.of() : List.copyOf(until);
        this.onEnter = onEnter == null ? "" : onEnter;
        this.onExit = onExit == null ? "" : onExit;
        this.bossbarTitle = bossbarTitle == null ? "" : bossbarTitle;
    }

    public String name() {
        return name;
    }

    /** Empty means the phase is terminal. */
    public List<ConditionNode> until() {
        return until;
    }

    public String onEnter() {
        return onEnter;
    }

    public String onExit() {
        return onExit;
    }

    /** Overrides the mob's bossbar title while this phase is active. */
    public String bossbarTitle() {
        return bossbarTitle;
    }

    @Override
    public String toString() {
        return name;
    }
}
