package dev.bwmp.bestiary.api.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * Bestiary's own view of a mob's AI, so bestiary-plugin never imports a Paper
 * type.
 * <p>
 * The implementation lives in bestiary-ai and is loaded reflectively behind a
 * {@code Platform.classExists} probe for {@code MobGoals}. On Spigot no
 * implementation exists and mobs keep their vanilla base AI plus scripted
 * movement mechanics — a real but honest limitation, reported at startup.
 */
public interface AiController {

    /** Applies a definition to a freshly spawned or re-adopted mob. */
    void apply(LivingEntity mob, AiDefinition definition, AiReport report);

    /** Drops every goal Bestiary added, leaving vanilla ones alone. */
    void release(LivingEntity mob);

    /** Walks the mob toward a location using its pathfinder. */
    boolean moveTo(LivingEntity mob, Location destination, double speed);

    /** True when a path to {@code destination} exists. */
    boolean canReach(LivingEntity mob, Location destination);

    void stopNavigation(LivingEntity mob);

    /** Registers a goal type so third parties contribute goals like mechanics. */
    void registerGoalType(String id, AiGoalType type);

    List<String> registeredGoalTypes();

    /**
     * Wires the engine's skill caster and anchor lookup in.
     * <p>
     * Goals need both and neither belongs in this module: bestiary-ai knows
     * about Paper, not about skills or anchors. Passing them in keeps the
     * dependency pointing one way.
     */
    void bindEngine(SkillCaster caster, AnchorLookup anchors);

    @FunctionalInterface
    interface SkillCaster {
        void cast(LivingEntity mob, String skillId);
    }

    @FunctionalInterface
    interface AnchorLookup {
        /** Where the mob belongs, or null. */
        Location anchorOf(LivingEntity mob);
    }

    /** What this tier could not do, so the load report can say so once. */
    interface AiReport {
        void downgrade(String source, String message);

        void warn(String source, String message);
    }
}
