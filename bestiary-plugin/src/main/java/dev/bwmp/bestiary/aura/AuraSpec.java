package dev.bwmp.bestiary.aura;

/**
 * The shape of an aura, as written on the {@code aura} mechanic.
 * <p>
 * An aura is the single most expressive mechanic in the set — most "how did
 * they do that" boss behaviour is an aura — and it is what makes buffs,
 * debuffs, damage-over-time, shields and channelled abilities expressible
 * without bespoke Java.
 */
public final class AuraSpec {

    private final String name;
    private final long durationTicks;
    private final long intervalTicks;
    private final int maxStacks;
    private final String onStart;
    private final String onTick;
    private final String onEnd;
    private final String onStack;
    private final boolean cancelOnDamage;
    private final boolean cancelOnGiverDeath;
    private final boolean refreshOnStack;

    public AuraSpec(String name, long durationTicks, long intervalTicks, int maxStacks,
                    String onStart, String onTick, String onEnd, String onStack,
                    boolean cancelOnDamage, boolean cancelOnGiverDeath, boolean refreshOnStack) {
        this.name = name;
        this.durationTicks = Math.max(1L, durationTicks);
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.maxStacks = Math.max(1, maxStacks);
        this.onStart = onStart == null ? "" : onStart;
        this.onTick = onTick == null ? "" : onTick;
        this.onEnd = onEnd == null ? "" : onEnd;
        this.onStack = onStack == null ? "" : onStack;
        this.cancelOnDamage = cancelOnDamage;
        this.cancelOnGiverDeath = cancelOnGiverDeath;
        this.refreshOnStack = refreshOnStack;
    }

    public String name() {
        return name;
    }

    public long durationTicks() {
        return durationTicks;
    }

    public long intervalTicks() {
        return intervalTicks;
    }

    public int maxStacks() {
        return maxStacks;
    }

    public String onStart() {
        return onStart;
    }

    public String onTick() {
        return onTick;
    }

    public String onEnd() {
        return onEnd;
    }

    public String onStack() {
        return onStack;
    }

    public boolean cancelOnDamage() {
        return cancelOnDamage;
    }

    /** A channelled ability ends when the channeller dies; a poison does not. */
    public boolean cancelOnGiverDeath() {
        return cancelOnGiverDeath;
    }

    public boolean refreshOnStack() {
        return refreshOnStack;
    }
}
