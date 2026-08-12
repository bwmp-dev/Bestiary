package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShorthandParserTest {

    @Test
    void parsesTheFourParts() {
        SkillNode node = ShorthandParser.parseLine(
                "damage{amount=9;ignoreArmor=true} @playersInRadius{r=7} ~onTimer:160 ?onGround", "test");

        assertEquals("damage", node.type());
        assertEquals("9", node.args().get("amount"));
        assertEquals("true", node.args().get("ignorearmor"));
        assertEquals("playersinradius", node.targeter().type());
        assertEquals("7", node.targeter().args().get("r"));
        assertEquals(TriggerKind.TIMER, node.trigger().kind());
        assertEquals(160L, node.trigger().parameterAsTicks(0));
        assertEquals(1, node.conditions().size());
        assertEquals("onground", node.conditions().get(0).type());
        assertFalse(node.conditions().get(0).negated());
    }

    @Test
    void keysAreNormalizedSoBothSpellingsAgree() {
        Args underscored = ShorthandParser.parseArgs("{ignore_armor=true}", "test");
        Args camelCased = ShorthandParser.parseArgs("{ignoreArmor=true}", "test");
        assertEquals(underscored.asMap(), camelCased.asMap());
    }

    @Test
    void nestedBracesBecomeNestedArgs() {
        SkillNode node = ShorthandParser.parseLine(
                "particle{shape={type=ring;radius=6;points=48};p=sweep_attack} @selfLocation", "test");

        Object shape = node.args().get("shape");
        assertTrue(shape instanceof Args);
        assertEquals("ring", ((Args) shape).get("type"));
        assertEquals("6", ((Args) shape).get("radius"));
        assertEquals("sweep_attack", node.args().get("p"));
    }

    @Test
    void whitespaceInsideBracesAndQuotesIsNotASeparator() {
        SkillNode node = ShorthandParser.parseLine(
                "message{msg=\"hello there, <target.name>\"} @self", "test");

        assertEquals("hello there, <target.name>", node.args().get("msg"));
        assertEquals("self", node.targeter().type());
    }

    @Test
    void escapedQuotesSurvive() {
        Args args = ShorthandParser.parseArgs("{msg=\"say \\\"hi\\\"\"}", "test");
        assertEquals("say \"hi\"", args.get("msg"));
    }

    @Test
    void ofChainsRightwards() {
        SkillNode node = ShorthandParser.parseLine(
                "particle @ring{radius=4} of @playersInRadius{r=10} of @self", "test");

        TargeterNode ring = node.targeter();
        assertEquals("ring", ring.type());
        assertEquals("playersinradius", ring.source().type());
        assertEquals("self", ring.source().source().type());
        assertNull(ring.source().source().source());
    }

    @Test
    void negatedConditionsAreMarked() {
        SkillNode node = ShorthandParser.parseLine("heal ?!onGround ?chance{c=0.5}", "test");
        assertTrue(node.conditions().get(0).negated());
        assertFalse(node.conditions().get(1).negated());
    }

    @Test
    void aBareFlagIsTrue() {
        Args args = ShorthandParser.parseArgs("{silent}", "test");
        assertEquals("true", args.get("silent"));
    }

    @Test
    void unbalancedBracesAreALoadTimeError() {
        assertThrows(ParseException.class,
                () -> ShorthandParser.parseLine("damage{amount=9 @self", "test"));
    }

    @Test
    void unterminatedQuotesAreALoadTimeError() {
        assertThrows(ParseException.class,
                () -> ShorthandParser.parseLine("message{msg=\"oops} @self", "test"));
    }

    @Test
    void twoTargetersWithoutOfIsAnError() {
        assertThrows(ParseException.class,
                () -> ShorthandParser.parseLine("damage @self @target", "test"));
    }

    @Test
    void anUnknownTriggerIsAnError() {
        assertThrows(ParseException.class,
                () -> ShorthandParser.parseLine("damage ~onNonsense", "test"));
    }

    @Test
    void tokenizerKeepsBracedGroupsTogether() {
        List<String> tokens = ShorthandParser.tokenize(
                "a{b=1; c=2} @d{e=\"f g\"} ?h", "test");
        assertEquals(List.of("a{b=1; c=2}", "@d{e=\"f g\"}", "?h"), tokens);
    }
}
