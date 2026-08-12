package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TriggerNode;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.mob.PhaseDefinition;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.mob.CompiledMob;
import dev.bwmp.bestiary.registry.ContentSnapshot;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.CompiledLine;
import dev.bwmp.bestiary.skill.CompiledSkill;
import dev.bwmp.bestiary.skill.SkillCompiler;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.keystone.config.LoadReport;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code skills/}, {@code mobs/}, {@code droptables/} and
 * {@code spawners/} into one immutable snapshot.
 * <p>
 * Files nest arbitrarily and merge into one namespace per directory, except for
 * mobs, where the first directory under {@code mobs/} is the id's namespace —
 * so {@code mobs/aether/valkyrie_champion.yml} defines
 * {@code aether:valkyrie_champion}.
 * <p>
 * Parse failures are per definition, not per file: one broken skill does not
 * take out the other forty in its file. Each failure names the file, the YAML
 * path, the offending value and what was expected, accumulated into the load
 * report.
 */
public final class ContentLoader {

    private final Engine engine;

    public ContentLoader(Engine engine) {
        this.engine = engine;
    }

    public ContentSnapshot load(SkillCompiler compiler, BestiarySettings settings, LoadReport report) {
        File root = engine.plugin().getDataFolder();

        Map<String, CompiledSkill> skills = new LinkedHashMap<>();
        Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
        loadSkills(new File(root, "skills"), compiler, skills, definitions, report);

        Map<NamespacedKey, CompiledMob> mobs = new LinkedHashMap<>();
        loadMobs(new File(root, "mobs"), compiler, skills, mobs, report);

        Map<String, DropTable> dropTables = new LinkedHashMap<>();
        loadDropTables(new File(root, "droptables"), compiler, dropTables, report);

        SpawnParser.Result spawns = new SpawnParser.Result();
        loadSpawns(new File(root, "spawners"), compiler, spawns, report);

        return new ContentSnapshot(skills, definitions, mobs, dropTables, spawns.spawners,
                spawns.randomSpawns, spawns.regions, settings);
    }

    // --- skills -----------------------------------------------------------

    private void loadSkills(File directory, SkillCompiler compiler,
                            Map<String, CompiledSkill> into,
                            Map<String, SkillDefinition> definitions, LoadReport report) {
        for (File file : yamlFiles(directory)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String source = relative(file);
            for (String id : yaml.getKeys(false)) {
                Map<String, Object> section = sectionOf(yaml, id);
                if (section == null) {
                    report.error(source + " -> " + id, "skill entry is not a map");
                    continue;
                }
                try {
                    SkillDefinition definition = SkillParser.parseSkill(
                            id.toLowerCase(Locale.ROOT), section, source, revisionOf(section));
                    compiler.compile(definition, into);
                    definitions.put(definition.id(), definition);
                    report.countLoaded();
                } catch (ParseException exception) {
                    report.error(exception.location().isEmpty() ? source : exception.location(),
                            messageOf(exception));
                } catch (RuntimeException exception) {
                    report.error(source + " -> " + id, String.valueOf(exception));
                }
            }
        }
    }

    // --- mobs -------------------------------------------------------------

    private void loadMobs(File directory, SkillCompiler compiler, Map<String, CompiledSkill> skills,
                          Map<NamespacedKey, CompiledMob> into, LoadReport report) {
        for (File file : yamlFiles(directory)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String source = relative(file);
            String namespace = namespaceOf(directory, file);

            for (String key : yaml.getKeys(false)) {
                Map<String, Object> section = sectionOf(yaml, key);
                if (section == null) {
                    report.error(source + " -> " + key, "mob entry is not a map");
                    continue;
                }
                NamespacedKey id;
                try {
                    id = new NamespacedKey(namespace, key.toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    report.error(source + " -> " + key, "'" + namespace + ":" + key + "' is not a valid id");
                    continue;
                }

                try {
                    MobDefinition definition = MobParser.parse(id, section, source, revisionOf(section));
                    into.put(id, compileMob(definition, compiler, skills));
                    report.countLoaded();
                } catch (ParseException exception) {
                    report.error(exception.location().isEmpty() ? source : exception.location(),
                            messageOf(exception));
                } catch (RuntimeException exception) {
                    report.error(source + " -> " + key, String.valueOf(exception));
                }
            }
        }
    }

    /**
     * Binds a mob's skill lines and phases, and groups the lines by trigger.
     * <p>
     * Lines sharing a trigger and parameter become one skill, so a mob with
     * three {@code ~onTimer:160} lines gets one task firing one skill rather
     * than three of each.
     */
    private CompiledMob compileMob(MobDefinition definition, SkillCompiler compiler,
                                   Map<String, CompiledSkill> skills) {
        Map<String, List<SkillNode>> grouped = new LinkedHashMap<>();
        for (SkillNode node : definition.skills()) {
            TriggerNode trigger = node.trigger();
            String key = trigger == null
                    ? TriggerKind.SPAWN.name() + ":"
                    : trigger.kind().name() + ":" + trigger.parameter();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node);
        }

        List<CompiledMob.Binding> bindings = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<SkillNode>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            TriggerKind kind = TriggerKind.valueOf(parts[0]);
            String parameter = parts.length > 1 ? parts[1] : "";

            String skillId = ("mob/" + definition.id() + "#" + kind.name().toLowerCase(Locale.ROOT)
                    + (parameter.isEmpty() ? "" : "-" + parameter) + "#" + index++)
                    .toLowerCase(Locale.ROOT);
            CompiledSkill skill = compiler.compileInline(skillId, entry.getValue(),
                    definition.source(), skills);

            long period = 0L;
            double threshold = 0.0d;
            if (kind == TriggerKind.TIMER) {
                period = new TriggerNode(kind, parameter).parameterAsTicks(20L);
            } else if (kind == TriggerKind.HEALTH_THRESHOLD) {
                threshold = new TriggerNode(kind, parameter).parameterAsNumber(50.0d);
            }
            bindings.add(CompiledMob.Binding.of(kind, parameter, skill, period, threshold));
        }

