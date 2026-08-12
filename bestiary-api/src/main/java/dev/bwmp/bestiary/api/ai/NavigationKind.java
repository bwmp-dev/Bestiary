package dev.bwmp.bestiary.api.ai;

import java.util.Locale;
import java.util.Optional;

/**
 * What a mob's pathfinder is willing to path through.
 * <p>
 * Anything but {@link #DEFAULT} needs the NMS tier:
 * {@code PathNavigation} is not exposed by Bukkit or Paper at all. Below
 * 1.20.5 a request for another kind is reported and ignored rather than
 * silently doing nothing — on a floating-island world this is the most
 * valuable capability the plugin has, so failing quietly would be the worst
 * outcome.
 */
public enum NavigationKind {

    /** Whatever the base entity type came with. */
    DEFAULT,
    GROUND,
    FLYING,
    AMPHIBIOUS,
    CLIMBING;

    public boolean needsNms() {
        return this != DEFAULT;
    }

    public static Optional<NavigationKind> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (NavigationKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
