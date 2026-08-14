package dev.bwmp.bestiary.drop;

import dev.bwmp.bestiary.skill.CompiledCondition;

import java.util.List;
import java.util.Locale;

/**
 * A named drop table.
 * <p>
 * Tables nest and entries carry conditions, so {@code shared_boss_loot} is
 * written once and referenced by every boss rather than pasted into each.
 */
public final class DropTable {

    public enum Mode {
        /** Every entry is rolled independently against its chance. */
        ALL,
        /** Exactly one entry, chosen by weight. */
        ONE_OF,
        /** {@code n} entries, chosen by weight without replacement. */
        N_OF;

        public static Mode parse(String value, Mode fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (Mode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            return fallback;
        }
    }

    public enum Distribution {
        PER_KILLER,
        SHARED;

        public static Distribution parse(String value, Distribution fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (Distribution distribution : values()) {
                if (distribution.name().equals(normalized)) {
                    return distribution;
                }
            }
            return fallback;
        }
    }

    private final String id;
    private final Mode mode;
    private final int count;
    private final Distribution distribution;
    private final List<CompiledCondition> conditions;
    private final double minimumDamageShare;
    private final List<DropEntry> entries;
    private final String source;

    public DropTable(String id, Mode mode, int count, Distribution distribution,
                     List<CompiledCondition> conditions, double minimumDamageShare,
                     List<DropEntry> entries, String source) {
        this.id = id;
        this.mode = mode;
        this.count = Math.max(1, count);
        this.distribution = distribution;
        this.conditions = List.copyOf(conditions);
        this.minimumDamageShare = minimumDamageShare;
        this.entries = List.copyOf(entries);
        this.source = source;
    }

    public String id() {
        return id;
    }

    public Mode mode() {
        return mode;
    }

    public int count() {
        return count;
    }

    public Distribution distribution() {
        return distribution;
    }

    /** Evaluated per killer, before any entry is rolled. */
    public List<CompiledCondition> conditions() {
        return conditions;
    }

    /**
     * Fraction of the mob's total damage a player must have dealt to qualify.
     * <p>
     * Lifted out of the generic condition list because damage attribution is
     * the one thing a drop table needs that no other condition can see — and it
     * is what makes "the person who tagged it first" not automatically the
     * person who gets the loot.
     */
    public double minimumDamageShare() {
        return minimumDamageShare;
    }

    public List<DropEntry> entries() {
        return entries;
    }

    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return id;
    }
}
