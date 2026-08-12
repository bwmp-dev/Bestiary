package dev.bwmp.bestiary.api.ai;

import java.util.Locale;
import java.util.Optional;

/**
 * The four kinds of goal a mob's selector holds.
 * <p>
 * Deliberately Bestiary's own enum rather than Paper's {@code GoalType}:
 * bestiary-plugin must not import a Paper type, because the same jar runs on
 * Spigot where that class does not exist. bestiary-ai maps between them.
 */
public enum GoalCategory {

    MOVE,
    LOOK,
    JUMP,
    TARGET;

    public static Optional<GoalCategory> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (GoalCategory category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
