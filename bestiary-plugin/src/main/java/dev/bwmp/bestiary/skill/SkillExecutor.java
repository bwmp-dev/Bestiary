package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.util.Throttle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Runs skill trees, and owns the four guards.
 * <p>
 * Guards were written before the executor rather than added to a working one,
 * which is why the budget lives on {@link Execution}
 * and is charged on the one path every mechanic goes through, instead of being
 * something each mechanic must remember to call.
 */
public final class SkillExecutor {

    private final BestiaryScheduler scheduler;
    private final Logger logger;
    private final Throttle failures = Throttle.perMinute();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private volatile ExecutionLimits limits = ExecutionLimits.DEFAULT;
    private volatile Function<String, CompiledSkill> skillLookup = id -> null;
    private volatile Function<Entity, BestiaryMob> mobLookup = entity -> null;
    private volatile Consumer<Map<String, Object>> globalVariableSink = values -> {
    };

    public SkillExecutor(BestiaryScheduler scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void bind(Function<String, CompiledSkill> skillLookup, Function<Entity, BestiaryMob> mobLookup) {
        this.skillLookup = skillLookup;
        this.mobLookup = mobLookup;
    }

    public void limits(ExecutionLimits limits) {
        this.limits = limits == null ? ExecutionLimits.DEFAULT : limits;
    }

    public ExecutionLimits limits() {
        return limits;
    }

    public Map<String, Object> globalVariables() {
        return globalVariables;
    }

    /** Called after a global variable changes, so storage can persist it. */
    public void onGlobalVariablesChanged(Consumer<Map<String, Object>> sink) {
        this.globalVariableSink = sink == null ? values -> {
        } : sink;
    }

    // --- entry points -----------------------------------------------------

    public boolean cast(String skillId, Entity caster, Entity trigger, Location origin,
                        List<Target> targets, double power, Cancellable event) {
        CompiledSkill skill = skill(skillId);
        if (skill == null) {
            reportThrottled(skillId, "no such skill");
            return false;
        }
        return cast(skill, caster, trigger, origin, targets, power, event, null);
    }

    public boolean cast(CompiledSkill skill, Entity caster, Entity trigger, Location origin,
                        List<Target> targets, double power, Cancellable event,
                        Consumer<String> tracer) {
        if (caster == null || !caster.isValid()) {
            return false;
        }
        Execution execution = new Execution(this, caster, trigger, origin, event);
        execution.tracer(tracer);
        execution.start(skill, targets == null ? List.of() : targets, power);
        return true;
    }

    public Execution newExecution(Entity caster, Entity trigger, Location origin, Cancellable event) {
        return new Execution(this, caster, trigger, origin, event);
    }

    // --- services the execution reaches back for ---------------------------

    CompiledSkill skill(String id) {
        return skillLookup.apply(id);
    }

    BestiaryMob mobOf(Entity entity) {
        return entity == null ? null : mobLookup.apply(entity);
    }

    Object globalVariable(String name) {
        return globalVariables.get(name);
    }

    void setGlobalVariable(String name, Object value) {
        if (value == null) {
            globalVariables.remove(name);
        } else {
            globalVariables.put(name, value);
        }
        globalVariableSink.accept(globalVariables);
    }

    /**
     * Resumes a suspended tree on the thread that owns the caster.
     * <p>
     * A skill whose caster died or unloaded during the pause is dropped, not
     * resumed against a stale entity — checked on resume rather than assumed.
     */
    void resumeLater(Execution execution, long delayTicks) {
        Entity caster = execution.caster();
        if (caster == null) {
            return;
        }
        scheduler.atEntityLater(caster, () -> {
            if (!execution.casterAlive() || execution.finished()) {
                return;
            }
            execution.run();
        }, Math.max(1L, delayTicks));
    }

    // --- cooldowns --------------------------------------------------------

    /**
     * Cooldowns live here, not in the mechanics.
     * <p>
     * Sigil hit the trap first: an ability that forgot to call
     * {@code enforceCooldown} silently had no cooldown. The same trap exists
     * for skills and is closed the same way — the executor is the only thing
     * that starts or checks one, on the single path every cast goes through.
     */
    public boolean onCooldown(Entity caster, String skillId) {
        Long until = cooldowns.get(cooldownKey(caster, skillId));
        return until != null && until > System.currentTimeMillis();
    }

    public long cooldownRemainingMillis(Entity caster, String skillId) {
        Long until = cooldowns.get(cooldownKey(caster, skillId));
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    void startCooldown(Entity caster, String skillId, long ticks) {
        if (ticks <= 0L || caster == null) {
            return;
        }
        cooldowns.put(cooldownKey(caster, skillId), System.currentTimeMillis() + ticks * 50L);
    }

    public void clearCooldowns(Entity caster) {
        String prefix = caster.getUniqueId() + "|";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Expired entries are never removed by the normal path, so they are swept. */
    public void purgeCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(until -> until <= now);
    }

    private static String cooldownKey(Entity caster, String skillId) {
        return (caster == null ? "?" : caster.getUniqueId().toString()) + "|"
                + (skillId == null ? "" : skillId.toLowerCase(java.util.Locale.ROOT));
    }

    void report(String source, String message) {
        logger.warning("[skill] " + source + ": " + message);
    }

    void reportThrottled(String source, String message) {
        if (failures.allow(source + "|" + message)) {
            logger.warning("[skill] " + source + ": " + message);
        }
    }

    public void resetThrottles() {
        failures.reset();
    }

    public BestiaryScheduler scheduler() {
        return scheduler;
    }
}
