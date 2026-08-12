package dev.bwmp.bestiary.condition;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.skill.ConditionMeta;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.VariableScope;
import dev.bwmp.bestiary.config.ArgsConfig;
import dev.bwmp.bestiary.config.SkillParser;
import dev.bwmp.bestiary.expression.Comparison;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.SkillCompiler;
import dev.bwmp.bestiary.util.Registries;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Every built-in condition.
 * <p>
 * Numeric ones take a comparator prefix — {@code health{amount="<= 30"}} — and
 * a bare number means {@code =}. Meta conditions ({@code all_of},
 * {@code any_of}, {@code none_of}) make boolean grouping a condition rather
 * than syntax, which is why the parser has no notion of it.
 */
public final class BuiltinConditions {

    private BuiltinConditions() {
    }

    public static Map<String, ConditionType> all(Engine engine, Supplier<SkillCompiler> compiler) {
        Map<String, ConditionType> types = new LinkedHashMap<>();
        registerEntity(types, engine);
        registerWorld(types, engine);
        registerMeta(types, engine, compiler);
        return types;
    }

    // --- entity -----------------------------------------------------------

    private static void registerEntity(Map<String, ConditionType> into, Engine engine) {

        into.put("health", numeric("health", "The target's current health.",
                target -> target.living() == null ? 0.0d : target.living().getHealth()));

        into.put("health_percent", numeric("health_percent", "The target's health as a percentage.",
                target -> {
                    LivingEntity living = target.living();
                    if (living == null) {
                        return 0.0d;
                    }
                    double max = dev.bwmp.bestiary.mechanic.DamageMechanics.maxHealth(living);
                    return max <= 0 ? 0.0d : living.getHealth() / max * 100.0d;
                }));

        into.put("food", numeric("food", "A player's hunger level.",
                target -> target.player() == null ? 0.0d : target.player().getFoodLevel()));

        into.put("altitude", Conditions.type(
                ConditionMeta.builder("altitude").description("The target's Y coordinate.")
                        .evaluates(TargetKind.ANY)
                        .required("amount", "a comparison such as \"> 170\"", "a", "value", "y")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> target != null
                            && comparison.test(target.location().getY(), context, target);
                }));

