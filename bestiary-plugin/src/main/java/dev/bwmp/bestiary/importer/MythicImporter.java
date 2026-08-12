package dev.bwmp.bestiary.importer;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.config.ParseException;
import dev.bwmp.bestiary.config.ShorthandParser;
import dev.bwmp.bestiary.config.SkillWriter;
import dev.bwmp.keystone.text.KeystoneText;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /bestiary import}: a one-shot converter, not runtime compatibility.
 * <p>
 * Bestiary never loads MythicMobs files at runtime. Runtime compatibility would
 * permanently couple the project to another plugin's undocumented syntax and
 * make every MythicMobs release something to track — which is one of the
 * reasons for not using MythicMobs in the first place.
 * <p>
 * Anything unmappable is written as a comment in place, with a summary naming
 * every dropped mechanic. A boss that converts to 90% of itself and never says
 * so is a very expensive debugging session.
 */
public final class MythicImporter {

    /** What one import run produced. */
    public static final class Result {
        private final int converted;
        private final int skipped;
        private final List<String> warnings;
        private final String output;

        Result(int converted, int skipped, List<String> warnings, String output) {
            this.converted = converted;
            this.skipped = skipped;
            this.warnings = List.copyOf(warnings);
            this.output = output;
        }

        public int converted() {
            return converted;
        }

        public int skipped() {
            return skipped;
        }

        public List<String> warnings() {
            return warnings;
        }

        public String output() {
            return output;
        }
    }

    private final BestiaryPlugin plugin;
    private final List<String> warnings = new ArrayList<>();
    private int converted;
    private int skipped;

    public MythicImporter(BestiaryPlugin plugin) {
        this.plugin = plugin;
    }

    public Result importFrom(File source) {
        File output = new File(plugin.getDataFolder(), "imported");
        //noinspection ResultOfMethodCallIgnored
        output.mkdirs();

        List<File> files = new ArrayList<>();
        collect(source, files);
        for (File file : files) {
            try {
                importFile(file, output);
            } catch (RuntimeException exception) {
                warnings.add(file.getName() + ": " + exception.getMessage());
                skipped++;
            }
        }
        return new Result(converted, skipped, warnings, output.getAbsolutePath());
    }

