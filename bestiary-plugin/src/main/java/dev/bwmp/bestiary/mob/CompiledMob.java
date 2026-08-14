package dev.bwmp.bestiary.mob;

import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.mob.PhaseDefinition;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.CompiledSkill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A mob definition with its trigger bindings and phases bound.
 * <p>
 * The polling rule is decided here rather than at runtime:
 * {@link #pollPeriodTicks()} is zero unless the definition declares a polled
 * trigger, and otherwise it is the GCD of the declared intervals. A mob with
 * one {@code ~onTimer:160} costs one task every 160 ticks, not a per-tick task
 * that checks a counter.
 */
public final class CompiledMob {

    /** One trigger binding: a kind, its parameter, and what to run. */
    public static final class Binding {

        private final TriggerKind kind;
        private final String parameter;
        private final CompiledSkill skill;
        private final long periodTicks;
        private final double threshold;

        private Binding(TriggerKind kind, String parameter, CompiledSkill skill, long periodTicks,
                        double threshold) {
            this.kind = kind;
            this.parameter = parameter;
            this.skill = skill;
            this.periodTicks = periodTicks;
            this.threshold = threshold;
        }

        public static Binding of(TriggerKind kind, String parameter, CompiledSkill skill,
                                 long periodTicks, double threshold) {
            return new Binding(kind, parameter, skill, periodTicks, threshold);
        }

        public TriggerKind kind() {
            return kind;
        }

        public String parameter() {
            return parameter;
        }

        public CompiledSkill skill() {
            return skill;
        }

        /** Only meaningful for {@link TriggerKind#TIMER}. */
        public long periodTicks() {
            return periodTicks;
        }

        /** Only meaningful for {@link TriggerKind#HEALTH_THRESHOLD}, as a percentage. */
        public double threshold() {
            return threshold;
        }

        @Override
        public String toString() {
            return kind.written() + (parameter.isEmpty() ? "" : ":" + parameter);
        }
    }

    public static final class Phase {

        private final PhaseDefinition definition;
        private final List<CompiledCondition> until;

        private Phase(PhaseDefinition definition, List<CompiledCondition> until) {
            this.definition = definition;
            this.until = List.copyOf(until);
        }

        public static Phase of(PhaseDefinition definition, List<CompiledCondition> until) {
            return new Phase(definition, until);
        }

        public PhaseDefinition definition() {
            return definition;
        }

        public List<CompiledCondition> until() {
            return until;
        }

        public boolean terminal() {
            return until.isEmpty();
        }
    }

    private final MobDefinition definition;
    private final Map<TriggerKind, List<Binding>> bindings;
    private final List<Phase> phases;
    private final long pollPeriodTicks;
    private final double playerNearRange;
    private final String renderedName;

    public CompiledMob(MobDefinition definition, List<Binding> bindings, List<Phase> phases,
                       double playerNearRange, String renderedName) {
        this.definition = definition;
        Map<TriggerKind, List<Binding>> grouped = new EnumMap<>(TriggerKind.class);
        for (Binding binding : bindings) {
            grouped.computeIfAbsent(binding.kind(), kind -> new ArrayList<>()).add(binding);
        }
        this.bindings = Map.copyOf(grouped);
        this.phases = List.copyOf(phases);
        this.playerNearRange = playerNearRange;
        this.renderedName = renderedName;
        this.pollPeriodTicks = computePollPeriod(bindings);
    }

    private static long computePollPeriod(List<Binding> bindings) {
        long period = 0L;
        for (Binding binding : bindings) {
            if (!binding.kind().polled()) {
                continue;
            }
            long own = binding.kind() == TriggerKind.TIMER ? Math.max(1L, binding.periodTicks()) : 20L;
            period = period == 0L ? own : gcd(period, own);
        }
        return period;
    }

    private static long gcd(long a, long b) {
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.max(1L, a);
    }

    public MobDefinition definition() {
        return definition;
    }

    public List<Binding> bindings(TriggerKind kind) {
        return bindings.getOrDefault(kind, List.of());
    }

    public boolean declares(TriggerKind kind) {
        return bindings.containsKey(kind);
    }

    public List<Phase> phases() {
        return phases;
    }

    /** Zero when nothing needs polling, which is the common case. */
    public long pollPeriodTicks() {
        return pollPeriodTicks;
    }

    public double playerNearRange() {
        return playerNearRange;
    }

    /** The display name, rendered once at load rather than once per tick. */
    public String renderedName() {
        return renderedName;
    }
}
