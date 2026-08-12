package dev.bwmp.bestiary.api.ai;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** What a goal is allowed to know about the mob it drives. */
public interface AiContext {

    LivingEntity mob();

    /** Navigation and path queries, without importing Paper's Pathfinder. */
    AiController controller();

    /**
     * Where this mob belongs, when it came from a structure anchor or spawner.
     * Null otherwise — {@code return_to_anchor} and {@code patrol_anchor} skip
     * themselves rather than guessing.
     */
    Location anchor();

    /** The mob's current attack target, threat table first when one is enabled. */
    LivingEntity target();

    void setTarget(LivingEntity target);

    /** Casts one of the mob's skills by id. Lets a goal drive an attack animation. */
    void castSkill(String skillId);
}
