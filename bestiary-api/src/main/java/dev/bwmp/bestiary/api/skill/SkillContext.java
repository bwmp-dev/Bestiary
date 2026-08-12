package dev.bwmp.bestiary.api.skill;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.List;

/**
 * Everything one execution of a skill tree knows.
 * <p>
 * The same object a {@link Mechanic} receives and a {@link Condition} is
 * evaluated against. It carries the casting entity, whatever tripped the
 * trigger, the origin location, the current target list, skill-scoped variables
 * and a power multiplier that mechanics fold into their numeric parameters.
 * <p>
 * The guard budget lives here too, because the execution guards have to see
 * the whole tree: a {@code repeat} inside a {@code repeat} is only a
 * problem in aggregate, so the counter cannot live in a node.
 */
public interface SkillContext {

    /** Who is casting. Never null. */
    Entity caster();

    /** The caster when it is living, null otherwise. */
    LivingEntity casterLiving();

    /** What tripped the trigger — the damager, the interacting player. May be null. */
    Entity trigger();

    /** The trigger entity when it is a player, null otherwise. */
    Player triggerPlayer();

    /** Where the skill is considered to have come from. Never null. */
    Location origin();

    /** The targets this node is running against. Never null, possibly empty. */
    List<Target> targets();

    /** Folded into numeric parameters by mechanics that scale. Defaults to 1. */
    double power();

    /** The id of the skill currently executing, for error messages. */
    String skillId();

    /** How many nested skill calls deep this execution is. */
    int depth();

    Object variable(VariableScope scope, String name);

    void setVariable(VariableScope scope, String name, Object value);

    /** The Bukkit event that triggered this skill, or null. */
    Cancellable event();

    /** No-op when there is no event. */
    void cancelEvent();

    /**
     * Charges the per-execution mechanic budget.
     *
     * @return false when the budget is exhausted, at which point the caller
     *         must stop rather than truncate silently
     */
    boolean charge(int mechanics);

    /**
     * Runs another skill as a child of this execution, sharing the guard
     * budget and inheriting the depth.
     */
    MechanicResult runSkill(String skillId, List<Target> targets, double power);

    /** A view of this context with a different target list. */
    SkillContext withTargets(List<Target> targets);

    /** A view of this context with a different origin. */
    SkillContext withOrigin(Location origin);

    /** A view of this context with the power multiplier scaled. */
    SkillContext withPower(double power);

    /** Records a line of trace when {@code /bestiary debug} is watching. */
    void trace(String message);

    boolean tracing();
}
