package dev.bwmp.bestiary.api.skill;

import java.util.Locale;
import java.util.Optional;

/**
 * When.
 * <p>
 * Nothing here is polled unless a mob's definition asks for it: {@link #TIMER},
 * {@link #PLAYER_NEAR} and {@link #TICK} drive a single per-mob task that only
 * starts if at least one of them is declared, and whose period is the GCD of
 * the declared intervals rather than one tick.
 */
public enum TriggerKind {

    SPAWN("onSpawn", false),
    FIRST_SPAWN("onFirstSpawn", false),
    TIMER("onTimer", true),
    DAMAGED("onDamaged", false),
    DAMAGED_BY_PLAYER("onDamagedByPlayer", false),
    ATTACK("onAttack", false),
    KILL("onKill", false),
    KILL_PLAYER("onKillPlayer", false),
    DEATH("onDeath", false),
    INTERACT("onInteract", false),
    PLAYER_NEAR("onPlayerNear", true),
    PLAYER_LEAVE("onPlayerLeave", true),
    COMBAT_ENTER("onCombatEnter", false),
    COMBAT_EXIT("onCombatExit", false),
    SIGNAL("onSignal", true),
    PHASE("onPhase", true),
    HEALTH_THRESHOLD("onHealthThreshold", true),
    SUMMON("onSummon", false),
    TELEPORT("onTeleport", false),
    PROJECTILE_HIT("onProjectileHit", false),
    BLOCK_BREAK("onBlockBreak", false),
    DESPAWN("onDespawn", false),
    ENTER_ARENA("onEnterArena", false),
    LEAVE_ARENA("onLeaveArena", false),
    TICK("onTick", false);

    private final String written;
    private final boolean parameterised;

    TriggerKind(String written, boolean parameterised) {
        this.written = written;
        this.parameterised = parameterised;
    }

    /** The spelling used in config, e.g. {@code onHealthThreshold}. */
    public String written() {
        return written;
    }

    /** True when the trigger takes a {@code :value} suffix. */
    public boolean parameterised() {
        return parameterised;
    }

    /** True for the three triggers that need the per-mob polling task. */
    public boolean polled() {
        return this == TIMER || this == PLAYER_NEAR || this == PLAYER_LEAVE || this == TICK;
    }

    public static Optional<TriggerKind> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = ParameterSpec.normalize(value.startsWith("~") ? value.substring(1) : value);
        for (TriggerKind kind : values()) {
            if (ParameterSpec.normalize(kind.written).equals(normalized)) {
                return Optional.of(kind);
            }
            if (kind.name().toLowerCase(Locale.ROOT).replace("_", "").equals(normalized)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
