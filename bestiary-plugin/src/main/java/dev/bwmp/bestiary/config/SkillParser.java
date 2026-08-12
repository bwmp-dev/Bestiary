package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.api.config.TriggerNode;
import dev.bwmp.bestiary.api.skill.ParameterSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structured front-end, and the branch point between the two forms.
 * <p>
 * A list entry that is a string goes to {@link ShorthandParser}; one that is a
 * map is read here. Both produce the identical {@link SkillNode} tree, so
 * mixing forms within one file costs nothing and there is one validator and one
 * set of error messages.
 * <p>
 * Operates on plain {@code Map}s rather than Bukkit's
 * {@code ConfigurationSection} so the parser is testable on the JVM with no
 * server — which, for a parser, is where a one-pass build silently goes wrong.
 */
public final class SkillParser {

    /** Keys a structured mechanic map uses for itself; everything else is a parameter. */
    private static final List<String> RESERVED =
            List.of("type", "mechanic", "targeter", "target", "conditions", "trigger", "skills", "children");

    private SkillParser() {
    }

    public static SkillDefinition parseSkill(String id, Map<String, Object> section, String source, int revision) {
        long cooldown = 0L;
        Object rawCooldown = lookup(section, "cooldown");
        if (rawCooldown != null) {
            try {
                cooldown = Durations.parseTicks(String.valueOf(rawCooldown));
            } catch (IllegalArgumentException exception) {
                throw new ParseException(source + " -> " + id, exception.getMessage());
            }
        }

        List<ConditionNode> conditions = parseConditions(lookup(section, "conditions"), source + " -> " + id);

        Object rawLines = lookup(section, "skills");
        if (rawLines == null) {
            rawLines = lookup(section, "mechanics");
        }
        if (rawLines == null) {
            throw new ParseException(source + " -> " + id, "skill has no 'skills:' list");
        }
        if (!(rawLines instanceof List)) {
            throw new ParseException(source + " -> " + id, "'skills' must be a list, found "
                    + rawLines.getClass().getSimpleName());
        }

        List<SkillNode> lines = new ArrayList<>();
        List<?> entries = (List<?>) rawLines;
        for (int index = 0; index < entries.size(); index++) {
            lines.add(parseNode(entries.get(index), source + " -> " + id + "[" + index + "]"));
        }
        return new SkillDefinition(id, cooldown, conditions, lines, source, revision);
    }

    /** One list entry, in whichever form it was written. */
    public static SkillNode parseNode(Object entry, String source) {
        if (entry == null) {
            throw new ParseException(source, "empty skill line");
        }
        if (entry instanceof String) {
            return ShorthandParser.parseLine((String) entry, source);
        }
        if (entry instanceof Map) {
            return parseStructured(asMap(entry), source);
        }
        throw new ParseException(source, "a skill line must be a string or a map, found "
                + entry.getClass().getSimpleName());
    }

    private static SkillNode parseStructured(Map<String, Object> map, String source) {
        Object rawType = lookup(map, "type");
        if (rawType == null) {
            rawType = lookup(map, "mechanic");
        }
        if (rawType == null) {
            throw new ParseException(source, "mechanic map has no 'type'");
        }
        String type = String.valueOf(rawType).trim();
        if (type.isEmpty()) {
            throw new ParseException(source, "mechanic 'type' is empty");
        }

        Args.Builder args = Args.builder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (isReserved(entry.getKey())) {
                continue;
            }
            args.put(entry.getKey(), convert(entry.getValue(), source));
        }

        Object rawTargeter = lookup(map, "targeter");
        if (rawTargeter == null) {
            rawTargeter = lookup(map, "target");
        }
        TargeterNode targeter = rawTargeter == null ? null : parseTargeter(rawTargeter, source);

        List<ConditionNode> conditions = parseConditions(lookup(map, "conditions"), source);
        TriggerNode trigger = parseTrigger(lookup(map, "trigger"), source);

        Object rawChildren = lookup(map, "skills");
        if (rawChildren == null) {
            rawChildren = lookup(map, "children");
        }
        List<SkillNode> children = new ArrayList<>();
        if (rawChildren instanceof List) {
            List<?> entries = (List<?>) rawChildren;
            for (int index = 0; index < entries.size(); index++) {
                children.add(parseNode(entries.get(index), source + "[" + index + "]"));
            }
        } else if (rawChildren != null) {
            throw new ParseException(source, "nested 'skills' must be a list");
        }

