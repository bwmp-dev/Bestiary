package dev.bwmp.bestiary.api.mob;

/**
 * Threat table tuning, opt-in per mob.
 * <p>
 * When enabled the mob's target selection reads the threat table instead of
 * vanilla's last-attacker heuristic, which is what makes a multi-player boss
 * fight a fight rather than a race to hit it first.
 */
public final class ThreatSettings {

    public static final ThreatSettings DISABLED = new ThreatSettings(false, 0.0d, 1.0d, 0.5d, 1.0d, 1.5d);

    private final boolean enabled;
    private final double decayPerSecond;
    private final double damageFactor;
    private final double healingFactor;
    private final double tauntMultiplier;
    private final double switchThreshold;

    public ThreatSettings(boolean enabled, double decayPerSecond, double damageFactor,
                          double healingFactor, double tauntMultiplier, double switchThreshold) {
        this.enabled = enabled;
        this.decayPerSecond = Math.max(0.0d, decayPerSecond);
        this.damageFactor = damageFactor;
        this.healingFactor = healingFactor;
        this.tauntMultiplier = tauntMultiplier;
        this.switchThreshold = Math.max(1.0d, switchThreshold);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Fraction of accumulated threat shed each second. */
    public double decayPerSecond() {
        return decayPerSecond;
    }

    public double damageFactor() {
        return damageFactor;
    }

    /** Healing done near the mob generates threat too, so healers are targetable. */
    public double healingFactor() {
        return healingFactor;
    }

    public double tauntMultiplier() {
        return tauntMultiplier;
    }

    /**
     * How far ahead a challenger must be before the mob switches. Without
     * hysteresis a boss flickers between two players on alternate hits.
     */
    public double switchThreshold() {
        return switchThreshold;
    }
}
