package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.Condition;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.Mechanic;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.Targeter;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.bestiary.config.ArgsConfig;
import dev.bwmp.bestiary.config.ParseException;
import dev.bwmp.bestiary.expression.ExpressionEngine;
import dev.bwmp.bestiary.registry.BestiaryRegistries;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Binds a parsed {@link SkillNode} tree to registered mechanic, targeter and
 * condition types.
 * <p>
 * This is where a misspelled mechanic becomes a load-time error naming the file
 * and the YAML path rather than a mob that silently does nothing, and where an
 * inline {@code skills:} block is lifted out into a real skill under a synthetic
 * id so every skill call goes through one executor path.
 */
public final class SkillCompiler {

    private final BestiaryRegistries registries;
    private final ExpressionEngine engine;
    private final Function<LivingEntity, BestiaryMob> mobLookup;
    private final int maxTargets;

    public SkillCompiler(BestiaryRegistries registries, ExpressionEngine engine,
                         Function<LivingEntity, BestiaryMob> mobLookup, int maxTargets) {
        this.registries = registries;
        this.engine = engine;
        this.mobLookup = mobLookup;
        this.maxTargets = maxTargets;
    }

    public CompiledSkill compile(SkillDefinition definition, Map<String, CompiledSkill> sink) {
        List<CompiledCondition> conditions = compileConditions(definition.conditions(),
                definition.source() + " -> " + definition.id(), TargetKind.ANY);

        List<CompiledLine> lines = new ArrayList<>(definition.lines().size());
        for (int index = 0; index < definition.lines().size(); index++) {
            lines.add(compileLine(definition.lines().get(index), definition.id(), index, sink));
        }

        CompiledSkill skill = new CompiledSkill(definition.id().toLowerCase(Locale.ROOT),
                definition.cooldownTicks(), conditions, lines, definition.source(), false);
        sink.put(skill.id(), skill);
        return skill;
    }

    /** Compiles a bare list of lines — a mob's {@code skills:} block. */
    public CompiledSkill compileInline(String id, List<SkillNode> nodes, String source,
                                       Map<String, CompiledSkill> sink) {
        List<CompiledLine> lines = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            lines.add(compileLine(nodes.get(index), id, index, sink));
        }
        CompiledSkill skill = new CompiledSkill(id.toLowerCase(Locale.ROOT), 0L, List.of(), lines, source, true);
        sink.put(skill.id(), skill);
        return skill;
    }

    public CompiledLine compileLine(SkillNode node, String ownerId, int index, Map<String, CompiledSkill> sink) {
        String path = node.source().isEmpty() ? ownerId + "[" + index + "]" : node.source();

        MechanicType type = registries.mechanicIndex().find(node.type()).orElseThrow(() ->
                new ParseException(path, "unknown mechanic '" + node.type() + "'"));

        Args args = node.args().canonicalized(type.meta()::canonical);

        // An inline block becomes a real skill under a synthetic id, and the
        // parent gets `skill=<id>` so flow mechanics need no special config
        // channel to reach their children.
        if (!node.children().isEmpty()) {
            String childId = (ownerId + "#" + index).toLowerCase(Locale.ROOT);
            compileInline(childId, node.children(), path, sink);
            if (!args.contains("skill")) {
                args = args.with("skill", childId);
            }
        }

        ArgsConfig config = new ArgsConfig(args, engine, path);
        Mechanic mechanic;
        try {
            mechanic = type.create(config);
        } catch (ParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ParseException(path, "mechanic '" + node.type() + "': " + exception.getMessage(), exception);
        }

        CompiledTargeter targeter = node.targeter() == null ? null : compileTargeter(node.targeter(), path);
        List<CompiledCondition> conditions = compileConditions(node.conditions(), path, TargetKind.ANY);

        return new CompiledLine(node.type(), mechanic, targeter, conditions, node.trigger(), path);
    }

    public CompiledTargeter compileTargeter(TargeterNode node, String path) {
        TargeterType type = registries.targeterIndex().find(node.type()).orElseThrow(() ->
                new ParseException(path, "unknown targeter '@" + node.type() + "'"));

        Args args = node.args().canonicalized(type.meta()::canonical);
        ArgsConfig config = new ArgsConfig(args, engine, path + " @" + node.type());

        Targeter targeter;
        try {
            targeter = type.create(config);
        } catch (ParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ParseException(path, "targeter '@" + node.type() + "': " + exception.getMessage(), exception);
        }

        int limit = config.integer("limit", 0);
        SortMode sort = SortMode.parse(config.raw("sort", ""), SortMode.NONE);
        List<CompiledCondition> filter = compileFilter(config, path + " @" + node.type(), type.meta().produces());

        CompiledTargeter source = node.source() == null ? null : compileTargeter(node.source(), path);
        if (source != null && !type.meta().acceptsSource()) {
            throw new ParseException(path, "targeter '@" + node.type()
                    + "' does not compose over another targeter; drop the 'of' clause");
        }
        return new CompiledTargeter(node.type(), targeter, source, limit, sort, filter, maxTargets, mobLookup);
    }

    private List<CompiledCondition> compileFilter(ArgsConfig config, String path, TargetKind produces) {
        List<Object> raw = config.rawList("filter");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<ConditionNode> nodes = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            nodes.add(dev.bwmp.bestiary.config.SkillParser.parseCondition(entry, path));
        }
        return compileConditions(nodes, path, produces);
    }

    public List<CompiledCondition> compileConditions(List<ConditionNode> nodes, String path, TargetKind slot) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        List<CompiledCondition> compiled = new ArrayList<>(nodes.size());
        for (ConditionNode node : nodes) {
            compiled.add(compileCondition(node, path, slot));
        }
        return compiled;
    }

    public CompiledCondition compileCondition(ConditionNode node, String path, TargetKind slot) {
        ConditionType type = registries.conditionIndex().find(node.type()).orElseThrow(() ->
                new ParseException(path, "unknown condition '?" + node.type() + "'"));

        // A location targeter's filter can never satisfy an entity condition,
        // and finding that out at runtime means a boss that quietly targets
        // nothing. So it is a load-time error.
        if (slot == TargetKind.LOCATION && type.meta().evaluates() == TargetKind.ENTITY) {
            throw new ParseException(path, "condition '?" + node.type()
                    + "' needs an entity, but this slot only ever holds a location");
        }

        Args args = node.args().canonicalized(type.meta()::canonical);
        ArgsConfig config = new ArgsConfig(args, engine, path + " ?" + node.type());

        Condition condition;
        try {
            condition = type.create(config);
        } catch (ParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ParseException(path, "condition '?" + node.type() + "': " + exception.getMessage(), exception);
        }
        return new CompiledCondition(node.type(), condition, node.negated());
    }

    public ExpressionEngine engine() {
        return engine;
    }

    public BestiaryRegistries registries() {
        return registries;
    }
}
