package dev.bwmp.bestiary.ai;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.ai.AiController;
import dev.bwmp.bestiary.api.ai.AiDefinition;
import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.keystone.compat.McVersion;
import dev.bwmp.keystone.compat.Platform;
import dev.bwmp.keystone.config.LoadReport;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * Loads bestiary-ai reflectively when Paper's Goal API is present.
 * <p>
 * bestiary-plugin must never name a Paper type: the same jar runs on Spigot
 * 1.19.4, where {@code MobGoals} does not exist and a static reference would
 * fail at class-load. So the whole AI tier is reached through
 * {@link AiController}, which lives in bestiary-api and mentions only Bukkit.
 * <p>
 * <b>Custom AI requires Paper.</b> On Spigot, mobs fall back to their vanilla
 * base AI plus scripted movement mechanics — a real but honest limitation,
 * reported at startup rather than discovered when a boss does not behave.
 */
public final class AiBridge {

    private static final String GOAL_API = "com.destroystokyo.paper.entity.ai.MobGoals";
    private static final String CONTROLLER = "dev.bwmp.bestiary.ai.PaperAiController";
    private static final String NMS_SUPPORT = "dev.bwmp.bestiary.ai.nms.NmsNavigation";
    private static final McVersion NMS_FLOOR = McVersion.of(1, 20, 5);

    private final Engine engine;
    private AiController controller;
    private Object nms;
    private boolean nmsAvailable;

    public AiBridge(Engine engine) {
        this.engine = engine;
    }

    public void initialise(Platform platform, LoadReport report) {
        if (!Platform.classExists(GOAL_API)) {
            report.downgrade("ai", "Paper's Goal API is absent (this is "
                    + platform.brand().displayName() + "); custom goals are skipped and mobs keep their "
                    + "vanilla base AI plus scripted movement mechanics");
            return;
        }
        try {
            Class<?> type = Class.forName(CONTROLLER);
            controller = (AiController) type.getConstructor(org.bukkit.plugin.Plugin.class)
                    .newInstance(engine.plugin());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            report.warn("ai", "the Paper AI layer failed to load (" + exception + "); custom goals are skipped");
            controller = null;
            return;
        }

        // The NMS tier activates on 1.20.5+ only, where Paper's runtime is
        // Mojang-mapped and member names are stable. Below that the four
        // capabilities it exists for degrade to Goal-API approximations, named
        // once here.
        boolean modern = platform.version().atLeast(NMS_FLOOR);
        if (!modern) {
            report.downgrade("ai:nms", "server is " + platform.version()
                    + "; navigation swapping, move/look control replacement, brain mobs and custom "
                    + "attribute registration need 1.20.5+ and are skipped");
            return;
        }
        try {
            Class<?> type = Class.forName(NMS_SUPPORT);
            nms = type.getConstructor().newInstance();
            nmsAvailable = (Boolean) type.getMethod("available").invoke(nms);
            if (!nmsAvailable) {
                report.downgrade("ai:nms", "a Mojang-mapped member did not resolve on this build; "
                        + "the whole NMS tier is disabled rather than half-working");
            } else if (controller instanceof dev.bwmp.bestiary.api.ai.NmsAware) {
                ((dev.bwmp.bestiary.api.ai.NmsAware) controller).attachNms(nms);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            report.downgrade("ai:nms", "unavailable on this build (" + exception + ")");
            nmsAvailable = false;
        }
    }

    public boolean available() {
        return controller != null;
    }

    public boolean nmsAvailable() {
        return nmsAvailable;
    }

    public void apply(LivingEntity mob, AiDefinition definition) {
        if (controller == null || definition == null || definition.isEmpty()) {
            return;
        }
        controller.apply(mob, definition, reporter());
    }

    public void release(LivingEntity mob) {
        if (controller != null) {
            controller.release(mob);
        }
    }

    public boolean moveTo(LivingEntity mob, Location destination, double speed) {
        return controller != null && controller.moveTo(mob, destination, speed);
    }

    public boolean canReach(LivingEntity mob, Location destination) {
        return controller != null && controller.canReach(mob, destination);
    }

    public void stopNavigation(LivingEntity mob) {
        if (controller != null) {
            controller.stopNavigation(mob);
        }
    }

    public void registerGoalType(String id, AiGoalType type) {
        if (controller != null) {
            controller.registerGoalType(id, type);
        }
    }

    public List<String> goalTypes() {
        return controller == null ? List.of() : controller.registeredGoalTypes();
    }

    public AiController controller() {
        return controller;
    }

    /**
     * Downgrades reported at runtime rather than at load, because a
     * {@code navigation: flying} that cannot be honoured is only discovered
     * when a mob using it spawns. Throttled by the logger's own once-per-line
     * behaviour in the load report.
     */
    private AiController.AiReport reporter() {
        return new AiController.AiReport() {
            @Override
            public void downgrade(String source, String message) {
                engine.logger().info("[ai] " + source + ": " + message);
            }

            @Override
            public void warn(String source, String message) {
                engine.logger().warning("[ai] " + source + ": " + message);
            }
        };
    }
}