        List<CompiledMob.Phase> phases = new ArrayList<>();
        for (PhaseDefinition phase : definition.phases()) {
            List<ConditionNode> nodes = phase.until();
            List<CompiledCondition> until = compiler.compileConditions(nodes,
                    definition.source() + " -> phase " + phase.name(), TargetKind.ANY);
            phases.add(CompiledMob.Phase.of(phase, until));
        }

        double playerNearRange = definition.followRange() > 0 ? definition.followRange() : 24.0d;
        return new CompiledMob(definition, bindings, phases, playerNearRange,
                Text.render(definition.display()));
    }

    // --- drop tables ------------------------------------------------------

    private void loadDropTables(File directory, SkillCompiler compiler,
                                Map<String, DropTable> into, LoadReport report) {
        for (File file : yamlFiles(directory)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String source = relative(file);
            for (String id : yaml.getKeys(false)) {
                Map<String, Object> section = sectionOf(yaml, id);
                if (section == null) {
                    report.error(source + " -> " + id, "drop table entry is not a map");
                    continue;
                }
                try {
                    DropTable table = DropTableParser.parse(id, section, source, compiler,
                            engine.expressions());
                    validateItems(table, report);
                    into.put(table.id(), table);
                    report.countLoaded();
                } catch (ParseException exception) {
                    report.error(exception.location().isEmpty() ? source : exception.location(),
                            messageOf(exception));
                } catch (RuntimeException exception) {
                    report.error(source + " -> " + id, String.valueOf(exception));
                }
            }
        }
    }

    /**
     * A {@code sigil:} id that does not resolve is a load-time error against
     * the drop table, not a silent nothing at kill time.
     */
    private void validateItems(DropTable table, LoadReport report) {
        for (var entry : table.entries()) {
            if (entry.kind() != dev.bwmp.bestiary.drop.DropEntry.Kind.ITEM) {
                continue;
            }
            if (engine.hooks().sigil().resolveItem(entry.id(), 1) == null) {
                report.error("droptable:" + table.id(),
                        "item '" + entry.id() + "' does not resolve"
                                + (engine.hooks().sigil().present() ? "" : " (Sigil is not installed)"));
            }
        }
    }

    // --- spawns -----------------------------------------------------------

    private void loadSpawns(File directory, SkillCompiler compiler, SpawnParser.Result into,
                            LoadReport report) {
        for (File file : yamlFiles(directory)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String source = relative(file);
            for (String id : yaml.getKeys(false)) {
                Map<String, Object> section = sectionOf(yaml, id);
                if (section == null) {
                    report.error(source + " -> " + id, "spawn entry is not a map");
                    continue;
                }
                try {
                    SpawnParser.parse(id, section, source, compiler, into);
                    report.countLoaded();
                } catch (ParseException exception) {
                    report.error(exception.location().isEmpty() ? source : exception.location(),
                            messageOf(exception));
                } catch (RuntimeException exception) {
                    report.error(source + " -> " + id, String.valueOf(exception));
                }
            }
        }
    }

    // --- plumbing ---------------------------------------------------------

    private List<File> yamlFiles(File directory) {
        List<File> files = new ArrayList<>();
        collect(directory, files);
        files.sort(java.util.Comparator.comparing(File::getAbsolutePath));
        return files;
    }

    private void collect(File directory, List<File> into) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, into);
            } else {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    into.add(child);
                }
            }
        }
    }

    private String relative(File file) {
        String root = engine.plugin().getDataFolder().getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.startsWith(root) ? path.substring(root.length() + 1).replace('\\', '/') : path;
    }

    /** The first directory under {@code mobs/}, or {@code bestiary} at the top level. */
    private String namespaceOf(File root, File file) {
        File parent = file.getParentFile();
        if (parent == null || parent.equals(root)) {
            return "bestiary";
        }
        File cursor = parent;
        while (cursor.getParentFile() != null && !cursor.getParentFile().equals(root)) {
            cursor = cursor.getParentFile();
        }
        return cursor.getName().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> sectionOf(YamlConfiguration yaml, String key) {
        var section = yaml.getConfigurationSection(key);
        return section == null ? null : SkillParser.deepValues(section);
    }

    /**
     * A content hash rather than a counter.
     * <p>
     * The revision is compared against what a live mob wrote at spawn, so it
     * has to change when — and only when — the definition changes. A counter
     * bumped on every reload would re-bind every boss on the server for a
     * reload that changed one unrelated file.
     */
    private static int revisionOf(Map<String, Object> section) {
        return section.toString().hashCode();
    }

    private static String messageOf(ParseException exception) {
        String message = exception.getMessage();
        String location = exception.location();
        return location.isEmpty() || !message.startsWith(location + ": ")
                ? message
                : message.substring(location.length() + 2);
    }
}
