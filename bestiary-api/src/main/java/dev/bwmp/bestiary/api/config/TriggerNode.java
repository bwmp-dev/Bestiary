package dev.bwmp.bestiary.api.config;

import dev.bwmp.bestiary.api.skill.TriggerKind;

/** One parsed trigger, e.g. {@code ~onTimer:160}. Mob files only. */
public final class TriggerNode {

    private final TriggerKind kind;
    private final String parameter;

    public TriggerNode(TriggerKind kind, String parameter) {
        this.kind = kind;
        this.parameter = parameter == null ? "" : parameter;
    }

    public TriggerKind kind() {
        return kind;
    }

    /** The {@code :value} suffix, empty when absent. */
    public String parameter() {
        return parameter;
    }

    public long parameterAsTicks(long fallback) {
        if (parameter.isEmpty()) {
            return fallback;
        }
        try {
            return Durations.parseTicks(parameter);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public double parameterAsNumber(double fallback) {
        try {
            return Double.parseDouble(parameter);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String toShorthand() {
        return "~" + kind.written() + (parameter.isEmpty() ? "" : ":" + parameter);
    }

    @Override
    public String toString() {
        return toShorthand();
    }
}
