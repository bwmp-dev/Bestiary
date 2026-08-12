package dev.bwmp.bestiary.api.skill;

import java.util.Locale;

/** Where a variable lives, and therefore how long it survives. */
public enum VariableScope {

    /** One execution of one skill tree. Gone when it finishes. */
    SKILL,

    /** Attached to the casting mob, persisted in its PDC. */
    MOB,

    /** Attached to the target entity, if it is a Bestiary mob. */
    TARGET,

    /** Server-wide, persisted in storage. */
    GLOBAL;

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static VariableScope parse(String value, VariableScope fallback) {
        if (value == null) {
            return fallback;
        }
        for (VariableScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value.trim())) {
                return scope;
            }
        }
        return fallback;
    }
}
