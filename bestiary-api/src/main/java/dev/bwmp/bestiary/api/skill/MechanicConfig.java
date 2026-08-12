package dev.bwmp.bestiary.api.skill;

import java.util.List;
import java.util.Set;

/**
 * Read-only, alias-resolved parameters for one mechanic, targeter or condition
 * line.
 * <p>
 * Deliberately not a Bukkit {@code ConfigurationSection}: this is a published
 * API surface, and narrowing it to typed getters with fallbacks means an
 * extension never has to handle a missing or mistyped key. Keys reaching here
 * have already been lowercased, had underscores stripped and had parameter
 * aliases applied, so {@code ignoreArmor} and {@code ignore_armor} are the same
 * key by the time a mechanic asks for it.
 */
public interface MechanicConfig {

    MechanicConfig EMPTY = new EmptyMechanicConfig();

    boolean contains(String key);

    Set<String> keys();

    /** The raw source text of a value, before any placeholder resolution. */
    String raw(String key, String fallback);

    /** A numeric parameter, evaluated per execution. */
    Expression number(String key, double fallback);

    /** A string parameter, placeholder-substituted per execution. */
    Expression text(String key, String fallback);

    /**
     * A parameter that must be present.
     *
     * @throws IllegalArgumentException when absent; the message is reported
     *                                  against the offending file
     */
    Expression require(String key);

    /** A load-time constant. Placeholders are not resolved. */
    int integer(String key, int fallback);

    double decimal(String key, double fallback);

    boolean bool(String key, boolean fallback);

    /**
     * A duration in ticks. A bare integer is ticks; {@code t}, {@code s},
     * {@code m} and {@code h} suffixes are ticks, seconds, minutes, hours.
     */
    long ticks(String key, long fallback);

    List<String> stringList(String key);

    /** A nested {@code args} block, or {@link #EMPTY}. */
    MechanicConfig section(String key);

    <E extends Enum<E>> E enumValue(Class<E> type, String key, E fallback);

    /** Where this block came from, for error messages. */
    String source();

    final class EmptyMechanicConfig implements MechanicConfig {

        private EmptyMechanicConfig() {
        }

        @Override
        public boolean contains(String key) {
            return false;
        }

        @Override
        public Set<String> keys() {
            return Set.of();
        }

        @Override
        public String raw(String key, String fallback) {
            return fallback;
        }

        @Override
        public Expression number(String key, double fallback) {
            return Constant.of(fallback);
        }

        @Override
        public Expression text(String key, String fallback) {
            return Constant.of(fallback);
        }

        @Override
        public Expression require(String key) {
            throw new IllegalArgumentException("required parameter '" + key + "' is missing");
        }

        @Override
        public int integer(String key, int fallback) {
            return fallback;
        }

        @Override
        public double decimal(String key, double fallback) {
            return fallback;
        }

        @Override
        public boolean bool(String key, boolean fallback) {
            return fallback;
        }

        @Override
        public long ticks(String key, long fallback) {
            return fallback;
        }

        @Override
        public List<String> stringList(String key) {
            return List.of();
        }

        @Override
        public MechanicConfig section(String key) {
            return this;
        }

        @Override
        public <E extends Enum<E>> E enumValue(Class<E> type, String key, E fallback) {
            return fallback;
        }

        @Override
        public String source() {
            return "<none>";
        }
    }
}
