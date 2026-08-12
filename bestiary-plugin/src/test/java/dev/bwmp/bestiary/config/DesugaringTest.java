package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Both definition forms are meant to produce the identical object graph. If
 * that ever stops being true, the two front-ends stop sharing
 * one validator and one set of error messages, which is the entire reason for
 * carrying both.
 */
class DesugaringTest {

    @Test
    void bothFormsProduceTheSameTree() {
        SkillNode shorthand = SkillParser.parseNode(
                "damage{amount=9;ignoreArmor=true} @playersInRadius{r=7} ?onGround", "test");

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("type", "damage");
        structured.put("amount", 9);
        structured.put("ignore_armor", true);
        structured.put("targeter", Map.of("type", "players_in_radius", "r", 7));
        structured.put("conditions", List.of(Map.of("type", "on_ground")));

        SkillNode fromYaml = SkillParser.parseNode(structured, "test");

        assertEquals(shorthand.type(), fromYaml.type());
        assertEquals(shorthand.args().asMap(), fromYaml.args().asMap());
        assertEquals(shorthand.targeter().type(), fromYaml.targeter().type());
        assertEquals(shorthand.targeter().args().asMap(), fromYaml.targeter().args().asMap());
        assertEquals(shorthand.conditions().size(), fromYaml.conditions().size());
        assertEquals(shorthand.conditions().get(0).type(), fromYaml.conditions().get(0).type());
    }

    @Test
    void nestedBlocksMatchNestedMaps() {
        SkillNode shorthand = SkillParser.parseNode(
                "particle{shape={type=ring;radius=6;points=48};p=sweep_attack}", "test");

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("type", "particle");
        structured.put("shape", Map.of("type", "ring", "radius", 6, "points", 48));
        structured.put("p", "sweep_attack");

        SkillNode fromYaml = SkillParser.parseNode(structured, "test");
        assertEquals(shorthand.args().toShorthand(), fromYaml.args().toShorthand());
    }

    @Test
    void mixedFormsInOneSkillAreFine() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("cooldown", "8s");
        section.put("skills", List.of(
                "sound{s=entity.generic.explode} @self",
                Map.of("type", "damage", "amount", 5, "targeter", Map.of("type", "self"))));

        SkillDefinition definition = SkillParser.parseSkill("mixed", section, "test.yml", 1);
        assertEquals(160L, definition.cooldownTicks());
        assertEquals(2, definition.lines().size());
        assertEquals("sound", definition.lines().get(0).type());
        assertEquals("damage", definition.lines().get(1).type());
    }

    @Test
    void aSkillWithNoLinesIsAnError() {
        assertThrows(ParseException.class,
                () -> SkillParser.parseSkill("empty", Map.of("cooldown", "1s"), "test.yml", 1));
    }

    @Test
    void aMechanicWithNoTypeIsAnError() {
        assertThrows(ParseException.class,
                () -> SkillParser.parseNode(Map.of("amount", 9), "test"));
    }

    @Test
    void conditionMapsAcceptNegation() {
        var condition = SkillParser.parseCondition(Map.of("type", "on_ground", "negate", true), "test");
        assertEquals("onground", condition.type());
        assertEquals(true, condition.negated());
    }

    @Test
    void aTargeterMayBeWrittenAsShorthandInsideYaml() {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("type", "damage");
        structured.put("targeter", "@ring{radius=4} of @playersInRadius{r=10}");

        SkillNode node = SkillParser.parseNode(structured, "test");
        assertEquals("ring", node.targeter().type());
        assertEquals("playersinradius", node.targeter().source().type());
    }
}
