package dev.bwmp.bestiary.api.skill;

/**
 * Where a condition list is attached, which decides what it is evaluated
 * against.
 * <p>
 * MythicMobs' {@code conditions} / {@code targetconditions} /
 * {@code triggerconditions} split is the same idea. Naming them by attachment
 * point rather than by list makes it obvious which one is wanted.
 */
public enum ConditionSlot {

    /** On the skill: does it run at all. Evaluated against the caster. */
    SKILL,

    /** On a mechanic line: does this line run. Evaluated against the caster. */
    LINE,

    /** Inside a targeter's filter: which targets survive. */
    TARGET,

    /** On a trigger: does the trigger fire. Evaluated against the trigger entity. */
    TRIGGER
}
