package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEngineTest {

    /** A namespace whose values are fixed, so evaluation is the only variable. */
    private static final class StubResolver implements PlaceholderResolver {

        private final Map<String, String> values;

        private StubResolver(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Set<String> namespaces() {
            return Set.of("caster", "target");
        }

        @Override
        public String resolve(String key, SkillContext context, Target target) {
            return values.get(key);
        }
    }

    private ExpressionEngine engine;
    private AtomicInteger warnings;

    @BeforeEach
    void setUp() {
        warnings = new AtomicInteger();
        engine = new ExpressionEngine();
        engine.register(new StubResolver(Map.of(
                "caster.level", "4",
                "caster.name", "Champion",
                "target.hp.percent", "37.5")));
        engine.register(new MathPlaceholders());
        engine.onWarning((source, message) -> warnings.incrementAndGet());
    }

    @Test
    void aLiteralNumberIsFoldedAtLoad() {
        Expression expression = engine.compileNumber("9", "test");
        assertTrue(expression.isConstant());
        assertEquals(9.0d, expression.asDouble(null, null));
    }

    @Test
    void arithmeticIsEvaluatedAfterSubstitution() {
        Expression expression = engine.compileNumber("<caster.level> * 2.5 + 1", "test");
        assertFalse(expression.isConstant());
        assertEquals(11.0d, expression.asDouble(null, null), 1.0e-9d);
    }

    @Test
    void stringContextsGetSubstitutionOnly() {
        Expression expression = engine.compileText("<caster.name> is at <target.hp.percent>%", "test");
        assertEquals("Champion is at 37.5%", expression.asString(null, null));
    }

    @Test
    void miniMessageTagsAreLeftAlone() {
        // <gradient:...> belongs to nobody, so it is not an unresolvable
        // placeholder — it is not addressed to Bestiary at all.
        Expression expression = engine.compileText(
                "<gradient:#e8d9a0:#c9a227>Valkyrie</gradient> <caster.name>", "test");
        assertEquals("<gradient:#e8d9a0:#c9a227>Valkyrie</gradient> Champion",
                expression.asString(null, null));
        assertEquals(0, warnings.get());
    }

    @Test
    void anUnresolvablePlaceholderIsZeroAndWarnsOnce() {
        Expression expression = engine.compileNumber("<caster.nonsense> + 1", "test");
        assertEquals(1.0d, expression.asDouble(null, null), 1.0e-9d);
        assertEquals(1, warnings.get());

        // Throttled: a boss on a 20-tick timer must not write a gigabyte of
        // identical lines.
        expression.asDouble(null, null);
        assertEquals(1, warnings.get());
    }

    @Test
    void anUnresolvablePlaceholderIsEmptyInStringContext() {
        Expression expression = engine.compileText("[<caster.nonsense>]", "test");
        assertEquals("[]", expression.asString(null, null));
    }

    @Test
    void nonsenseThatIsNotAnExpressionYieldsZero() {
        Expression expression = engine.compileNumber("<caster.name> * 2", "test");
        assertEquals(0.0d, expression.asDouble(null, null));
    }

    @Test
    void randomRangesAreInclusiveAtBothEnds() {
        Expression expression = engine.compileNumber("<random.4to9>", "test");
        boolean sawLow = false;
        boolean sawHigh = false;
        for (int attempt = 0; attempt < 500; attempt++) {
            double value = expression.asDouble(null, null);
            assertTrue(value >= 4 && value <= 9, "out of range: " + value);
            sawLow |= value == 4;
            sawHigh |= value == 9;
        }
        assertTrue(sawLow, "never rolled the lower bound");
        assertTrue(sawHigh, "never rolled the upper bound");
    }

    @Test
    void arithmeticFollowsPrecedenceAndParentheses() {
        assertEquals(14.0d, Arithmetic.evaluate("2 + 3 * 4"), 1.0e-9d);
        assertEquals(20.0d, Arithmetic.evaluate("(2 + 3) * 4"), 1.0e-9d);
        assertEquals(-6.0d, Arithmetic.evaluate("-2 * 3"), 1.0e-9d);
        assertEquals(1.0d, Arithmetic.evaluate("10 % 3"), 1.0e-9d);
        assertEquals(2.5d, Arithmetic.evaluate(" 5 / 2 "), 1.0e-9d);
    }

    @Test
    void divisionByZeroIsZeroRatherThanInfinity() {
        // Infinity propagates into a damage roll and the server applies it.
        assertEquals(0.0d, Arithmetic.evaluate("5 / 0"));
        assertEquals(0.0d, Arithmetic.evaluate("5 % 0"));
    }

    @Test
    void trailingRubbishIsRejected() {
        assertThrows(NumberFormatException.class, () -> Arithmetic.evaluate("1 + 2 oops"));
        assertThrows(NumberFormatException.class, () -> Arithmetic.evaluate("(1 + 2"));
    }

    @Test
    void comparatorPrefixesParse() {
        Comparison comparison = Comparison.parse(engine, "<= 60", "test");
        assertEquals(Comparison.Operator.LE, comparison.operator());
        assertTrue(comparison.test(60.0d, null, null));
        assertTrue(comparison.test(10.0d, null, null));
        assertFalse(comparison.test(61.0d, null, null));
    }

    @Test
    void aBareNumberMeansEquals() {
        Comparison comparison = Comparison.parse(engine, "5", "test");
        assertEquals(Comparison.Operator.EQ, comparison.operator());
        assertTrue(comparison.test(5.0d, null, null));
        assertFalse(comparison.test(6.0d, null, null));
    }

    @Test
    void aLeadingPlaceholderIsNotALessThan() {
        Comparison comparison = Comparison.parse(engine, "<caster.level>", "test");
        assertEquals(Comparison.Operator.EQ, comparison.operator());
        assertTrue(comparison.test(4.0d, null, null));
    }

    @Test
    void durationsSuffixToTicks() {
        assertEquals(160L, Durations.parseTicks("8s"));
        assertEquals(20L, Durations.parseTicks("20"));
        assertEquals(20L, Durations.parseTicks("20t"));
        assertEquals(1200L, Durations.parseTicks("1m"));
        assertEquals(72000L, Durations.parseTicks("1h"));
        assertEquals(30L, Durations.parseTicks("1.5s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseTicks("8x"));
    }

    @Test
    void durationsRoundTrip() {
        assertEquals("8s", Durations.render(160L));
        assertEquals("1m", Durations.render(1200L));
        assertEquals("13", Durations.render(13L));
    }
}