    private void collect(File source, List<File> into) {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    collect(child, into);
                }
            }
            return;
        }
        String name = source.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            into.add(source);
        }
    }

    private void importFile(File file, File output) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> skills = new LinkedHashMap<>();
        Map<String, Object> mobs = new LinkedHashMap<>();
        Map<String, Object> dropTables = new LinkedHashMap<>();

        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            if (section.contains("Type") || section.contains("type")) {
                mobs.put(id.toLowerCase(Locale.ROOT), convertMob(id, section));
            } else if (section.contains("Drops") || section.contains("drops")) {
                dropTables.put(id.toLowerCase(Locale.ROOT), convertDropTable(id, section));
            } else if (section.contains("Skills") || section.contains("skills")) {
                skills.put(id.toLowerCase(Locale.ROOT), convertSkill(id, section));
            } else {
                warnings.add(id + ": not a MythicMobs mob, skill or drop table; skipped");
                skipped++;
            }
        }

        write(new File(output, "skills/" + file.getName()), skills);
        write(new File(output, "mobs/mythic/" + file.getName()), mobs);
        write(new File(output, "droptables/" + file.getName()), dropTables);
    }

    // --- skills -----------------------------------------------------------

    private Map<String, Object> convertSkill(String id, ConfigurationSection section) {
        List<String> lines = stringList(section, "Skills");
        List<SkillNode> nodes = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        for (String line : lines) {
            SkillNode node = convertLine(id, line, unmapped);
            if (node != null) {
                nodes.add(node);
            }
        }

        long cooldown = Math.round(section.getDouble("Cooldown", 0.0d) * 20.0d);
        List<ConditionNode> conditions = new ArrayList<>();
        for (String written : stringList(section, "Conditions")) {
            ConditionNode condition = convertCondition(id, written, unmapped);
            if (condition != null) {
                conditions.add(condition);
            }
        }

        SkillDefinition definition = new SkillDefinition(id.toLowerCase(Locale.ROOT), cooldown,
                conditions, nodes, "imported", 0);
        Map<String, Object> map = SkillWriter.toMap(definition);
        if (!unmapped.isEmpty()) {
            // Written into the file rather than only into the log: the person
            // fixing it is looking at the file, not at a console scrollback.
            map.put("_unconverted", unmapped);
        }
        converted++;
        return map;
    }

    private SkillNode convertLine(String owner, String line, List<String> unmapped) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        SkillNode parsed;
        try {
            parsed = ShorthandParser.parseLine(stripTrailingBoolean(trimmed), "mythic:" + owner);
        } catch (ParseException exception) {
            unmapped.add(trimmed + "   # unparseable: " + exception.getMessage());
            skipped++;
            return null;
        }

        String mechanic = MythicNames.mechanic(parsed.type());
        if (mechanic == null) {
            unmapped.add(trimmed + "   # no equivalent mechanic for '" + parsed.type() + "'");
            skipped++;
            return null;
        }

        TargeterNode targeter = convertTargeter(parsed.targeter(), trimmed, unmapped);
        List<ConditionNode> conditions = new ArrayList<>();
        for (ConditionNode condition : parsed.conditions()) {
            String name = MythicNames.condition(condition.type());
            if (name == null) {
                unmapped.add(trimmed + "   # no equivalent condition for '?" + condition.type() + "'");
                continue;
            }
            conditions.add(new ConditionNode(name, condition.args(), condition.negated()));
        }

        return new SkillNode(mechanic, parsed.args(), targeter, conditions,
                parsed.trigger(), List.of(), "imported");
    }

    private TargeterNode convertTargeter(TargeterNode node, String line, List<String> unmapped) {
        if (node == null) {
            return null;
        }
        String name = MythicNames.targeter(node.type());
        if (name == null) {
            unmapped.add(line + "   # no equivalent targeter for '@" + node.type() + "'");
            return null;
        }
        return new TargeterNode(name, node.args(), convertTargeter(node.source(), line, unmapped));
    }

    private ConditionNode convertCondition(String owner, String written, List<String> unmapped) {
        String text = stripTrailingBoolean(written.trim());
        try {
            ConditionNode parsed = ShorthandParser.parseCondition(
                    text.startsWith("?") ? text : "?" + text, "mythic:" + owner);
            String name = MythicNames.condition(parsed.type());
            if (name == null) {
                unmapped.add(written + "   # no equivalent condition");
                return null;
            }
            return new ConditionNode(name, parsed.args(), parsed.negated());
        } catch (ParseException exception) {
            unmapped.add(written + "   # unparseable condition: " + exception.getMessage());
            return null;
        }
    }

    /** MythicMobs condition lines end in {@code true} / {@code false}; Bestiary negates with {@code !}. */
    private static String stripTrailingBoolean(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" false")) {
            return "!" + line.substring(0, line.length() - 6).trim();
        }
        if (lower.endsWith(" true")) {
            return line.substring(0, line.length() - 5).trim();
        }
        return line;
    }

    // --- mobs -------------------------------------------------------------

    private Map<String, Object> convertMob(String id, ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", section.getString("Type", section.getString("type", "ZOMBIE"))
                .toLowerCase(Locale.ROOT));

        String display = section.getString("Display", section.getString("display", ""));
        if (!display.isEmpty()) {
            map.put("display", KeystoneText.legacyToMiniMessage(display));
        }
        copyNumber(section, map, "Health", "health");
        copyNumber(section, map, "Damage", "damage");
        copyNumber(section, map, "Armor", "armor");

        ConfigurationSection options = section.getConfigurationSection("Options");
        if (options != null) {
            Map<String, Object> converted = new LinkedHashMap<>();
            copyNumber(options, map, "MovementSpeed", "movement_speed");
            copyNumber(options, map, "FollowRange", "follow_range");
            copyNumber(options, map, "KnockbackResistance", "knockback_resistance");
            if (options.contains("PreventOtherDrops")) {
                converted.put("prevent_other_drops", options.getBoolean("PreventOtherDrops"));
            }
            if (options.contains("Despawn")) {
                converted.put("despawn", options.getBoolean("Despawn"));
            }
            if (options.contains("Silent")) {
                converted.put("silent", options.getBoolean("Silent"));
            }
            if (options.contains("AlwaysShowName")) {
                converted.put("always_show_name", options.getBoolean("AlwaysShowName"));
            }
            if (!converted.isEmpty()) {
                map.put("options", converted);
            }
        }

        String faction = section.getString("Faction", "");
        if (!faction.isEmpty()) {
            map.put("faction", faction);
        }

        List<String> unmapped = new ArrayList<>();
        List<Object> skills = new ArrayList<>();
        for (String line : stringList(section, "Skills")) {
            SkillNode node = convertLine(id, line, unmapped);
            if (node != null) {
                skills.add(SkillWriter.node(node));
            }
        }
        if (!skills.isEmpty()) {
            map.put("skills", skills);
        }

        List<String> drops = stringList(section, "Drops");
        if (!drops.isEmpty()) {
            map.put("drops", id.toLowerCase(Locale.ROOT) + "_drops");
            unmapped.add("# inline Drops were converted into the drop table '"
                    + id.toLowerCase(Locale.ROOT) + "_drops'");
        }
        if (!unmapped.isEmpty()) {
            map.put("_unconverted", unmapped);
        }
        converted++;
        return map;
    }

    // --- drop tables ------------------------------------------------------

    private Map<String, Object> convertDropTable(String id, ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> drops = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        for (String line : stringList(section, "Drops")) {
            // `material amount chance`, the MythicMobs drop line.
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", parts[0].toLowerCase(Locale.ROOT));
            if (parts.length >= 2) {
                entry.put("amount", parts[1]);
            }
            if (parts.length >= 3) {
                try {
                    entry.put("chance", Double.parseDouble(parts[2]));
                } catch (NumberFormatException ignored) {
                    unmapped.add(line + "   # chance is not a number");
                }
            }
            drops.add(entry);
        }
        map.put("drops", drops);
        if (!unmapped.isEmpty()) {
            map.put("_unconverted", unmapped);
        }
        converted++;
        return map;
    }

    // --- plumbing ---------------------------------------------------------

    private static void copyNumber(ConfigurationSection from, Map<String, Object> into,
                                   String mythicKey, String bestiaryKey) {
        if (from.contains(mythicKey)) {
            into.put(bestiaryKey, from.getDouble(mythicKey));
        }
    }

    private static List<String> stringList(ConfigurationSection section, String key) {
        List<String> lines = section.getStringList(key);
        if (!lines.isEmpty()) {
            return lines;
        }
        return section.getStringList(key.toLowerCase(Locale.ROOT));
    }

    private void write(File file, Map<String, Object> content) {
        if (content.isEmpty()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        content.forEach((id, value) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            yaml.createSection(id, map);
        });
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            warnings.add("could not write " + file.getName() + ": " + exception.getMessage());
        }
    }

    static Args unusedArgs() {
        return Args.EMPTY;
    }
}
