package dev.bwmp.bestiary.api.skill;

/**
 * Only if.
 * <p>
 * One vocabulary, evaluated against a caster, a target or a location depending
 * on where it is attached. Negation is applied by the engine, not by the
 * condition, so {@code ?!onGround} needs no cooperation from
 * {@code on_ground}.
 */
public interface Condition {

    ConditionMeta meta();

    boolean test(SkillContext context, Target target);
}
