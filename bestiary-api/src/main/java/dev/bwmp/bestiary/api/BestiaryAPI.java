package dev.bwmp.bestiary.api;

import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargeterType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Bestiary's public entry point, published through Bukkit's service manager.
 * <p>
 * Bukkit's own services manager is used rather than a static holder because it
 * is the one registry that genuinely spans plugin boundaries, and because it
 * makes the dependency explicit and unregisters cleanly.
 */
public interface BestiaryAPI {

    static Optional<BestiaryAPI> get() {
        RegisteredServiceProvider<BestiaryAPI> provider =
                Bukkit.getServicesManager().getRegistration(BestiaryAPI.class);
        return provider == null ? Optional.empty() : Optional.of(provider.getProvider());
    }

    // --- content ---------------------------------------------------------

    Optional<MobDefinition> mob(NamespacedKey id);

    Collection<MobDefinition> mobs();

    Collection<String> skillIds();

    Collection<String> dropTableIds();

    // --- runtime ---------------------------------------------------------

    /** The mob an entity is, if any. The single supported way to identify one. */
    Optional<BestiaryMob> resolve(Entity entity);

    default boolean isBestiaryMob(Entity entity) {
        return resolve(entity).isPresent();
    }

    Collection<BestiaryMob> activeMobs();

    Optional<BestiaryMob> spawn(NamespacedKey id, Location location, int level);

    /**
     * Runs a skill with an arbitrary caster.
     *
     * @param targets the initial target list; empty means each line resolves
     *                its own targeter from the caster
     */
    boolean castSkill(String skillId, Entity caster, List<Target> targets, double power);

    // --- extension -------------------------------------------------------

    /** The id's namespace must match {@code owner}. */
    void registerMechanicType(Plugin owner, NamespacedKey id, MechanicType type);

    void registerTargeterType(Plugin owner, NamespacedKey id, TargeterType type);

    void registerConditionType(Plugin owner, NamespacedKey id, ConditionType type);

    void registerGoalType(Plugin owner, NamespacedKey id, AiGoalType type);

    // --- statistics ------------------------------------------------------

    /** Served from the in-memory view, never a database round-trip. */
    int killCount(Player player, NamespacedKey mob);

    int totalKillCount(Player player);

    /** Milliseconds remaining on a named anchor's respawn cooldown, or 0. */
    long anchorCooldownMillis(String anchorId);

    BestiaryScheduler scheduler();
}
