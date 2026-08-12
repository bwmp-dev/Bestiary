package dev.bwmp.bestiary.api.skill;

/**
 * What a mechanic, targeter or condition can be pointed at.
 * <p>
 * Attaching a location condition to an entity slot is a load-time error rather
 * than a runtime surprise, and this is the type that makes that check possible.
 */
public enum TargetKind {

    /** Needs an entity. A location target is skipped with a warning. */
    ENTITY,

    /** Needs only a position. Entity targets supply theirs. */
    LOCATION,

    /** Works with either. */
    ANY,

    /** Runs once regardless of targets — flow control, messages to the caster. */
    NONE;

    public boolean accepts(Target target) {
        switch (this) {
            case ENTITY:
                return target != null && target.isEntity();
            case LOCATION:
            case ANY:
                return target != null;
            case NONE:
            default:
                return true;
        }
    }

    /** True when a slot declaring {@code required} can be fed by {@code this}. */
    public boolean satisfies(TargetKind required) {
        if (required == ANY || required == NONE || this == required) {
            return true;
        }
        return this == ENTITY && required == LOCATION;
    }
}
