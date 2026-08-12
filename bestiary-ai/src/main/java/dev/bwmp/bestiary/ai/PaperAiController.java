package dev.bwmp.bestiary.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import dev.bwmp.bestiary.api.ai.AiContext;
import dev.bwmp.bestiary.api.ai.AiController;
import dev.bwmp.bestiary.api.ai.AiDefinition;
import dev.bwmp.bestiary.api.ai.AiGoal;
import dev.bwmp.bestiary.api.ai.AiGoalNode;
import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.ai.GoalCategory;
import dev.bwmp.bestiary.api.ai.NavigationKind;
import dev.bwmp.bestiary.api.ai.NmsAware;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Paper Goal API layer.
 * <p>
 * Custom goals cost zero NMS and work across the whole supported band:
 * {@code MobGoals} offers real insertion into and removal from the live goal
 * selector, including stripping a Ravager's
 * vanilla goals entirely and replacing them.
 * <p>
 * Loaded reflectively by {@code AiBridge} behind a {@code classExists} probe,
 * so nothing here is class-loaded on Spigot.
 */
public final class PaperAiController implements AiController, NmsAware {

    private final Plugin plugin;
    private final Map<String, AiGoalType> goalTypes = new LinkedHashMap<>();
    private final Map<UUID, List<GoalKey<Mob>>> applied = new ConcurrentHashMap<>();

    private SkillCaster skillCaster = (mob, skill) -> {
    };
    private AnchorLookup anchors = mob -> null;
    private Object nms;

    public PaperAiController(Plugin plugin) {
        this.plugin = plugin;
        BuiltinGoals.all().forEach(goalTypes::put);
    }

    @Override
    public void bindEngine(SkillCaster caster, AnchorLookup anchorLookup) {
        this.skillCaster = caster == null ? (mob, skill) -> {
        } : caster;
        this.anchors = anchorLookup == null ? mob -> null : anchorLookup;
    }

    @Override
    public void attachNms(Object support) {
        this.nms = support;
    }

    @Override
    public void apply(LivingEntity entity, AiDefinition definition, AiReport report) {
        if (!(entity instanceof Mob)) {
            report.downgrade("ai", entity.getType() + " has no goal selector; goals skipped");
            return;
        }
        Mob mob = (Mob) entity;
        release(entity);

        MobGoals goals = Bukkit.getMobGoals();
        List<GoalKey<Mob>> keys = new ArrayList<>();

        int priority = 0;
        for (AiGoalNode node : definition.goals()) {
            if (node.isClear()) {
                for (GoalCategory category : node.clears()) {
                    goals.removeAllGoals(mob, toGoalType(category));
                }
                continue;
            }

            AiGoalType type = goalTypes.get(normalize(node.type()));
            if (type == null) {
                report.warn("ai", "unknown goal '" + node.type() + "'");
                continue;
            }

            AiContext context = contextFor(mob);
            AiGoal goal;
            try {
                goal = type.create(context, node.args());
            } catch (RuntimeException exception) {
                report.warn("ai", "goal '" + node.type() + "' failed to build: " + exception.getMessage());
                continue;
            }

            GoalKey<Mob> key = GoalKey.of(Mob.class,
                    new NamespacedKey(plugin, "goal_" + normalize(node.type()) + "_" + priority));
            goals.addGoal(mob, node.priority() > 0 ? node.priority() : priority,
                    new GoalAdapter(goal, key));
            keys.add(key);
            priority++;
        }

        applied.put(mob.getUniqueId(), keys);
        applyNavigation(mob, definition, report);
    }

    private void applyNavigation(Mob mob, AiDefinition definition, AiReport report) {
        Pathfinder pathfinder = mob.getPathfinder();
        if (definition.navigation() == NavigationKind.AMPHIBIOUS
                || definition.navigation() == NavigationKind.FLYING) {
            pathfinder.setCanFloat(true);
        }

        if (!definition.needsNms()) {
            return;
        }
        if (nms == null) {
            // Named once rather than silently doing nothing: on a floating
            // island world this is the most valuable capability the plugin has.
            report.downgrade("ai:nms", "navigation '" + definition.navigation().lower()
                    + "' needs the 1.20.5+ NMS tier, which is unavailable; the mob keeps its "
                    + "base type's pathfinder");
            return;
        }
        try {
            nms.getClass().getMethod("applyNavigation", LivingEntity.class, String.class)
                    .invoke(nms, mob, definition.navigation().lower());
            if (!definition.moveControl().isEmpty()) {
                nms.getClass().getMethod("applyMoveControl", LivingEntity.class, String.class)
                        .invoke(nms, mob, definition.moveControl());
            }
            if (!definition.lookControl().isEmpty()) {
                nms.getClass().getMethod("applyLookControl", LivingEntity.class, String.class)
                        .invoke(nms, mob, definition.lookControl());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            report.warn("ai:nms", "could not apply navigation: " + exception);
        }
    }

    @Override
    public void release(LivingEntity entity) {
        if (!(entity instanceof Mob)) {
            return;
        }
        List<GoalKey<Mob>> keys = applied.remove(entity.getUniqueId());
        if (keys == null) {
            return;
        }
        MobGoals goals = Bukkit.getMobGoals();
        for (GoalKey<Mob> key : keys) {
            goals.removeGoal((Mob) entity, key);
        }
    }

    @Override
    public boolean moveTo(LivingEntity entity, Location destination, double speed) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        return ((Mob) entity).getPathfinder().moveTo(destination, speed);
    }

    @Override
    public boolean canReach(LivingEntity entity, Location destination) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        Pathfinder.PathResult path = ((Mob) entity).getPathfinder().findPath(destination);
        return path != null && path.getFinalPoint() != null;
    }

    @Override
    public void stopNavigation(LivingEntity entity) {
        if (entity instanceof Mob) {
            ((Mob) entity).getPathfinder().stopPathfinding();
        }
    }

    @Override
    public void registerGoalType(String id, AiGoalType type) {
        goalTypes.put(normalize(id), type);
    }

    @Override
    public List<String> registeredGoalTypes() {
        return List.copyOf(goalTypes.keySet());
    }

    AiContext contextFor(Mob mob) {
        return new AiContext() {
            @Override
            public LivingEntity mob() {
                return mob;
            }

            @Override
            public AiController controller() {
                return PaperAiController.this;
            }

            @Override
            public Location anchor() {
                return anchors.anchorOf(mob);
            }

            @Override
            public LivingEntity target() {
                return mob.getTarget();
            }

            @Override
            public void setTarget(LivingEntity target) {
                mob.setTarget(target);
            }

            @Override
            public void castSkill(String skillId) {
                skillCaster.cast(mob, skillId);
            }
        };
    }

    static GoalType toGoalType(GoalCategory category) {
        switch (category) {
            case LOOK:
                return GoalType.LOOK;
            case JUMP:
                return GoalType.JUMP;
            case TARGET:
                return GoalType.TARGET;
            case MOVE:
            default:
                return GoalType.MOVE;
        }
    }

    static Set<GoalType> toGoalTypes(Set<GoalCategory> categories) {
        java.util.EnumSet<GoalType> types = java.util.EnumSet.noneOf(GoalType.class);
        for (GoalCategory category : categories) {
            types.add(toGoalType(category));
        }
        return types;
    }

    private static String normalize(String id) {
        String text = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        String key = colon < 0 ? text : text.substring(colon + 1);
        return key.replace("_", "").replace("-", "");
    }
}
