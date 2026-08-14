package dev.bwmp.bestiary.skill;

/**
 * The four guards, all of them load-bearing because skills call skills.
 * <p>
 * Exceeding any one aborts that execution, logs once per skill per minute with
 * the skill id and the offending node path, and does not touch the rest of the
 * server.
 */
public final class ExecutionLimits {

    public static final ExecutionLimits DEFAULT = new ExecutionLimits(32, 4000, 64, 5.0d);

    private final int maxDepth;
    private final int maxMechanics;
    private final int maxTargets;
    private final double budgetMillis;

    public ExecutionLimits(int maxDepth, int maxMechanics, int maxTargets, double budgetMillis) {
        this.maxDepth = Math.max(1, maxDepth);
        this.maxMechanics = Math.max(1, maxMechanics);
        this.maxTargets = Math.max(1, maxTargets);
        this.budgetMillis = budgetMillis <= 0 ? Double.MAX_VALUE : budgetMillis;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int maxMechanics() {
        return maxMechanics;
    }

    public int maxTargets() {
        return maxTargets;
    }

    /** Wall clock per tick, after which the tree is suspended and resumed next tick. */
    public double budgetMillis() {
        return budgetMillis;
    }

    public long budgetNanos() {
        return budgetMillis >= Double.MAX_VALUE ? Long.MAX_VALUE : (long) (budgetMillis * 1_000_000.0d);
    }
}