        into.put("distance", Conditions.type(
                ConditionMeta.builder("distance").description("Distance from the caster to the target.")
                        .evaluates(TargetKind.ANY)
                        .required("amount", "a comparison such as \"<= 8\"", "a", "value", "d")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> {
                        if (target == null || context.caster() == null) {
                            return false;
                        }
                        Location from = context.caster().getLocation();
                        Location to = target.location();
                        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                            return false;
                        }
                        return comparison.test(from.distance(to), context, target);
                    };
                }));

        into.put("level", Conditions.type(
                ConditionMeta.builder("level").description("A Bestiary mob's level.")
                        .evaluates(TargetKind.ENTITY)
                        .required("amount", "a comparison such as \">= 5\"", "a", "value")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> {
                        MobInstance instance = instance(engine, target);
                        return instance != null && comparison.test(instance.level(), context, target);
                    };
                }));

        into.put("name", stringMatch("name", "The target's display name, formatting stripped.",
                TargetKind.ENTITY,
                (engineRef, target) -> {
                    Entity entity = target.entity();
                    if (entity == null) {
                        return "";
                    }
                    String custom = entity.getCustomName();
                    return custom != null && !custom.isEmpty()
                            ? dev.bwmp.bestiary.text.Text.stripLegacy(custom)
                            : entity.getName();
                }));

        into.put("entity_type", stringMatch("entity_type", "The target's entity type.", TargetKind.ENTITY,
                (engineRef, target) -> target.entity() == null
                        ? "" : target.entity().getType().name().toLowerCase(Locale.ROOT)));

        into.put("mob_type", stringMatch("mob_type", "The target's Bestiary mob id.", TargetKind.ENTITY,
                (engineRef, target) -> {
                    MobInstance instance = instance(engineRef, target);
                    return instance == null ? "" : instance.definition().id().toString();
                }));

        into.put("faction", stringMatch("faction", "The target's faction.", TargetKind.ENTITY,
                (engineRef, target) -> {
                    MobInstance instance = instance(engineRef, target);
                    if (instance == null) {
                        return "";
                    }
                    Object override = instance.variables().get("faction");
                    return override != null ? String.valueOf(override) : instance.definition().faction();
                }));

        into.put("stance", stringMatch("stance", "The target's stance variable.", TargetKind.ENTITY,
                (engineRef, target) -> {
                    MobInstance instance = instance(engineRef, target);
                    Object stance = instance == null ? null : instance.variables().get("stance");
                    return stance == null ? "" : String.valueOf(stance);
                }));

        // `phase` reads the caster rather than the target, so it does not fit
        // the stringMatch shape the conditions above share.
        into.put("phase", Conditions.type(
                ConditionMeta.builder("phase").description("The caster's current phase name.")
                        .evaluates(TargetKind.ANY)
                        .required("is", "the phase name", "value", "name", "phase")
                        .build(),
                config -> {
                    Expression expected = config.text("is", "");
                    return (context, target) -> {
                        MobInstance instance = engine.mobs().instance(context.caster());
                        return instance != null
                                && instance.phase().equalsIgnoreCase(expected.asString(context, target));
                    };
                }));

        into.put("is_player", flag("is_player", "The target is a player.",
                target -> target.player() != null));
        into.put("is_living", flag("is_living", "The target is a living entity.",
                target -> target.isLiving()));

        into.put("is_caster", Conditions.type(
                ConditionMeta.builder("is_caster").description("The target is the caster itself.")
                        .evaluates(TargetKind.ENTITY).build(),
                config -> (context, target) -> target != null && target.entity() != null
                        && target.entity().equals(context.caster())));

        into.put("on_ground", flag("on_ground", "The target is standing on something.",
                target -> target.entity() != null && target.entity().isOnGround()));
        into.put("in_water", flag("in_water", "The target is in water.",
                target -> target.entity() != null && target.entity().isInWater()));
        into.put("in_lava", flag("in_lava", "The target is standing in lava.",
                target -> target.entity() != null
                        && target.entity().getLocation().getBlock().getType() == Material.LAVA));
        into.put("burning", flag("burning", "The target is on fire.",
                target -> target.entity() != null && target.entity().getFireTicks() > 0));
        into.put("frozen", flag("frozen", "The target is freezing.",
                target -> target.entity() != null && target.entity().getFreezeTicks() > 0));
        into.put("gliding", flag("gliding", "The target is gliding.",
                target -> target.living() != null && target.living().isGliding()));
        into.put("sneaking", flag("sneaking", "The target player is sneaking.",
                target -> target.player() != null && target.player().isSneaking()));
        into.put("sprinting", flag("sprinting", "The target player is sprinting.",
                target -> target.player() != null && target.player().isSprinting()));
        into.put("blocking", flag("blocking", "The target player is blocking with a shield.",
                target -> target.player() != null && target.player().isBlocking()));

        into.put("line_of_sight", Conditions.type(
                ConditionMeta.builder("line_of_sight")
                        .description("The caster can see the target.")
                        .evaluates(TargetKind.ENTITY).build(),
                config -> (context, target) -> {
                    LivingEntity caster = context.casterLiving();
                    return caster != null && target != null && target.entity() != null
                            && caster.hasLineOfSight(target.entity());
                }));

        into.put("has_aura", Conditions.type(
                ConditionMeta.builder("has_aura").description("The target carries a named aura.")
                        .evaluates(TargetKind.ENTITY)
                        .required("name", "the aura name", "n", "aura")
                        .param("stacks", "a comparison on the stack count", "")
                        .build(),
                config -> {
                    String name = config.raw("name", "");
                    String stacksRaw = config.raw("stacks", "");
                    Comparison stacks = stacksRaw.isEmpty()
                            ? null : Comparison.parse(engine.expressions(), stacksRaw, config.source());
                    return (context, target) -> {
                        if (target == null || target.entity() == null) {
                            return false;
                        }
                        if (!engine.auras().has(target.entity(), name)) {
                            return false;
                        }
                        return stacks == null
                                || stacks.test(engine.auras().stacks(target.entity(), name), context, target);
                    };
                }));

        into.put("owner", Conditions.type(
                ConditionMeta.builder("owner").description("The caster owns the target, or the reverse.")
                        .evaluates(TargetKind.ENTITY)
                        .param("reverse", "test whether the target owns the caster", "false")
                        .build(),
                config -> {
                    boolean reverse = config.bool("reverse", false);
                    return (context, target) -> {
                        if (target == null || target.entity() == null) {
                            return false;
                        }
                        Entity holder = reverse ? context.caster() : target.entity();
                        Entity expected = reverse ? target.entity() : context.caster();
                        String owner = holder.getPersistentDataContainer()
                                .get(engine.keys().owner, org.bukkit.persistence.PersistentDataType.STRING);
                        return owner != null && owner.equals(expected.getUniqueId().toString());
                    };
                }));

        into.put("has_permission", Conditions.type(
                ConditionMeta.builder("has_permission").description("The target player holds a permission.")
                        .evaluates(TargetKind.ENTITY)
                        .required("permission", "the node", "p", "node")
                        .build(),
                config -> {
                    String node = config.raw("permission", "");
                    return (context, target) -> target != null && target.player() != null
                            && target.player().hasPermission(node);
                }));

        into.put("has_item", Conditions.type(
                ConditionMeta.builder("has_item").description("The target player carries an item.")
                        .evaluates(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i", "material")
                        .param("amount", "how many are needed", "1", "a")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    int amount = config.integer("amount", 1);
                    return (context, target) -> {
                        Player player = target == null ? null : target.player();
                        if (player == null) {
                            return false;
                        }
                        ItemStack wanted = engine.hooks().sigil().resolveItem(item);
                        if (wanted == null) {
                            return false;
                        }
                        int found = 0;
                        for (ItemStack stack : player.getInventory().getContents()) {
                            if (stack != null && stack.isSimilar(wanted)) {
                                found += stack.getAmount();
                            }
                        }
                        return found >= amount;
                    };
                }));

        into.put("holding", Conditions.type(
                ConditionMeta.builder("holding").description("The target is holding an item.")
                        .evaluates(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i", "material")
                        .param("off_hand", "check the off hand instead", "false")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    boolean offHand = config.bool("off_hand", false);
                    return (context, target) -> {
                        LivingEntity entity = target == null ? null : target.living();
                        if (entity == null || entity.getEquipment() == null) {
                            return false;
                        }
                        ItemStack held = offHand
                                ? entity.getEquipment().getItemInOffHand()
                                : entity.getEquipment().getItemInMainHand();
                        ItemStack wanted = engine.hooks().sigil().resolveItem(item);
                        return wanted != null && held.isSimilar(wanted);
                    };
                }));

        into.put("wearing", Conditions.type(
                ConditionMeta.builder("wearing").description("The target is wearing an item.")
                        .evaluates(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i", "material")
                        .param("slot", "head, chest, legs or feet; empty means any", "")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    String slot = config.raw("slot", "").toLowerCase(Locale.ROOT);
                    return (context, target) -> {
                        LivingEntity entity = target == null ? null : target.living();
                        if (entity == null || entity.getEquipment() == null) {
                            return false;
                        }
                        ItemStack wanted = engine.hooks().sigil().resolveItem(item);
                        if (wanted == null) {
                            return false;
                        }
                        ItemStack[] armour = entity.getEquipment().getArmorContents();
                        // getArmorContents is boots, leggings, chestplate, helmet.
                        int index = switch (slot) {
                            case "feet", "boots" -> 0;
                            case "legs", "leggings" -> 1;
                            case "chest", "chestplate" -> 2;
                            case "head", "helmet" -> 3;
                            default -> -1;
                        };
                        if (index >= 0) {
                            return armour.length > index && armour[index] != null
                                    && armour[index].isSimilar(wanted);
                        }
                        for (ItemStack piece : armour) {
                            if (piece != null && piece.isSimilar(wanted)) {
                                return true;
                            }
                        }
                        return false;
                    };
                }));

        into.put("variable", Conditions.type(
                ConditionMeta.builder("variable").description("A variable's value.")
                        .evaluates(TargetKind.ANY)
                        .required("name", "variable name", "n", "var", "key")
                        .param("scope", "skill, mob, target or global", "skill", "s")
                        .param("amount", "a numeric comparison", "", "value", "a")
                        .param("is", "an exact string match", "", "equals")
                        .build(),
                config -> {
                    String name = config.raw("name", "");
                    VariableScope scope = VariableScope.parse(config.raw("scope", "skill"), VariableScope.SKILL);
                    String amountRaw = config.raw("amount", "");
                    Comparison comparison = amountRaw.isEmpty()
                            ? null : Comparison.parse(engine.expressions(), amountRaw, config.source());
                    Expression expected = config.contains("is") ? config.text("is", "") : null;
                    return (context, target) -> {
                        Object value = context.variable(scope, name);
                        if (expected != null) {
                            return value != null && String.valueOf(value)
                                    .equalsIgnoreCase(expected.asString(context, target));
                        }
                        if (comparison != null) {
                            return comparison.test(
                                    dev.bwmp.bestiary.mechanic.StateMechanics.asNumber(value), context, target);
                        }
                        // With neither, the question is simply "is it set?".
                        return value != null;
                    };
                }));

        into.put("score", Conditions.type(
                ConditionMeta.builder("score").description("A scoreboard objective's value for the target.")
                        .evaluates(TargetKind.ENTITY)
                        .required("objective", "objective name", "o", "name")
                        .required("amount", "a comparison", "a", "value")
                        .build(),
                config -> {
                    String objectiveName = config.raw("objective", "");
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> {
                        Entity entity = target == null ? null : target.entity();
                        if (entity == null) {
                            return false;
                        }
                        var objective = org.bukkit.Bukkit.getScoreboardManager()
                                .getMainScoreboard().getObjective(objectiveName);
                        if (objective == null) {
                            return false;
                        }
                        var score = objective.getScore(entity instanceof Player
                                ? entity.getName() : entity.getUniqueId().toString());
                        return comparison.test(score.getScore(), context, target);
                    };
                }));

        into.put("cooldown", Conditions.type(
                ConditionMeta.builder("cooldown")
                        .description("Whether a named skill is off cooldown for the caster.")
                        .evaluates(TargetKind.ANY)
                        .required("skill", "the skill id", "s", "name")
                        .build(),
                config -> {
                    String skillId = config.raw("skill", "");
                    return (context, target) -> engine.mobs().instance(context.caster()) == null
                            || !engine.executor().onCooldown(context.caster(), skillId);
                }));
    }

    // --- world ------------------------------------------------------------

    private static void registerWorld(Map<String, ConditionType> into, Engine engine) {

        into.put("biome", stringMatch("biome", "The biome at the target.", TargetKind.LOCATION,
                (engineRef, target) -> {
                    Location location = target.location();
                    return location.getWorld() == null ? ""
                            : location.getBlock().getBiome().getKey().getKey();
                }));

        into.put("world", stringMatch("world", "The world the target is in.", TargetKind.LOCATION,
                (engineRef, target) -> {
                    World world = target.location().getWorld();
                    return world == null ? "" : world.getName();
                }));

        into.put("block_type", stringMatch("block_type", "The block at the target.", TargetKind.LOCATION,
                (engineRef, target) -> target.location().getBlock().getType().getKey().getKey()));

        into.put("light_level", Conditions.type(
                ConditionMeta.builder("light_level").description("Total light at the target.")
                        .evaluates(TargetKind.LOCATION)
                        .required("amount", "a comparison such as \"< 8\"", "a", "value")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> target != null
                            && comparison.test(target.location().getBlock().getLightLevel(), context, target);
                }));

        into.put("sunlight", Conditions.type(
                ConditionMeta.builder("sunlight").description("Sky light at the target.")
                        .evaluates(TargetKind.LOCATION)
                        .required("amount", "a comparison", "a", "value")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> target != null && comparison.test(
                            target.location().getBlock().getLightFromSky(), context, target);
                }));

        into.put("is_day", worldFlag("is_day", "It is daytime where the target is.",
                world -> world.getTime() < 13000L));
        into.put("is_night", worldFlag("is_night", "It is night where the target is.",
                world -> world.getTime() >= 13000L));
        into.put("raining", worldFlag("raining", "It is raining where the target is.", World::hasStorm));
        into.put("thundering", worldFlag("thundering", "It is thundering where the target is.",
                World::isThundering));

        into.put("moon_phase", Conditions.type(
                ConditionMeta.builder("moon_phase").description("The moon phase, 0 to 7.")
                        .evaluates(TargetKind.LOCATION)
                        .required("amount", "a comparison, or an exact phase", "a", "value", "phase")
                        .build(),
                config -> {
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> {
                        World world = target == null ? null : target.location().getWorld();
                        if (world == null) {
                            return false;
                        }
                        return comparison.test((world.getFullTime() / 24000L) % 8L, context, target);
                    };
                }));

        into.put("players_in_radius_count", Conditions.type(
                ConditionMeta.builder("players_in_radius_count")
                        .description("How many players are near the target.")
                        .evaluates(TargetKind.LOCATION)
                        .param("radius", "how far", "16", "r")
                        .required("amount", "a comparison such as \">= 3\"", "a", "value", "count")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 16);
                    Comparison comparison = comparison(engine, config);
                    return (context, target) -> {
                        if (target == null) {
                            return false;
                        }
                        Location centre = target.location();
                        World world = centre.getWorld();
                        if (world == null) {
                            return false;
                        }
                        double range = radius.asDouble(context, target);
                        long count = world.getPlayers().stream()
                                .filter(player -> player.getLocation().distanceSquared(centre) <= range * range)
                                .count();
                        return comparison.test(count, context, target);
                    };
                }));

        into.put("in_region", Conditions.type(
                ConditionMeta.builder("in_region")
                        .description("The target is inside a WorldGuard region. False without WorldGuard.")
                        .evaluates(TargetKind.LOCATION)
                        .required("region", "the region id", "r", "id", "name")
                        .build(),
                config -> {
                    String region = config.raw("region", "");
                    return (context, target) -> target != null
                            && engine.hooks().regions().inRegion(target.location(), region);
                }));

        into.put("in_claim", Conditions.type(
                ConditionMeta.builder("in_claim")
                        .description("The target is inside a GriefPrevention claim. "
                                + "False without GriefPrevention.")
                        .evaluates(TargetKind.LOCATION)
                        .build(),
                config -> (context, target) -> target != null
                        && engine.hooks().regions().inClaim(target.location())));

        into.put("structure", Conditions.type(
                ConditionMeta.builder("structure")
                        .description("The target is inside a generated structure.")
                        .evaluates(TargetKind.LOCATION)
                        .required("structure", "the structure key, e.g. village_plains", "s", "name")
                        .build(),
                config -> {
                    String wanted = config.raw("structure", "").toLowerCase(Locale.ROOT);
                    return (context, target) -> {
                        if (target == null) {
                            return false;
                        }
                        Location location = target.location();
                        World world = location.getWorld();
                        if (world == null) {
                            return false;
                        }
                        // Structure lookup landed at different times on
                        // different forks, so it is reached reflectively and
                        // simply answers false where it does not exist.
                        try {
                            var method = World.class.getMethod("getStructures", int.class, int.class);
                            Object structures = method.invoke(world,
                                    location.getChunk().getX(), location.getChunk().getZ());
                            for (Object structure : (Iterable<?>) structures) {
                                Object type = structure.getClass().getMethod("getStructure").invoke(structure);
                                Object key = type.getClass().getMethod("getKey").invoke(type);
                                if (String.valueOf(key).toLowerCase(Locale.ROOT).endsWith(wanted)) {
                                    return true;
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            return false;
                        }
                        return false;
                    };
                }));
    }

    // --- meta -------------------------------------------------------------

    private static void registerMeta(Map<String, ConditionType> into, Engine engine,
                                     Supplier<SkillCompiler> compiler) {

        into.put("chance", Conditions.type(
                ConditionMeta.builder("chance").description("Passes at random.")
                        .evaluates(TargetKind.ANY)
                        .param("chance", "0..1", "0.5", "c", "value", "amount")
                        .build(),
                config -> {
                    Expression chance = config.number("chance", 0.5d);
                    return (context, target) -> java.util.concurrent.ThreadLocalRandom.current()
                            .nextDouble() < chance.asDouble(context, target);
                }));

        into.put("all_of", group("all_of", "Every nested condition must pass.", compiler,
                (conditions, context, target) -> {
                    for (CompiledCondition condition : conditions) {
                        if (!condition.test(context, target)) {
                            return false;
                        }
                    }
                    return true;
                }));

        into.put("any_of", group("any_of", "At least one nested condition must pass.", compiler,
                (conditions, context, target) -> {
                    for (CompiledCondition condition : conditions) {
                        if (condition.test(context, target)) {
                            return true;
                        }
                    }
                    return false;
                }));

        into.put("none_of", group("none_of", "No nested condition may pass.", compiler,
                (conditions, context, target) -> {
                    for (CompiledCondition condition : conditions) {
                        if (condition.test(context, target)) {
                            return false;
                        }
                    }
                    return true;
                }));
    }

    private interface GroupTest {
        boolean test(List<CompiledCondition> conditions, dev.bwmp.bestiary.api.skill.SkillContext context,
                     dev.bwmp.bestiary.api.skill.Target target);
    }

    private static ConditionType group(String id, String description, Supplier<SkillCompiler> compiler,
                                       GroupTest test) {
        return Conditions.type(
                ConditionMeta.builder(id).description(description)
                        .evaluates(TargetKind.ANY)
                        .required("conditions", "a nested condition list", "c", "list")
                        .build(),
                config -> {
                    List<Object> raw = config instanceof ArgsConfig
                            ? ((ArgsConfig) config).rawList("conditions")
                            : List.of();
                    if (raw.isEmpty()) {
                        for (String written : config.stringList("conditions")) {
                            raw = new ArrayList<>(raw);
                            raw.add(written);
                        }
                    }
                    List<ConditionNode> nodes = new ArrayList<>();
                    for (Object entry : raw) {
                        nodes.add(SkillParser.parseCondition(entry, config.source()));
                    }
                    SkillCompiler skillCompiler = compiler.get();
                    if (skillCompiler == null) {
                        throw new IllegalStateException("condition groups are not available yet");
                    }
                    List<CompiledCondition> compiled =
                            skillCompiler.compileConditions(nodes, config.source(), TargetKind.ANY);
                    return (context, target) -> test.test(compiled, context, target);
                });
    }

    // --- helpers ----------------------------------------------------------

    private static ConditionType numeric(String id, String description,
                                         ToDoubleFunction<dev.bwmp.bestiary.api.skill.Target> reader) {
        return new ConditionType() {
            private final ConditionMeta meta = ConditionMeta.builder(id)
                    .description(description)
                    .evaluates(TargetKind.ENTITY)
                    .required("amount", "a comparison such as \"<= 50\"", "a", "value")
                    .build();

            @Override
            public ConditionMeta meta() {
                return meta;
            }

            @Override
            public dev.bwmp.bestiary.api.skill.Condition create(MechanicConfig config) {
                String raw = config.raw("amount", "");
                if (raw.isEmpty()) {
                    throw new IllegalArgumentException("condition '" + id + "' needs amount=<comparison>");
                }
                Comparison comparison = Comparison.parse(EngineHolder.expressions(), raw, config.source());
                return new dev.bwmp.bestiary.api.skill.Condition() {
                    @Override
                    public ConditionMeta meta() {
                        return meta;
                    }

                    @Override
                    public boolean test(dev.bwmp.bestiary.api.skill.SkillContext context,
                                        dev.bwmp.bestiary.api.skill.Target target) {
                        return target != null
                                && comparison.test(reader.applyAsDouble(target), context, target);
                    }
                };
            }
        };
    }

    private static ConditionType flag(String id, String description,
                                      Predicate<dev.bwmp.bestiary.api.skill.Target> test) {
        return Conditions.type(
                ConditionMeta.builder(id).description(description).evaluates(TargetKind.ENTITY).build(),
                config -> (context, target) -> target != null && test.test(target));
    }

    private static ConditionType worldFlag(String id, String description, Predicate<World> test) {
        return Conditions.type(
                ConditionMeta.builder(id).description(description).evaluates(TargetKind.LOCATION).build(),
                config -> (context, target) -> {
                    World world = target == null ? null : target.location().getWorld();
                    return world != null && test.test(world);
                });
    }

    private static ConditionType stringMatch(String id, String description, TargetKind evaluates,
                                             BiPredicateString reader) {
        return new ConditionType() {
            private final ConditionMeta meta = ConditionMeta.builder(id)
                    .description(description)
                    .evaluates(evaluates)
                    .required("is", "the expected value; several may be comma-separated",
                            "value", "equals", "type", "name")
                    .build();

            @Override
            public ConditionMeta meta() {
                return meta;
            }

            @Override
            public dev.bwmp.bestiary.api.skill.Condition create(MechanicConfig config) {
                List<String> expected = config.stringList("is");
                if (expected.isEmpty()) {
                    throw new IllegalArgumentException("condition '" + id + "' needs is=<value>");
                }
                Set<String> wanted = new java.util.HashSet<>();
                for (String value : expected) {
                    wanted.add(value.trim().toLowerCase(Locale.ROOT));
                }
                Engine engine = EngineHolder.engine();
                return new dev.bwmp.bestiary.api.skill.Condition() {
                    @Override
                    public ConditionMeta meta() {
                        return meta;
                    }

                    @Override
                    public boolean test(dev.bwmp.bestiary.api.skill.SkillContext context,
                                        dev.bwmp.bestiary.api.skill.Target target) {
                        if (target == null) {
                            return false;
                        }
                        String actual = reader.read(engine, target);
                        if (actual == null) {
                            return false;
                        }
                        String normalized = actual.toLowerCase(Locale.ROOT);
                        if (wanted.contains(normalized)) {
                            return true;
                        }
                        // A namespaced value matches its bare key too, so
                        // `biome{is=plains}` works against `minecraft:plains`.
                        int colon = normalized.indexOf(':');
                        return colon >= 0 && wanted.contains(normalized.substring(colon + 1));
                    }
                };
            }
        };
    }

    @FunctionalInterface
    private interface BiPredicateString {
        String read(Engine engine, dev.bwmp.bestiary.api.skill.Target target);
    }

    private static Comparison comparison(Engine engine, MechanicConfig config) {
        String raw = config.raw("amount", "");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("this condition needs amount=<comparison>");
        }
        return Comparison.parse(engine.expressions(), raw, config.source());
    }

    private static MobInstance instance(Engine engine, dev.bwmp.bestiary.api.skill.Target target) {
        if (target == null || target.entity() == null) {
            return null;
        }
        return engine.mobs().instance(target.entity());
    }

    /**
     * The engine, for the two condition factories that cannot take it as a
     * parameter without duplicating their whole body per condition.
     * <p>
     * Set once at plugin enable and never replaced, so this is a bootstrap
     * detail rather than shared mutable state.
     */
    public static final class EngineHolder {

        private static volatile Engine engine;

        private EngineHolder() {
        }

        public static void set(Engine value) {
            engine = value;
        }

        static Engine engine() {
            return engine;
        }

        static dev.bwmp.bestiary.expression.ExpressionEngine expressions() {
            return engine.expressions();
        }
    }
}
