package dev.bwmp.bestiary.api.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/** A live instance of a {@link MobDefinition}. */
public interface BestiaryMob {

    MobDefinition definition();

    LivingEntity entity();

    UUID uniqueId();

    int level();

    /** The current phase name, empty when the definition declares none. */
    String phase();

    /** Mob-scoped variables, persisted in the entity's PDC. */
    Map<String, Object> variables();

    /** The owning spawn anchor, empty when the mob was not spawned from one. */
    String anchorId();

    /** Threat for one player, empty when the mob has no threat table. */
    OptionalDouble threat(Player player);

    /** Total damage this player has dealt over the fight, for drop shares. */
    double damageDealtBy(Player player);

    /** Runs one of the mob's skills, or any registered skill, by id. */
    void cast(String skillId);

    /** Sends a named signal, firing {@code ~onSignal:<name>} bindings. */
    void signal(String name);

    /**
     * Removes the mob. {@code permanent} also clears its anchor's memory of it,
     * so the anchor does not immediately respawn it.
     */
    void remove(boolean permanent);
}
