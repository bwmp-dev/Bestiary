package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.api.config.TriggerNode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialises a {@link SkillDefinition} back to canonical structured YAML.
 * <p>
 * The GUI edits the {@code SkillNode} graph and saves through here, which is
 * why structured YAML is the canonical form: a GUI over an inline DSL would
 * have to round-trip strings. Editing does not preserve hand-written shorthand
 * — a skill edited in the GUI comes back structured, and the docs say so.
 */
public final class SkillWriter {

    private SkillWriter() {
    }

    public static Map<String, Object> toMap(SkillDefinition definition) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (definition.cooldownTicks() > 0) {
            map.put("cooldown", Durations.render(definition.cooldownTicks()));
        }
        if (!definition.conditions().isEmpty()) {
            map.put("conditions", conditions(definition.conditions()));
        }
        List<Object> lines = new ArrayList<>(definition.lines().size());
        for (SkillNode node : definition.lines()) {
            lines.add(node(node));
        }
        map.put("skills", lines);
        return map;
    }

    public static Map<String, Object> node(SkillNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", node.type());
        args(map, node.args());
        if (node.targeter() != null) {
            map.put("targeter", targeter(node.targeter()));
        }
        if (node.trigger() != null) {
            map.put("trigger", trigger(node.trigger()));
        }
        if (!node.conditions().isEmpty()) {
            map.put("conditions", conditions(node.conditions()));
        }
        if (!node.children().isEmpty()) {
            List<Object> children = new ArrayList<>(node.children().size());
            for (SkillNode child : node.children()) {
                children.add(node(child));
            }
            map.put("skills", children);
        }
        return map;
    }

    private static Object targeter(TargeterNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", node.type());
        args(map, node.args());
        if (node.source() != null) {
            map.put("of", targeter(node.source()));
        }
        return map;
    }

    private static Object trigger(TriggerNode node) {
        return node.parameter().isEmpty()
                ? node.kind().written()
                : node.kind().written() + ":" + node.parameter();
    }

    private static List<Object> conditions(List<ConditionNode> nodes) {
        List<Object> list = new ArrayList<>(nodes.size());
        for (ConditionNode node : nodes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", (node.negated() ? "!" : "") + node.type());
            args(map, node.args());
            list.add(map);
        }
        return list;
    }

    private static void args(Map<String, Object> into, Args args) {
        for (Map.Entry<String, Object> entry : args.asMap().entrySet()) {
            into.put(entry.getKey(), value(entry.getValue()));
        }
    }

    private static Object value(Object raw) {
        if (raw instanceof Args) {
            Map<String, Object> nested = new LinkedHashMap<>();
            args(nested, (Args) raw);
            return nested;
        }
        if (raw instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object element : (List<?>) raw) {
                list.add(value(element));
            }
            return list;
        }
        return raw;
    }

    /**
     * Rewrites one skill inside its own file, leaving the others alone.
     * <p>
     * Comments and formatting elsewhere in the file are still lost — Bukkit's
     * YAML writer does not preserve them — which is exactly why the docs say
     * the GUI is for structure and the file is for hand-editing.
     */
    public static void save(File file, String skillId, SkillDefinition definition) throws IOException {
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set(skillId, null);
        yaml.createSection(skillId, toMap(definition));
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        yaml.save(file);
    }
}
