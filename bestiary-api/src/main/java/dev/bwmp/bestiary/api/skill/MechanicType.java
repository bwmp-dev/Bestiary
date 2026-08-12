package dev.bwmp.bestiary.api.skill;

/**
 * A factory that builds a {@link Mechanic} from parsed parameters.
 * <p>
 * Registered under a namespaced key, the same contract as Sigil's
 * {@code AbilityType}. A plugin registering a new type makes it available to
 * every skill file on the server, in both definition forms, with no parser
 * change — the parser only ever asks {@link #meta()} what the parameters are
 * called.
 */
public interface MechanicType {

    /**
     * Declared before anything is built, because the parser needs the alias
     * table to canonicalise keys.
     */
    MechanicMeta meta();

    /**
     * @throws IllegalArgumentException when a required parameter is missing or
     *                                  invalid; the message is reported against
     *                                  the offending file and YAML path
     */
    Mechanic create(MechanicConfig config);
}