        return new SkillNode(type, args.build(), targeter, conditions, trigger, children, source);
    }

    public static TargeterNode parseTargeter(Object raw, String source) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            // A whole shorthand targeter, `of` chain included, is legal as a
            // YAML value. Useful when only the targeter is awkward to express
            // structurally.
            List<String> tokens = ShorthandParser.tokenize(text, source);
            TargeterNode head = ShorthandParser.parseTargeter(tokens.get(0), source);
            for (int index = 1; index < tokens.size(); index++) {
                if (tokens.get(index).equalsIgnoreCase("of") && index + 1 < tokens.size()) {
                    head = new TargeterNode(head.type(), head.args(),
                            ShorthandParser.parseTargeter(tokens.get(++index), source));
                } else {
                    throw new ParseException(source, "unexpected '" + tokens.get(index) + "' in targeter '" + text + "'");
                }
            }
            return head;
        }
        if (!(raw instanceof Map)) {
            throw new ParseException(source, "'targeter' must be a string or a map, found "
                    + raw.getClass().getSimpleName());
        }

        Map<String, Object> map = asMap(raw);
        Object rawType = lookup(map, "type");
        if (rawType == null) {
            throw new ParseException(source, "targeter map has no 'type'");
        }

        Args.Builder args = Args.builder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = ParameterSpec.normalize(entry.getKey());
            if (key.equals("type") || key.equals("of") || key.equals("source")) {
                continue;
            }
            args.put(entry.getKey(), convert(entry.getValue(), source));
        }

        Object rawSource = lookup(map, "of");
        if (rawSource == null) {
            rawSource = lookup(map, "source");
        }
        return new TargeterNode(String.valueOf(rawType), args.build(), parseTargeter(rawSource, source));
    }

    public static List<ConditionNode> parseConditions(Object raw, String source) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            throw new ParseException(source, "'conditions' must be a list, found "
                    + raw.getClass().getSimpleName());
        }
        List<ConditionNode> conditions = new ArrayList<>();
        for (Object entry : (List<?>) raw) {
            conditions.add(parseCondition(entry, source));
        }
        return conditions;
    }

    public static ConditionNode parseCondition(Object entry, String source) {
        if (entry instanceof String) {
            String text = ((String) entry).trim();
            return ShorthandParser.parseCondition(text.startsWith("?") ? text : "?" + text, source);
        }
        if (!(entry instanceof Map)) {
            throw new ParseException(source, "a condition must be a string or a map, found "
                    + (entry == null ? "nothing" : entry.getClass().getSimpleName()));
        }

        Map<String, Object> map = asMap(entry);
        Object rawType = lookup(map, "type");
        if (rawType == null) {
            throw new ParseException(source, "condition map has no 'type'");
        }
        String type = String.valueOf(rawType).trim();
        boolean negated = false;
        if (type.startsWith("!")) {
            negated = true;
            type = type.substring(1);
        }

        Args.Builder args = Args.builder();
        for (Map.Entry<String, Object> field : map.entrySet()) {
            String key = ParameterSpec.normalize(field.getKey());
            if (key.equals("type")) {
                continue;
            }
            if (key.equals("negate") || key.equals("not")) {
                negated = negated || Boolean.parseBoolean(String.valueOf(field.getValue()));
                continue;
            }
            args.put(field.getKey(), convert(field.getValue(), source));
        }
        return new ConditionNode(type, args.build(), negated);
    }

    public static TriggerNode parseTrigger(Object raw, String source) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            return ShorthandParser.parseTrigger(text.startsWith("~") ? text : "~" + text, source);
        }
        if (raw instanceof Map) {
            Map<String, Object> map = asMap(raw);
            Object type = lookup(map, "type");
            if (type == null) {
                throw new ParseException(source, "trigger map has no 'type'");
            }
            Object value = lookup(map, "value");
            String written = String.valueOf(type) + (value == null ? "" : ":" + value);
            return ShorthandParser.parseTrigger("~" + written, source);
        }
        throw new ParseException(source, "'trigger' must be a string or a map, found "
                + raw.getClass().getSimpleName());
    }

    /**
     * YAML scalars become strings.
     * <p>
     * Every parameter is an expression evaluated at execution time, and an
     * expression's input is text — so normalising here means a mechanic reading
     * {@code amount} gets the same thing whether the file said {@code 9},
     * {@code "9"} or {@code "<caster.level> * 3"}.
     */
    public static Object convert(Object value, String source) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            asMap(value).forEach((key, element) -> nested.put(key, convert(element, source)));
            return Args.of(nested);
        }
        if (value instanceof List) {
            List<Object> converted = new ArrayList<>();
            for (Object element : (List<?>) value) {
                converted.add(convert(element, source));
            }
            return converted;
        }
        return String.valueOf(value);
    }

    private static boolean isReserved(String key) {
        String normalized = ParameterSpec.normalize(key);
        for (String reserved : RESERVED) {
            if (reserved.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** Case- and underscore-insensitive key lookup, matching the parameter rules. */
    public static Object lookup(Map<String, Object> map, String key) {
        String normalized = ParameterSpec.normalize(key);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (ParameterSpec.normalize(entry.getKey()).equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        if (value instanceof Args) {
            return ((Args) value).asMap();
        }
        if (value instanceof org.bukkit.configuration.ConfigurationSection) {
            return deepValues((org.bukkit.configuration.ConfigurationSection) value);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, element) -> map.put(String.valueOf(key), element));
        return (Map<String, Object>) (Map<?, ?>) map;
    }

    /**
     * Reads a Bukkit configuration section into plain maps, all the way down.
     * <p>
     * {@code getValues(false)} hands back nested mappings as
     * {@code MemorySection}, not {@code Map} — so every {@code instanceof Map}
     * check downstream quietly answers false and a whole {@code ai:} or
     * {@code options:} block is skipped without an error. Normalising once at
     * the boundary is what keeps the parser's contract honest: plain maps in,
     * and therefore testable on the JVM with no server.
     */
    public static Map<String, Object> deepValues(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, deepConvert(section.get(key)));
        }
        return map;
    }

    private static Object deepConvert(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection) {
            return deepValues((org.bukkit.configuration.ConfigurationSection) value);
        }
        if (value instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, element) ->
                    map.put(String.valueOf(key), deepConvert(element)));
            return map;
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object element : (List<?>) value) {
                list.add(deepConvert(element));
            }
            return list;
        }
        return value;
    }
}
