package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.VariableScope;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One execution of one skill tree, walked iteratively over an explicit frame
 * stack rather than by recursion.
 * <p>
 * That is the design decision the guards rest on. With a recursive walk, the
 * wall-clock guard could only ever abort — there is no way
 * to suspend a Java call stack and resume it next tick. With an explicit stack,
 * suspension is just "stop looping and reschedule", and it works identically at
 * depth 1 and depth 30. {@code delay} and the sync/async hops fall out of the
 * same mechanism for free.
 * <p>
 * The cost is that {@code SkillContext#runSkill} pushes a frame and returns
 * before the child has run. Ordering is preserved — the child frame is on top,
 * so it runs before the parent's next step — but a flow mechanic cannot see its
 * child's result. That is not a loss: {@code HALT} is defined as stopping the
 * <em>enclosing</em> skill, so it never needed to propagate outwards.
 */
public final class Execution {

    static final class Frame {

        private final CompiledSkill skill;
        private final SkillContextImpl context;
        private int lineIndex;
        private List<Target> lineTargets;
        private int targetIndex;
        private boolean halted;

        Frame(CompiledSkill skill, SkillContextImpl context) {
            this.skill = skill;
            this.context = context;
        }
    }

    private final SkillExecutor executor;
    private final Entity caster;
    private final Entity trigger;
    private final Location origin;
    private final Cancellable event;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Map<String, Object> skillVariables = new HashMap<>();

    private int mechanicsUsed;
    private boolean aborted;
    private long pendingDelayTicks;
    private Consumer<String> tracer;

    Execution(SkillExecutor executor, Entity caster, Entity trigger, Location origin, Cancellable event) {
        // A casterless execution is legitimate — evaluating a spawn's
        // conditions, or rolling a drop table from the console — but then the
        // origin is the only thing that says where "here" is, so it has to be
        // given rather than derived.
        if (caster == null && origin == null) {
            throw new IllegalArgumentException("an execution needs a caster or an origin");
        }
        this.executor = executor;
        this.caster = caster;
        this.trigger = trigger;
        this.origin = origin == null ? caster.getLocation() : origin.clone();
        this.event = event;
    }

    // --- accessors used by SkillContextImpl -------------------------------

    Entity caster() {
        return caster;
    }

    Entity trigger() {
        return trigger;
    }

    Location origin() {
        return origin.clone();
    }

    Cancellable event() {
        return event;
    }

    BestiaryMob mobOf(Entity entity) {
        return executor.mobOf(entity);
    }

    void trace(String message) {
        if (tracer != null) {
            tracer.accept(message);
        }
    }

    boolean tracing() {
        return tracer != null;
    }

    public void tracer(Consumer<String> tracer) {
        this.tracer = tracer;
    }

    Object variable(VariableScope scope, String name, SkillContextImpl context) {
        switch (scope) {
            case SKILL:
                return skillVariables.get(name);
            case MOB: {
                BestiaryMob mob = executor.mobOf(caster);
                return mob == null ? null : mob.variables().get(name);
            }
            case TARGET: {
                List<Target> targets = context.targets();
                if (targets.isEmpty() || !targets.get(0).isEntity()) {
                    return null;
                }
                BestiaryMob mob = executor.mobOf(targets.get(0).entity());
                return mob == null ? null : mob.variables().get(name);
            }
            case GLOBAL:
            default:
                return executor.globalVariable(name);
        }
    }

    void setVariable(VariableScope scope, String name, Object value, SkillContextImpl context) {
        switch (scope) {
            case SKILL:
                skillVariables.put(name, value);
                break;
            case MOB: {
                BestiaryMob mob = executor.mobOf(caster);
                if (mob != null) {
                    mob.variables().put(name, value);
                }
                break;
            }
            case TARGET: {
                List<Target> targets = context.targets();
                if (!targets.isEmpty() && targets.get(0).isEntity()) {
                    BestiaryMob mob = executor.mobOf(targets.get(0).entity());
                    if (mob != null) {
                        mob.variables().put(name, value);
                    }
                }
                break;
            }
            case GLOBAL:
            default:
                executor.setGlobalVariable(name, value);
                break;
        }
    }

    boolean charge(int mechanics) {
        mechanicsUsed += mechanics;
        if (mechanicsUsed > executor.limits().maxMechanics()) {
            abort("mechanic budget of " + executor.limits().maxMechanics() + " exceeded");
            return false;
        }
        return true;
    }

    // --- mechanics called from inside a running mechanic -------------------

    /** Pauses the whole tree. The remainder resumes at the caster's region. */
    public void delay(long ticks) {
        this.pendingDelayTicks = Math.max(pendingDelayTicks, ticks);
    }

    /** Drops every remaining line at every depth. What {@code cancel_skill} means. */
    public void cancelAll() {
        stack.clear();
        aborted = true;
    }

    /** Drops the remaining lines of the innermost skill only. */
    public void stopCurrent() {
        Frame frame = stack.peek();
        if (frame != null) {
            frame.halted = true;
        }
    }

    MechanicResult pushSkill(String skillId, List<Target> targets, Location childOrigin,
                             double power, int depth) {
        if (depth > executor.limits().maxDepth()) {
            abort("recursion depth of " + executor.limits().maxDepth() + " exceeded at skill '" + skillId + "'");
            return MechanicResult.FAIL;
        }
        CompiledSkill skill = executor.skill(skillId);
        if (skill == null) {
            executor.report(currentSkillId(), "calls unknown skill '" + skillId + "'");
            return MechanicResult.FAIL;
        }
        if (!claimCooldown(skill)) {
            return MechanicResult.FAIL;
        }
        SkillContextImpl context = new SkillContextImpl(this, skill.id(), depth, targets, childOrigin, power);
        if (!CompiledCondition.allPass(skill.conditions(), context, casterTarget())) {
            return MechanicResult.FAIL;
        }
        stack.push(new Frame(skill, context));
        return MechanicResult.SUCCESS;
    }

    // --- the loop ---------------------------------------------------------

    void start(CompiledSkill skill, List<Target> targets, double power) {
        if (!claimCooldown(skill)) {
            return;
        }
        SkillContextImpl context = new SkillContextImpl(this, skill.id(), 1, targets, origin, power);
        if (!CompiledCondition.allPass(skill.conditions(), context, casterTarget())) {
            return;
        }
        stack.push(new Frame(skill, context));
        run();
    }

    /**
     * Cooldowns are claimed here, on the one path every cast goes through, so
     * no mechanic can forget to enforce one.
     */
    private boolean claimCooldown(CompiledSkill skill) {
        if (skill.cooldownTicks() <= 0L) {
            return true;
        }
        if (executor.onCooldown(caster, skill.id())) {
            return false;
        }
        executor.startCooldown(caster, skill.id(), skill.cooldownTicks());
        return true;
    }

    void run() {
        long deadline = System.nanoTime() + executor.limits().budgetNanos();

        while (!aborted && !stack.isEmpty()) {
            if (pendingDelayTicks > 0) {
                long ticks = pendingDelayTicks;
                pendingDelayTicks = 0;
                executor.resumeLater(this, ticks);
                return;
            }
            if (System.nanoTime() > deadline) {
                executor.reportThrottled(currentSkillId(),
                        "exceeded the " + executor.limits().budgetMillis()
                                + " ms tick budget; suspended and resumed next tick");
                executor.resumeLater(this, 1L);
                return;
            }

            Frame frame = stack.peek();
            if (frame.halted || frame.lineIndex >= frame.skill.lines().size()) {
                stack.pop();
                continue;
            }

            CompiledLine line = frame.skill.lines().get(frame.lineIndex);

            if (frame.lineTargets == null) {
                if (!evaluateLine(frame, line)) {
                    continue;
                }
            }

            if (frame.targetIndex >= frame.lineTargets.size()) {
                frame.lineIndex++;
                frame.lineTargets = null;
                frame.targetIndex = 0;
                continue;
            }

            Target target = frame.lineTargets.get(frame.targetIndex++);
            if (!charge(1)) {
                return;
            }
            if (!line.mechanic().meta().requires().accepts(target)) {
                // A location mechanic fed an entity target is fine; the reverse
                // is not, and skipping is better than a class cast at runtime.
                continue;
            }

            runMechanic(frame, line, target);
        }
    }

    private boolean evaluateLine(Frame frame, CompiledLine line) {
        try {
            if (!CompiledCondition.allPass(line.conditions(), frame.context, casterTarget())) {
                frame.lineIndex++;
                return false;
            }
        } catch (SkillFailure failure) {
            executor.reportThrottled(line.path(), failure.getMessage());
            frame.lineIndex++;
            return false;
        }

        List<Target> resolved;
        try {
            resolved = resolveTargets(frame, line);
        } catch (SkillFailure failure) {
            executor.reportThrottled(line.path(), failure.getMessage());
            frame.lineIndex++;
            return false;
        }

        frame.lineTargets = resolved;
        frame.targetIndex = 0;
        return true;
    }

    private List<Target> resolveTargets(Frame frame, CompiledLine line) {
        if (line.targeter() != null) {
            return line.targeter().resolve(frame.context);
        }
        List<Target> inherited = frame.context.targets();

        // A mechanic that declares it needs no target still runs once per
        // resolved target unless this is here. `delay` under a targeter that
        // found eight players would otherwise pause eight times, and `skill`
        // would push eight identical frames.
        if (line.mechanic().meta().requires() == dev.bwmp.bestiary.api.skill.TargetKind.NONE) {
            Target single = inherited.isEmpty() ? casterTarget() : inherited.get(0);
            return single == null ? List.of() : List.of(single);
        }

        if (!inherited.isEmpty()) {
            return inherited;
        }
        // No targeter and nothing inherited means the caster, which is what
        // makes `- sound{s=...}` on its own do the obvious thing.
        Target self = casterTarget();
        return self == null ? List.of() : List.of(self);
    }

    private void runMechanic(Frame frame, CompiledLine line, Target target) {
        SkillContextImpl lineContext = frame.context.derive(frame.skill.id(), frame.context.depth(),
                frame.lineTargets, frame.context.origin(), frame.context.power());
        if (tracing()) {
            trace("  " + line.id() + " -> " + target);
        }

        MechanicResult result;
        try {
            result = line.mechanic().execute(lineContext, target);
        } catch (SkillFailure failure) {
            executor.reportThrottled(line.path(), failure.getMessage());
            result = MechanicResult.FAIL;
        } catch (RuntimeException exception) {
            executor.reportThrottled(line.path(),
                    "mechanic '" + line.id() + "' threw " + exception);
            result = MechanicResult.FAIL;
        }

        if (result == MechanicResult.HALT) {
            frame.halted = true;
        }
    }

    private void abort(String reason) {
        aborted = true;
        stack.clear();
        executor.reportThrottled(currentSkillId(), reason);
    }

    private String currentSkillId() {
        Frame frame = stack.peek();
        return frame == null ? "?" : frame.skill.id();
    }

    /**
     * A context with no skill behind it, for evaluating a condition list
     * outside a skill — phase transitions, spawner gating, drop tables.
     */
    public SkillContextImpl contextForConditions() {
        return new SkillContextImpl(this, "(conditions)", 1, List.of(), origin, 1.0d);
    }

    Target casterTarget() {
        return caster != null && caster.isValid() ? Target.of(caster) : null;
    }

    boolean casterAlive() {
        return caster != null && caster.isValid();
    }

    boolean finished() {
        return aborted || stack.isEmpty();
    }

    /** For {@code /bestiary debug}: the path of frames currently open. */
    public List<String> stackDescription() {
        List<String> description = new ArrayList<>();
        for (Frame frame : stack) {
            description.add(frame.skill.id() + "[" + frame.lineIndex + "]");
        }
        return description;
    }
}
