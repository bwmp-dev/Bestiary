package dev.bwmp.bestiary.api.skill;

/**
 * What happens.
 * <p>
 * Stateless: one instance is built per config line at load and shared by every
 * execution of it, so anything mutable belongs in {@link SkillContext} or on
 * the entity. The dispatcher — never the mechanic — owns cooldowns, conditions
 * and event cancellation, which is the trap Sigil closed the same way: an
 * ability that forgot to call {@code enforceCooldown} silently had no
 * cooldown.
 */
public interface Mechanic {

    MechanicMeta meta();

    /** Runs once per resolved target. */
    MechanicResult execute(SkillContext context, Target target);
}
