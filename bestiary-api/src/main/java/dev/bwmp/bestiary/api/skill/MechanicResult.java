package dev.bwmp.bestiary.api.skill;

/**
 * What a mechanic did, and what the enclosing skill should do about it.
 * <p>
 * Mirrors Sigil's {@code ActionResult} for the same reason: a boolean conflates
 * "I wasn't interested" with "I tried and couldn't", and neither of those is
 * "stop the rest of this skill". {@link #HALT} lets a failed line abort a skill
 * cleanly rather than by exception.
 */
public enum MechanicResult {

    /** Nothing to do here. The skill continues. */
    PASS,

    /** Ran. The skill continues. */
    SUCCESS,

    /** Tried and could not. The skill continues. */
    FAIL,

    /** Stops the remaining lines of the enclosing skill. */
    HALT;

    public boolean fired() {
        return this == SUCCESS;
    }

    public boolean halts() {
        return this == HALT;
    }
}
