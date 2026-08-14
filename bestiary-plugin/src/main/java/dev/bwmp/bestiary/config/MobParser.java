package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.ai.AiDefinition;
import dev.bwmp.bestiary.api.ai.AiGoalNode;
import dev.bwmp.bestiary.api.ai.GoalCategory;
import dev.bwmp.bestiary.api.ai.NavigationKind;
import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.mob.BossbarDefinition;
import dev.bwmp.bestiary.api.mob.DamageModifiers;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.mob.MobOptions;
import dev.bwmp.bestiary.api.mob.PhaseDefinition;
import dev.bwmp.bestiary.api.mob.ThreatSettings;
import dev.bwmp.bestiary.api.skill.ParameterSpec;
import dev.bwmp.bestiary.util.Registries;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MobParser {

    private MobParser() {
    }

    public static MobDefinition parse(NamespacedKey id, Map<String, Object> section, String source,
                                      int revision) {
        String location = source + " -> " + id;

        Object rawType = SkillParser.lookup(section, "type");
        if (rawType == null) {
            throw new ParseException(location, "mob has no 'type'");
        }
        EntityType type = Registries.entityType(String.valueOf(rawType));
        if (type == null) {
            throw new ParseException(location, "unknown entity type '" + rawType + "'");
        }

        MobDefinition.Builder builder = MobDefinition.builder(id, type)
                .display(string(section, "display", ""))
                .health(number(section, "health", -1, location))
                .damage(number(section, "damage", -1, location))
                .armor(number(section, "armor", -1, location))
                .armorToughness(number(section, "armor_toughness", -1, location))
                .knockbackResistance(number(section, "knockback_resistance", -1, location))
                .movementSpeed(number(section, "movement_speed", -1, location))
                .followRange(number(section, "follow_range", -1, location))
                .scale(number(section, "scale", -1, location))
                .faction(string(section, "faction", ""))
                .dropTable(string(section, "drops", string(section, "drop_table", "")))
                .levelModifier(string(section, "level_modifier", ""))
                .defaultLevel((int) number(section, "level", 1, location))
                .modelEngineModel(string(section, "model", ""))
                .suppressExternalXp(bool(section, "suppress_external_xp", false))
                .source(source)
                .revision(revision);

        builder.options(options(SkillParser.lookup(section, "options"), location));
        equipment(builder, SkillParser.lookup(section, "equipment"), location);
        builder.threat(threat(SkillParser.lookup(section, "threat"), location));
        builder.ai(ai(SkillParser.lookup(section, "ai"), location));
        builder.phases(phases(SkillParser.lookup(section, "phases"), location));
        builder.skills(skills(SkillParser.lookup(section, "skills"), location));
        builder.bossbar(bossbar(SkillParser.lookup(section, "bossbar"), location));
        builder.damageModifiers(damageModifiers(SkillParser.lookup(section, "damage_modifiers"), location));
        return builder.build();
    }

    private static MobOptions options(Object raw, String location) {
        if (!(raw instanceof Map)) {
            return MobOptions.DEFAULT;
        }
        Map<String, Object> map = SkillParser.asMap(raw);
        return MobOptions.builder()
                .despawn(bool(map, "despawn", false))
                .preventOtherDrops(bool(map, "prevent_other_drops", true))
                .preventMobKillDrops(bool(map, "prevent_mob_kill_drops", false))
                .silent(bool(map, "silent", false))
                .collidable(bool(map, "collidable", true))
                .alwaysShowName(bool(map, "always_show_name", false))
                .glowing(bool(map, "glowing", false))
                .invulnerable(bool(map, "invulnerable", false))
                .gravity(bool(map, "gravity", true))
                .ai(bool(map, "ai", true))
                .preventRandomEquipment(bool(map, "prevent_random_equipment", true))
                .preventSunburn(bool(map, "prevent_sunburn", true))
                .digOutOfGround(bool(map, "dig_out_of_ground", true))
                .build();
    }

    private static void equipment(MobDefinition.Builder builder, Object raw, String location) {
        if (!(raw instanceof Map)) {
            return;
        }
        for (Map.Entry<String, Object> entry : SkillParser.asMap(raw).entrySet()) {
            EquipmentSlot slot = slot(entry.getKey());
            if (slot == null) {
                throw new ParseException(location, "unknown equipment slot '" + entry.getKey() + "'");
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nested = SkillParser.asMap(value);
                builder.equip(slot, String.valueOf(SkillParser.lookup(nested, "item")),
                        (float) number(nested, "drop_chance", 0.0d, location));
            } else {
                builder.equip(slot, String.valueOf(value), 0.0f);
            }
        }
    }

    private static EquipmentSlot slot(String written) {
        switch (ParameterSpec.normalize(written)) {
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            case "mainhand":
            case "hand":
            case "weapon":
                return EquipmentSlot.HAND;
            case "offhand":
                return EquipmentSlot.OFF_HAND;
            default:
                return null;
        }
    }

    private static ThreatSettings threat(Object raw, String location) {
        if (!(raw instanceof Map)) {
            return ThreatSettings.DISABLED;
        }
        Map<String, Object> map = SkillParser.asMap(raw);
        return new ThreatSettings(
                bool(map, "enabled", true),
                number(map, "decay", 0.0d, location),
                number(map, "damage_factor", 1.0d, location),
                number(map, "healing_factor", 0.5d, location),
                number(map, "taunt_multiplier", 1.0d, location),
                number(map, "switch_threshold", 1.5d, location));
    }

    private static AiDefinition ai(Object raw, String location) {
        if (!(raw instanceof Map)) {
            return AiDefinition.NONE;
        }
        Map<String, Object> map = SkillParser.asMap(raw);

        List<AiGoalNode> goals = new ArrayList<>();
        Object rawGoals = SkillParser.lookup(map, "goals");
        if (rawGoals instanceof List) {
            List<?> entries = (List<?>) rawGoals;
            for (int index = 0; index < entries.size(); index++) {
                goals.add(goal(entries.get(index), index, location));
            }
        } else if (rawGoals != null) {
            throw new ParseException(location, "'ai.goals' must be a list");
        }

        Object rawNavigation = SkillParser.lookup(map, "navigation");
        NavigationKind navigation = rawNavigation == null
                ? NavigationKind.DEFAULT
                : NavigationKind.parse(String.valueOf(rawNavigation)).orElseThrow(() ->
                new ParseException(location, "unknown navigation '" + rawNavigation
                        + "'; expected default, ground, flying, amphibious or climbing"));
        return new AiDefinition(goals, navigation,
                string(map, "move_control", ""), string(map, "look_control", ""));
    }

    private static AiGoalNode goal(Object entry, int index, String location) {
        if (entry instanceof Map) {
            Map<String, Object> map = SkillParser.asMap(entry);
            Object clear = SkillParser.lookup(map, "clear");
            if (clear != null) {
                return AiGoalNode.clear(categories(clear, location));
            }
            Object type = SkillParser.lookup(map, "type");
            if (type == null) {
                throw new ParseException(location, "an ai goal map needs 'type' or 'clear'");
            }
            Args.Builder args = Args.builder();
            for (Map.Entry<String, Object> field : map.entrySet()) {
                String key = ParameterSpec.normalize(field.getKey());
                if (key.equals("type") || key.equals("priority")) {
                    continue;
                }
                args.put(field.getKey(), SkillParser.convert(field.getValue(), location));
            }
            int priority = (int) number(map, "priority", index, location);
            return AiGoalNode.goal(String.valueOf(type), args.build(), priority);
        }

        String written = String.valueOf(entry).trim();
        if (written.toLowerCase(Locale.ROOT).startsWith("clear")) {
            int colon = written.indexOf(':');
            return colon < 0
                    ? AiGoalNode.clearAll()
                    : AiGoalNode.clear(categories(written.substring(colon + 1), location));
        }
        int brace = written.indexOf('{');
        if (brace < 0) {
            return AiGoalNode.goal(written, Args.EMPTY, index);
        }
        return AiGoalNode.goal(written.substring(0, brace),
                ShorthandParser.parseArgs(written.substring(brace), location), index);
    }

    private static Set<GoalCategory> categories(Object raw, String location) {
        Set<GoalCategory> categories = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        if (raw instanceof List) {
            for (Object element : (List<?>) raw) {
                names.add(String.valueOf(element));
            }
        } else {
            for (String part : String.valueOf(raw).split("[,\\[\\]]")) {
                if (!part.isBlank()) {
                    names.add(part.trim());
                }
            }
        }
        if (names.isEmpty()) {
            return Set.of(GoalCategory.values());
        }
        for (String name : names) {
            categories.add(GoalCategory.parse(name).orElseThrow(() ->
                    new ParseException(location, "unknown goal category '" + name
                            + "'; expected MOVE, LOOK, JUMP or TARGET")));
        }
        return categories;
    }

    private static List<PhaseDefinition> phases(Object raw, String location) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            throw new ParseException(location, "'phases' must be a list");
        }
        List<PhaseDefinition> phases = new ArrayList<>();
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map)) {
                throw new ParseException(location, "a phase must be a map");
            }
            Map<String, Object> map = SkillParser.asMap(entry);
            Object name = SkillParser.lookup(map, "name");
            if (name == null) {
                throw new ParseException(location, "a phase has no 'name'");
            }
            phases.add(new PhaseDefinition(String.valueOf(name),
                    untilConditions(SkillParser.lookup(map, "until"), location + " -> phase " + name),
                    string(map, "on_enter", ""),
                    string(map, "on_exit", ""),
                    string(map, "bossbar_title", "")));
        }
        return phases;
    }

    /**
     * {@code until: { health_percent: "<= 60" }} is shorthand for a condition
     * list, and reads far better than one for a phase gate. Each entry becomes
     * a condition whose value is offered as both {@code amount} and {@code is},
     * so numeric and string conditions both find it under the name they
     * declared.
     */
    public static List<ConditionNode> untilConditions(Object raw, String location) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List) {
            return SkillParser.parseConditions(raw, location);
        }
        if (!(raw instanceof Map)) {
            throw new ParseException(location, "'until' must be a map or a list");
        }
        List<ConditionNode> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : SkillParser.asMap(raw).entrySet()) {
            Object value = SkillParser.convert(entry.getValue(), location);
            conditions.add(new ConditionNode(entry.getKey(),
                    Args.builder().put("amount", value).put("is", value).build(), false));
        }
        return conditions;
    }

    private static List<SkillNode> skills(Object raw, String location) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            throw new ParseException(location, "'skills' must be a list");
        }
        List<SkillNode> nodes = new ArrayList<>();
        List<?> entries = (List<?>) raw;
        for (int index = 0; index < entries.size(); index++) {
            nodes.add(SkillParser.parseNode(entries.get(index), location + " -> skills[" + index + "]"));
        }
        return nodes;
    }

    private static BossbarDefinition bossbar(Object raw, String location) {
        if (!(raw instanceof Map)) {
            return BossbarDefinition.NONE;
        }
        Map<String, Object> map = SkillParser.asMap(raw);
        BarColor colour = enumOf(BarColor.class, string(map, "color", string(map, "colour", "white")),
                BarColor.WHITE, location, "bossbar colour");
        BarStyle style = enumOf(BarStyle.class, string(map, "style", "solid"),
                BarStyle.SOLID, location, "bossbar style");
        return new BossbarDefinition(bool(map, "enabled", true),
                string(map, "title", ""), colour, style,
                number(map, "range", 48.0d, location),
                bool(map, "show_health", true));
    }

    private static DamageModifiers damageModifiers(Object raw, String location) {
        if (!(raw instanceof Map)) {
            return DamageModifiers.NONE;
        }
        Map<String, Object> map = SkillParser.asMap(raw);
        Map<DamageCause, Double> byCause = new EnumMap<>(DamageCause.class);
        Object causes = SkillParser.lookup(map, "causes");
        if (causes instanceof Map) {
            for (Map.Entry<String, Object> entry : SkillParser.asMap(causes).entrySet()) {
                DamageCause cause = enumOf(DamageCause.class, entry.getKey(), null, location, "damage cause");
                if (cause == null) {
                    throw new ParseException(location, "unknown damage cause '" + entry.getKey() + "'");
                }
                byCause.put(cause, Double.parseDouble(String.valueOf(entry.getValue())));
            }
        }
        return new DamageModifiers(byCause,
                number(map, "melee", 1.0d, location),
                number(map, "projectile", 1.0d, location),
                number(map, "magic", 1.0d, location));
    }

    // --- small readers ----------------------------------------------------

    static String string(Map<String, Object> map, String key, String fallback) {
        Object value = SkillParser.lookup(map, key);
        return value == null ? fallback : String.valueOf(value);
    }

    static double number(Map<String, Object> map, String key, double fallback, String location) {
        Object value = SkillParser.lookup(map, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new ParseException(location, "'" + key + "' must be a number, found '" + value + "'");
        }
    }

    static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = SkillParser.lookup(map, key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("yes") || text.equals("on") || text.equals("1");
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String written, E fallback, String location,
                                        String what) {
        if (written == null || written.isBlank()) {
            return fallback;
        }
        String normalized = written.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        if (fallback != null) {
            throw new ParseException(location, "unknown " + what + " '" + written + "'");
        }
        return null;
    }
}
