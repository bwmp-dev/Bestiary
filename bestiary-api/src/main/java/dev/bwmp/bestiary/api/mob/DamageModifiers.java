package dev.bwmp.bestiary.api.mob;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-mob damage multipliers keyed on cause, plus the melee / projectile /
 * magic split that cuts across causes.
 * <p>
 * A value of {@code 0} is immunity, which is deliberately not a separate
 * concept: "takes no fire damage" and "takes 0× fire damage" are the same
 * statement, and having one way to write it means one code path.
 */
public final class DamageModifiers {

    public static final DamageModifiers NONE = new DamageModifiers(Map.of(), 1.0d, 1.0d, 1.0d);

    private final Map<DamageCause, Double> byCause;
    private final double melee;
    private final double projectile;
    private final double magic;

    public DamageModifiers(Map<DamageCause, Double> byCause, double melee, double projectile, double magic) {
        this.byCause = byCause == null || byCause.isEmpty()
                ? Map.of()
                : Map.copyOf(new EnumMap<>(byCause));
        this.melee = melee;
        this.projectile = projectile;
        this.magic = magic;
    }

    public double melee() {
        return melee;
    }

    public double projectile() {
        return projectile;
    }

    public double magic() {
        return magic;
    }

    public boolean isIdentity() {
        return byCause.isEmpty() && melee == 1.0d && projectile == 1.0d && magic == 1.0d;
    }

    /** The combined multiplier for one damage event. */
    public double multiplierFor(DamageCause cause, boolean fromProjectile, boolean fromMagic) {
        double multiplier = byCause.getOrDefault(cause, 1.0d);
        if (fromProjectile) {
            multiplier *= projectile;
        } else if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            multiplier *= melee;
        }
        if (fromMagic) {
            multiplier *= magic;
        }
        return multiplier;
    }

    public Map<DamageCause, Double> byCause() {
        return byCause;
    }
}
