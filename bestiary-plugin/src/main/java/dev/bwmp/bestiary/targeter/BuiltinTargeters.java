package dev.bwmp.bestiary.targeter;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.TargeterMeta;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.bestiary.api.skill.VariableScope;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.util.Registries;
import dev.bwmp.bestiary.util.ShapeSpec;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every built-in targeter.
 * <p>
 * {@code limit}, {@code sort} and {@code filter} are declared on each one but
 * applied by the engine, so a third-party targeter gets them free and cannot
 * forget the {@code max_targets} cap.
 */
public final class BuiltinTargeters {

    private BuiltinTargeters() {
    }

    public static Map<String, TargeterType> all(Engine engine) {
        Map<String, TargeterType> types = new LinkedHashMap<>();
        registerEntity(types, engine);
        registerLocation(types, engine);
        return types;
    }

    // --- entity targeters -------------------------------------------------

    private static void registerEntity(Map<String, TargeterType> into, Engine engine) {

        into.put("self", Targeters.type(entityMeta("self", "The caster.").noSource().build(),
                config -> (context, source) -> one(Target.of(context.caster()))));

        into.put("target", Targeters.type(
                entityMeta("target", "Whatever the caster is currently attacking.").noSource().build(),
                config -> (context, source) -> {
                    if (!(context.caster() instanceof Mob)) {
                        return List.of();
                    }
                    LivingEntity victim = ((Mob) context.caster()).getTarget();
                    return victim == null ? List.of() : one(Target.of(victim));
                }));

        into.put("trigger", Targeters.type(
                entityMeta("trigger", "Whatever tripped the trigger.").noSource().build(),
                config -> (context, source) ->
                        context.trigger() == null ? List.of() : one(Target.of(context.trigger()))));

        into.put("damager", Targeters.type(
                entityMeta("damager", "The trigger entity. An alias, for readability on ~onDamaged.")
                        .noSource().build(),
                config -> (context, source) ->
                        context.trigger() == null ? List.of() : one(Target.of(context.trigger()))));

        into.put("killer", Targeters.type(
                entityMeta("killer", "The player who killed the caster, on ~onDeath.").noSource().build(),
                config -> (context, source) -> {
                    LivingEntity caster = context.casterLiving();
                    Player killer = caster == null ? null : caster.getKiller();
                    return killer == null ? List.of() : one(Target.of(killer));
                }));

        into.put("nearest_player", Targeters.type(
                entityMeta("nearest_player", "The closest player within a radius.")
                        .param("radius", "how far to look", "16", "r")
                        .param("ignore_invisible", "skip invisible players", "false")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 16);
                    boolean ignoreInvisible = config.bool("ignore_invisible", false);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        double range = radius.asDouble(context, null);
                        Player nearest = null;
                        double best = range * range;
                        for (Player player : playersNear(centre, range)) {
                            if (ignoreInvisible && player.isInvisible()) {
                                continue;
                            }
                            double distance = player.getLocation().distanceSquared(centre);
                            if (distance <= best) {
                                best = distance;
                                nearest = player;
                            }
                        }
                        return nearest == null ? List.of() : one(Target.of(nearest));
                    };
                }));

        into.put("players_in_radius", Targeters.type(
                entityMeta("players_in_radius", "Every player within a radius.")
                        .param("radius", "how far", "8", "r")
                        .param("ignore_caster", "skip the caster if it is a player", "true")
                        .param("require_line_of_sight", "skip players behind walls", "false", "los")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 8);
                    boolean ignoreCaster = config.bool("ignore_caster", true);
                    boolean requireLineOfSight = config.bool("require_line_of_sight", false);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        List<Target> targets = new ArrayList<>();
                        for (Player player : playersNear(centre, radius.asDouble(context, null))) {
                            if (ignoreCaster && player.equals(context.caster())) {
                                continue;
                            }
                            if (requireLineOfSight && context.casterLiving() != null
                                    && !context.casterLiving().hasLineOfSight(player)) {
                                continue;
                            }
                            targets.add(Target.of(player));
                        }
                        return targets;
                    };
                }));

        into.put("entities_in_radius", Targeters.type(
                entityMeta("entities_in_radius", "Every living entity within a radius.")
                        .param("radius", "how far", "8", "r")
                        .param("types", "comma-separated entity types; empty means all", "", "type")
                        .param("ignore_caster", "skip the caster", "true")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 8);
                    Set<EntityType> types = entityTypes(config.stringList("types"));
                    boolean ignoreCaster = config.bool("ignore_caster", true);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        double range = radius.asDouble(context, null);
                        List<Target> targets = new ArrayList<>();
                        for (Entity entity : nearby(centre, range)) {
                            if (!(entity instanceof LivingEntity)) {
                                continue;
                            }
                            if (ignoreCaster && entity.equals(context.caster())) {
                                continue;
                            }
                            if (!types.isEmpty() && !types.contains(entity.getType())) {
                                continue;
                            }
                            if (engine.hooks().isNpc(entity) && !engine.settings().adoptCitizens()) {
                                continue;
                            }
                            targets.add(Target.of(entity));
                        }
                        return targets;
                    };
                }));

        into.put("mobs_in_radius", Targeters.type(
                entityMeta("mobs_in_radius", "Every non-player living entity within a radius.")
                        .param("radius", "how far", "8", "r")
                        .param("bestiary_only", "only Bestiary mobs", "false")
                        .param("faction", "only mobs of this faction", "")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 8);
                    boolean bestiaryOnly = config.bool("bestiary_only", false);
                    String faction = config.raw("faction", "");
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        List<Target> targets = new ArrayList<>();
                        for (Entity entity : nearby(centre, radius.asDouble(context, null))) {
                            if (!(entity instanceof LivingEntity) || entity instanceof Player
                                    || entity.equals(context.caster())) {
                                continue;
                            }
                            MobInstance instance = engine.mobs().instance(entity);
                            if (bestiaryOnly && instance == null) {
                                continue;
                            }
                            if (!faction.isEmpty() && (instance == null
                                    || !faction.equalsIgnoreCase(instance.definition().faction()))) {
                                continue;
                            }
                            targets.add(Target.of(entity));
                        }
                        return targets;
                    };
                }));

        into.put("players_in_ring", Targeters.type(
                entityMeta("players_in_ring", "Players between an inner and an outer radius.")
                        .param("radius", "outer radius", "10", "r", "outer")
                        .param("inner", "inner radius", "4", "ir")
                        .build(),
                config -> {
                    Expression outer = config.number("radius", 10);
                    Expression inner = config.number("inner", 4);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        double outerRange = outer.asDouble(context, null);
                        double innerRange = inner.asDouble(context, null);
                        List<Target> targets = new ArrayList<>();
                        for (Player player : playersNear(centre, outerRange)) {
                            double distance = player.getLocation().distance(centre);
                            if (distance >= innerRange && distance <= outerRange) {
                                targets.add(Target.of(player));
                            }
                        }
                        return targets;
                    };
                }));

        into.put("players_in_cone", Targeters.type(
                entityMeta("players_in_cone", "Players inside a cone along the caster's facing.")
                        .param("radius", "cone length", "10", "r", "length")
                        .param("angle", "half-angle in degrees", "45", "a")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 10);
                    Expression angle = config.number("angle", 45);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        Vector facing = context.caster().getLocation().getDirection().normalize();
                        double range = radius.asDouble(context, null);
                        double cosine = Math.cos(Math.toRadians(angle.asDouble(context, null)));
                        List<Target> targets = new ArrayList<>();
                        for (Player player : playersNear(centre, range)) {
                            Vector toPlayer = player.getLocation().toVector().subtract(centre.toVector());
                            if (toPlayer.lengthSquared() < 1.0e-6d) {
                                targets.add(Target.of(player));
                                continue;
                            }
                            if (facing.dot(toPlayer.normalize()) >= cosine) {
                                targets.add(Target.of(player));
                            }
                        }
                        return targets;
                    };
                }));

        into.put("players_in_world", Targeters.type(
                entityMeta("players_in_world", "Every player in the caster's world.").noSource().build(),
                config -> (context, source) -> {
                    World world = context.caster().getWorld();
                    List<Target> targets = new ArrayList<>();
                    for (Player player : world.getPlayers()) {
                        targets.add(Target.of(player));
                    }
                    return targets;
                }));

        into.put("owner", Targeters.type(
                entityMeta("owner", "Whoever summoned the caster.").noSource().build(),
                config -> (context, source) -> {
                    Entity owner = ownerOf(engine, context.caster());
                    return owner == null ? List.of() : one(Target.of(owner));
                }));

        into.put("parent", Targeters.type(
                entityMeta("parent", "An alias of owner.").noSource().build(),
                config -> (context, source) -> {
                    Entity owner = ownerOf(engine, context.caster());
                    return owner == null ? List.of() : one(Target.of(owner));
                }));

        into.put("summoner", Targeters.type(
                entityMeta("summoner", "An alias of owner.").noSource().build(),
                config -> (context, source) -> {
                    Entity owner = ownerOf(engine, context.caster());
                    return owner == null ? List.of() : one(Target.of(owner));
                }));

        into.put("children", Targeters.type(
                entityMeta("children", "Every mob the caster summoned that is still alive.")
                        .param("radius", "how far to look", "48", "r")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 48);
                    return (context, source) -> {
                        String casterId = context.caster().getUniqueId().toString();
                        List<Target> targets = new ArrayList<>();
                        for (Entity entity : nearby(context.caster().getLocation(),
                                radius.asDouble(context, null))) {
                            String owner = entity.getPersistentDataContainer()
                                    .get(engine.keys().owner, PersistentDataType.STRING);
                            if (casterId.equals(owner)) {
                                targets.add(Target.of(entity));
                            }
                        }
                        return targets;
                    };
                }));

        into.put("mount", Targeters.type(
                entityMeta("mount", "What the caster is riding.").noSource().build(),
                config -> (context, source) -> {
                    Entity vehicle = context.caster().getVehicle();
                    return vehicle == null ? List.of() : one(Target.of(vehicle));
                }));

        into.put("passengers", Targeters.type(
                entityMeta("passengers", "Everything riding the caster.").noSource().build(),
                config -> (context, source) -> {
                    List<Target> targets = new ArrayList<>();
                    for (Entity passenger : context.caster().getPassengers()) {
                        targets.add(Target.of(passenger));
                    }
                    return targets;
                }));

        into.put("threat_table", Targeters.type(
                entityMeta("threat_table", "Every player on the caster's threat table, highest first.")
                        .noSource().build(),
                config -> (context, source) -> {
                    MobInstance instance = engine.mobs().instance(context.caster());
                    if (instance == null || instance.threatTable() == null) {
                        return List.of();
                    }
                    List<Target> targets = new ArrayList<>();
                    for (Player player : instance.threatTable().ranked()) {
                        targets.add(Target.of(player));
                    }
                    return targets;
                }));

        into.put("threat_table_top", Targeters.type(
                entityMeta("threat_table_top", "The player at the top of the threat table.")
                        .noSource().build(),
                config -> (context, source) -> {
                    MobInstance instance = engine.mobs().instance(context.caster());
                    if (instance == null || instance.threatTable() == null) {
                        return List.of();
                    }
                    Player top = instance.threatTable().select();
                    return top == null ? List.of() : one(Target.of(top));
                }));

        into.put("random_threat_target", Targeters.type(
                entityMeta("random_threat_target", "A random player from the threat table.")
                        .noSource().build(),
                config -> (context, source) -> {
                    MobInstance instance = engine.mobs().instance(context.caster());
                    if (instance == null || instance.threatTable() == null) {
                        return List.of();
                    }
                    List<Player> ranked = instance.threatTable().ranked();
                    if (ranked.isEmpty()) {
                        return List.of();
                    }
                    return one(Target.of(ranked.get(
                            java.util.concurrent.ThreadLocalRandom.current().nextInt(ranked.size()))));
                }));

        into.put("variable_entity", Targeters.type(
                entityMeta("variable_entity", "The entity whose UUID is stored in a variable.")
                        .required("variable", "variable name holding a UUID", "var", "name")
                        .param("scope", "skill, mob, target or global", "skill", "s")
                        .noSource().build(),
                config -> {
                    String variable = config.raw("variable", "");
                    VariableScope scope = VariableScope.parse(config.raw("scope", "skill"), VariableScope.SKILL);
                    return (context, source) -> {
                        Object value = context.variable(scope, variable);
                        Entity entity = value == null ? null : entityByUuid(String.valueOf(value));
                        return entity == null ? List.of() : one(Target.of(entity));
                    };
                }));

        into.put("unique_id", Targeters.type(
                entityMeta("unique_id", "One specific entity, by UUID.")
                        .required("uuid", "the entity UUID", "id")
                        .noSource().build(),
                config -> {
                    String uuid = config.raw("uuid", "");
                    return (context, source) -> {
                        Entity entity = entityByUuid(uuid);
                        return entity == null ? List.of() : one(Target.of(entity));
                    };
                }));
    }

    // --- location targeters -----------------------------------------------

    private static void registerLocation(Map<String, TargeterType> into, Engine engine) {

        into.put("self_location", Targeters.type(
                locationMeta("self_location", "Where the caster is.").noSource().build(),
                config -> (context, source) -> one(Target.of(context.caster().getLocation()))));

        into.put("target_location", Targeters.type(
                locationMeta("target_location", "Where the caster's attack target is.").noSource().build(),
                config -> (context, source) -> {
                    if (!(context.caster() instanceof Mob)) {
                        return List.of();
                    }
                    LivingEntity victim = ((Mob) context.caster()).getTarget();
                    return victim == null ? List.of() : one(Target.of(victim.getLocation()));
                }));

        into.put("trigger_location", Targeters.type(
                locationMeta("trigger_location", "Where the trigger entity is.").noSource().build(),
                config -> (context, source) -> context.trigger() == null
                        ? List.of() : one(Target.of(context.trigger().getLocation()))));

        into.put("origin", Targeters.type(
                locationMeta("origin", "The skill's origin location.").noSource().build(),
                config -> (context, source) -> one(Target.of(context.origin()))));

        into.put("spawn_location", Targeters.type(
                locationMeta("spawn_location", "Where the caster was spawned.").noSource().build(),
                config -> (context, source) -> {
                    MobInstance instance = engine.mobs().instance(context.caster());
                    return instance == null ? List.of() : one(Target.of(instance.spawnLocation()));
                }));

        into.put("anchor", Targeters.type(
                locationMeta("anchor", "The caster's owning structure anchor.")
                        .param("id", "an explicit anchor id; empty uses the caster's own", "")
                        .noSource().build(),
                config -> {
                    String id = config.raw("id", "");
                    return (context, source) -> {
                        String anchorId = id;
                        if (anchorId.isEmpty()) {
                            MobInstance instance = engine.mobs().instance(context.caster());
                            anchorId = instance == null ? "" : instance.anchorId();
                        }
                        if (anchorId.isEmpty()) {
                            return List.of();
                        }
                        Location location = engine.anchors().byId(anchorId)
                                .map(record -> record.location()).orElse(null);
                        return location == null ? List.of() : one(Target.of(location));
                    };
                }));

        into.put("forward", Targeters.type(
                locationMeta("forward", "A point in front of the caster.")
                        .param("distance", "how far", "3", "d", "range")
                        .param("y_offset", "vertical offset", "0", "oy")
                        .build(),
                config -> {
                    Expression distance = config.number("distance", 3);
                    Expression yOffset = config.number("y_offset", 0);
                    return (context, source) -> {
                        Location base = originOf(context, source);
                        Vector direction = context.caster().getLocation().getDirection().normalize();
                        return one(Target.of(base.clone()
                                .add(direction.multiply(distance.asDouble(context, null)))
                                .add(0, yOffset.asDouble(context, null), 0)));
                    };
                }));

        into.put("offset", Targeters.type(
                locationMeta("offset", "A fixed offset from each source location.")
                        .param("x", "east offset", "0")
                        .param("y", "vertical offset", "0")
                        .param("z", "south offset", "0")
                        .build(),
                config -> {
                    Expression x = config.number("x", 0);
                    Expression y = config.number("y", 0);
                    Expression z = config.number("z", 0);
                    return (context, source) -> {
                        List<Target> targets = new ArrayList<>();
                        for (Location base : basesOf(context, source)) {
                            targets.add(Target.of(base.clone().add(
                                    x.asDouble(context, null),
                                    y.asDouble(context, null),
                                    z.asDouble(context, null))));
                        }
                        return targets;
                    };
                }));

        into.put("random_near_origin", Targeters.type(
                locationMeta("random_near_origin", "Random points around each source location.")
                        .param("radius", "how far", "5", "r")
                        .param("amount", "how many points", "1", "a", "count")
                        .param("flat", "keep the same height", "true")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 5);
                    Expression amount = config.number("amount", 1);
                    boolean flat = config.bool("flat", true);
                    return (context, source) -> {
                        List<Target> targets = new ArrayList<>();
                        int count = Math.max(1, amount.asInt(context, null));
                        double range = radius.asDouble(context, null);
                        for (Location base : basesOf(context, source)) {
                            for (int index = 0; index < count; index++) {
                                targets.add(Target.of(Shapes.randomNear(base, range, flat)));
                            }
                        }
                        return targets;
                    };
                }));

        into.put("blocks_in_radius", Targeters.type(
                locationMeta("blocks_in_radius", "Every block position inside a radius.")
                        .param("radius", "how far", "3", "r")
                        .param("hollow", "surface only", "false")
                        .param("solid_only", "skip air", "false")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 3);
                    boolean hollow = config.bool("hollow", false);
                    boolean solidOnly = config.bool("solid_only", false);
                    return (context, source) -> {
                        Location centre = originOf(context, source);
                        double range = radius.asDouble(context, null);
                        int ceiling = (int) Math.ceil(range);
                        List<Target> targets = new ArrayList<>();
                        for (int x = -ceiling; x <= ceiling; x++) {
                            for (int y = -ceiling; y <= ceiling; y++) {
                                for (int z = -ceiling; z <= ceiling; z++) {
                                    double squared = x * x + y * y + z * z;
                                    if (squared > range * range) {
                                        continue;
                                    }
                                    if (hollow && squared < (range - 1) * (range - 1)) {
                                        continue;
                                    }
                                    Location point = centre.clone().add(x, y, z);
                                    if (solidOnly && point.getBlock().getType().isAir()) {
                                        continue;
                                    }
                                    targets.add(Target.of(point));
                                }
                            }
                        }
                        return targets;
                    };
                }));

        shapeTargeter(into, "ring", "Points on a horizontal circle around each source.");
        shapeTargeter(into, "sphere", "Points on a sphere around each source.");
        shapeTargeter(into, "cone", "Points in a cone along the caster's facing.");
        shapeTargeter(into, "line", "Points along a line from each source.");
        shapeTargeter(into, "spiral", "Points along a rising spiral.");
        shapeTargeter(into, "mesh", "Points on a horizontal grid.");
    }

    private static void shapeTargeter(Map<String, TargeterType> into, String id, String description) {
        into.put(id, Targeters.type(
                locationMeta(id, description)
                        .param("radius", "shape radius", "3", "r")
                        .param("points", "how many points", "12", "n", "amount")
                        .param("height", "vertical offset, or height for spiral", "0", "h")
                        .param("length", "length for cone and line", "6", "l")
                        .param("angle", "cone half-angle in degrees", "30")
                        .param("turns", "revolutions for spiral", "3")
                        .param("spacing", "point spacing for line and mesh", "1", "step")
                        .build(),
                config -> {
                    ShapeSpec spec = ShapeSpec.of(new FixedTypeConfig(config, id.equals("mesh") ? "box" : id));
                    return (context, source) -> {
                        List<Target> targets = new ArrayList<>();
                        for (Location base : basesOf(context, source)) {
                            for (Location point : spec.points(context, null, base)) {
                                targets.add(Target.of(point));
                            }
                        }
                        return targets;
                    };
                }));
    }

    // --- helpers ----------------------------------------------------------

    private static TargeterMeta.Builder entityMeta(String id, String description) {
        return TargeterMeta.builder(id).description(description).produces(TargetKind.ENTITY)
                .param("limit", "maximum targets after sorting", "0")
                .param("sort", "nearest, farthest, random, threat, lowest_health, highest_health", "none")
                .param("filter", "a condition list every target must pass", "");
    }

    private static TargeterMeta.Builder locationMeta(String id, String description) {
        return TargeterMeta.builder(id).description(description).produces(TargetKind.LOCATION)
                .param("limit", "maximum targets after sorting", "0")
                .param("sort", "nearest, farthest, random", "none")
                .param("filter", "a condition list every target must pass", "");
    }

    private static List<Target> one(Target target) {
        return List.of(target);
    }

    /** The source list's first location, or the context origin when there is none. */
    private static Location originOf(SkillContext context, List<Target> source) {
        if (source != null && !source.isEmpty()) {
            return source.get(0).location();
        }
        return context.origin();
    }

    /** Every source location, or the context origin when there is none. */
    private static List<Location> basesOf(SkillContext context, List<Target> source) {
        if (source == null || source.isEmpty()) {
            return List.of(context.origin());
        }
        List<Location> bases = new ArrayList<>(source.size());
        for (Target target : source) {
            bases.add(target.location());
        }
        return bases;
    }

    private static List<Player> playersNear(Location centre, double radius) {
        World world = centre.getWorld();
        if (world == null) {
            return List.of();
        }
        List<Player> players = new ArrayList<>();
        double squared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(centre) <= squared) {
                players.add(player);
            }
        }
        return players;
    }

    private static java.util.Collection<Entity> nearby(Location centre, double radius) {
        World world = centre.getWorld();
        return world == null ? List.of() : world.getNearbyEntities(centre, radius, radius, radius);
    }

    private static Set<EntityType> entityTypes(List<String> names) {
        Set<EntityType> types = new HashSet<>();
        for (String name : names) {
            EntityType type = Registries.entityType(name);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private static Entity ownerOf(Engine engine, Entity entity) {
        String owner = entity.getPersistentDataContainer()
                .get(engine.keys().owner, PersistentDataType.STRING);
        return owner == null ? null : entityByUuid(owner);
    }

    private static Entity entityByUuid(String raw) {
        try {
            return Bukkit.getEntity(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Presents a targeter's own parameters as a shape block with {@code type} fixed. */
    private static final class FixedTypeConfig implements dev.bwmp.bestiary.api.skill.MechanicConfig {

        private final dev.bwmp.bestiary.api.skill.MechanicConfig delegate;
        private final String type;

        private FixedTypeConfig(dev.bwmp.bestiary.api.skill.MechanicConfig delegate, String type) {
            this.delegate = delegate;
            this.type = type;
        }

        @Override
        public boolean contains(String key) {
            return key.equals("type") || delegate.contains(key);
        }

        @Override
        public Set<String> keys() {
            return delegate.keys();
        }

        @Override
        public String raw(String key, String fallback) {
            return key.equals("type") ? type : delegate.raw(key, fallback);
        }

        @Override
        public Expression number(String key, double fallback) {
            return delegate.number(key, fallback);
        }

        @Override
        public Expression text(String key, String fallback) {
            return key.equals("type")
                    ? dev.bwmp.bestiary.api.skill.Constant.of(type)
                    : delegate.text(key, fallback);
        }

        @Override
        public Expression require(String key) {
            return delegate.require(key);
        }

        @Override
        public int integer(String key, int fallback) {
            return delegate.integer(key, fallback);
        }

        @Override
        public double decimal(String key, double fallback) {
            return delegate.decimal(key, fallback);
        }

        @Override
        public boolean bool(String key, boolean fallback) {
            return delegate.bool(key, fallback);
        }

        @Override
        public long ticks(String key, long fallback) {
            return delegate.ticks(key, fallback);
        }

        @Override
        public List<String> stringList(String key) {
            return delegate.stringList(key);
        }

        @Override
        public dev.bwmp.bestiary.api.skill.MechanicConfig section(String key) {
            return delegate.section(key);
        }

        @Override
        public <E extends Enum<E>> E enumValue(Class<E> enumType, String key, E fallback) {
            return delegate.enumValue(enumType, key, fallback);
        }

        @Override
        public String source() {
            return delegate.source();
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
