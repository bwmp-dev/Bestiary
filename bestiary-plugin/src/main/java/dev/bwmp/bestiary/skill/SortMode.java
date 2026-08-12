package dev.bwmp.bestiary.skill;

import java.util.Locale;

/** How a targeter orders its results before {@code limit} truncates them. */
public enum SortMode {

    /** Declaration order, whatever the underlying lookup produced. */
    NONE,
    NEAREST,
    FARTHEST,
    RANDOM,
    /** Highest threat first. Falls back to nearest when the mob has no table. */
    THREAT,
    LOWEST_HEALTH,
    HIGHEST_HEALTH;

    public static SortMode parse(String value, SortMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (SortMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return fallback;
    }
}
