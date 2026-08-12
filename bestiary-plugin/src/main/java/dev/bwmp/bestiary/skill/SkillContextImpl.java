package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.VariableScope;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.List;

/**
 * The per-frame view of an {@link Execution}.
 * <p>
 * Cheap to derive: {@code withTargets}, {@code withOrigin} and
 * {@code withPower} share the execution, so variables, the guard budget and the
 * trace sink are common to the whole tree while targets and origin are local to
 * a line.
 */
public final class SkillContextImpl implements SkillContext {

    private final Execution execution;
    private final String skillId;
    private final int depth;
    private final List<Target> targets;
    private final Location origin;
    private final double power;

    SkillContextImpl(Execution execution, String skillId, int depth, List<Target> targets,
                     Location origin, double power) {
        this.execution = execution;
        this.skillId = skillId;
        this.depth = depth;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.origin = origin;
        this.power = power;
    }

    public Execution execution() {
        return execution;
    }

    @Override
    public Entity caster() {
        return execution.caster();
    }

    @Override
    public LivingEntity casterLiving() {
        Entity caster = execution.caster();
        return caster instanceof LivingEntity ? (LivingEntity) caster : null;
    }

    @Override
    public Entity trigger() {
        return execution.trigger();
    }

    @Override
    public Player triggerPlayer() {
        Entity trigger = execution.trigger();
        return trigger instanceof Player ? (Player) trigger : null;
    }

    @Override
    public Location origin() {
        return origin != null ? origin.clone() : execution.origin();
    }

    @Override
    public List<Target> targets() {
        return targets;
    }

    @Override
    public double power() {
        return power;
    }

    @Override
    public String skillId() {
        return skillId;
    }

    @Override
    public int depth() {
        return depth;
    }

    @Override
    public Object variable(VariableScope scope, String name) {
        return execution.variable(scope, name, this);
    }

    @Override
    public void setVariable(VariableScope scope, String name, Object value) {
        execution.setVariable(scope, name, value, this);
    }

    @Override
    public Cancellable event() {
        return execution.event();
    }

    @Override
    public void cancelEvent() {
        Cancellable event = execution.event();
        if (event != null) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean charge(int mechanics) {
        return execution.charge(mechanics);
    }

    @Override
    public MechanicResult runSkill(String childSkillId, List<Target> childTargets, double childPower) {
        return execution.pushSkill(childSkillId, childTargets == null ? targets : childTargets,
                origin(), power * childPower, depth + 1);
    }

    @Override
    public SkillContext withTargets(List<Target> replacement) {
        return new SkillContextImpl(execution, skillId, depth, replacement, origin, power);
    }

    @Override
    public SkillContext withOrigin(Location replacement) {
        return new SkillContextImpl(execution, skillId, depth, targets, replacement, power);
    }

    @Override
    public SkillContext withPower(double replacement) {
        return new SkillContextImpl(execution, skillId, depth, targets, origin, replacement);
    }

    @Override
    public void trace(String message) {
        execution.trace(message);
    }

    @Override
    public boolean tracing() {
        return execution.tracing();
    }

    /** The Bestiary mob casting this, or null when the caster is a player or plain entity. */
    public BestiaryMob casterMob() {
        return execution.mobOf(caster());
    }

    SkillContextImpl derive(String childSkillId, int childDepth, List<Target> childTargets,
                            Location childOrigin, double childPower) {
        return new SkillContextImpl(execution, childSkillId, childDepth, childTargets, childOrigin, childPower);
    }
}
