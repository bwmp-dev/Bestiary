package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.drop.DropEntry;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.expression.ExpressionEngine;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.SkillCompiler;
import dev.bwmp.bestiary.util.Ranges;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DropTableParser {

    private DropTableParser() {
    }

    public static DropTable parse(String id, Map<String, Object> section, String source,
                                  SkillCompiler compiler, ExpressionEngine engine) {
        String location = source + " -> " + id;

        List<ConditionNode> nodes = new ArrayList<>();
        double minimumShare = 0.0d;

        Object rawConditions = SkillParser.lookup(section, "conditions");
        if (rawConditions instanceof List) {
            for (Object entry : (List<?>) rawConditions) {
                // damage_share is not a Condition: it needs the mob's damage
                // ledger, which no condition can see. It is lifted out of the
                // list rather than being faked as one.
                if (entry instanceof Map) {
                    Map<String, Object> map = SkillParser.asMap(entry);
                    Object type = SkillParser.lookup(map, "type");
                    if (type != null && type.toString().replace("_", "")
                            .equalsIgnoreCase("damageshare")) {
                        Object min = SkillParser.lookup(map, "min");
                        if (min == null) {
                            min = SkillParser.lookup(map, "amount");
                        }
                        minimumShare = min == null ? 0.0d : parseDouble(min, location, "damage_share min");
                        continue;
                    }
                }
                nodes.add(SkillParser.parseCondition(entry, location));
            }
        } else if (rawConditions != null) {
            throw new ParseException(location, "'conditions' must be a list");
        }

        List<CompiledCondition> conditions = compiler.compileConditions(nodes, location, TargetKind.ANY);

        Object rawDrops = SkillParser.lookup(section, "drops");
        if (!(rawDrops instanceof List)) {
            throw new ParseException(location, "drop table has no 'drops' list");
        }

        List<DropEntry> entries = new ArrayList<>();
        List<?> rows = (List<?>) rawDrops;
        for (int index = 0; index < rows.size(); index++) {
            entries.add(entry(rows.get(index), location + "[" + index + "]", compiler, engine));
        }

        return new DropTable(id.toLowerCase(Locale.ROOT),
                DropTable.Mode.parse(MobParser.string(section, "mode", "all"), DropTable.Mode.ALL),
                (int) MobParser.number(section, "n", 1, location),
                DropTable.Distribution.parse(MobParser.string(section, "distribution", "per_killer"),
                        DropTable.Distribution.PER_KILLER),
                conditions, minimumShare, entries, source);
    }

    private static DropEntry entry(Object raw, String location, SkillCompiler compiler,
                                   ExpressionEngine engine) {
        if (!(raw instanceof Map)) {
            throw new ParseException(location, "a drop must be a map");
        }
        Map<String, Object> map = SkillParser.asMap(raw);

        DropEntry.Kind kind;
        String id;
        if (SkillParser.lookup(map, "item") != null) {
            kind = DropEntry.Kind.ITEM;
            id = String.valueOf(SkillParser.lookup(map, "item"));
        } else if (SkillParser.lookup(map, "table") != null) {
            kind = DropEntry.Kind.TABLE;
            id = String.valueOf(SkillParser.lookup(map, "table"));
        } else if (SkillParser.lookup(map, "exp") != null) {
            kind = DropEntry.Kind.EXP;
            id = "";
        } else if (SkillParser.lookup(map, "currency") != null) {
            kind = DropEntry.Kind.CURRENCY;
            id = "";
        } else if (SkillParser.lookup(map, "command") != null) {
            kind = DropEntry.Kind.COMMAND;
            id = String.valueOf(SkillParser.lookup(map, "command"));
        } else if (SkillParser.lookup(map, "quest") != null) {
            kind = DropEntry.Kind.QUEST;
            id = String.valueOf(SkillParser.lookup(map, "quest"));
        } else {
            throw new ParseException(location,
                    "a drop needs one of: item, table, exp, currency, command, quest");
        }

        // exp and currency carry their amount in the key that named them, so
        // `{exp: "200-350"}` is one row rather than two.
        Object rawAmount = SkillParser.lookup(map, "amount");
        if (rawAmount == null && kind == DropEntry.Kind.EXP) {
            rawAmount = SkillParser.lookup(map, "exp");
        }
        if (rawAmount == null && kind == DropEntry.Kind.CURRENCY) {
            rawAmount = SkillParser.lookup(map, "currency");
        }

        var amount = Ranges.compile(engine, rawAmount == null ? "1" : String.valueOf(rawAmount),
                location, 1.0d);

        double chance = 1.0d;
        Object rawChance = SkillParser.lookup(map, "chance");
        if (rawChance != null) {
            chance = parseDouble(rawChance, location, "chance");
            // Both `0.08` and `8%` are natural to write, and getting it wrong
            // by a factor of a hundred is an expensive mistake in a drop table.
            if (chance > 1.0d) {
                chance = chance / 100.0d;
            }
        }

        double weight = 1.0d;
        Object rawWeight = SkillParser.lookup(map, "weight");
        if (rawWeight != null) {
            weight = parseDouble(rawWeight, location, "weight");
        }

        List<ConditionNode> nodes = SkillParser.parseConditions(
                SkillParser.lookup(map, "conditions"), location);
        List<CompiledCondition> conditions =
                compiler.compileConditions(nodes, location, TargetKind.ANY);

        return new DropEntry(kind, id, amount, chance, weight, conditions);
    }

    private static double parseDouble(Object value, String location, String what) {
        String text = String.valueOf(value).trim();
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
            try {
                return Double.parseDouble(text) / 100.0d;
            } catch (NumberFormatException exception) {
                throw new ParseException(location, what + " '" + value + "' is not a number");
            }
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new ParseException(location, what + " '" + value + "' is not a number");
        }
    }
}
