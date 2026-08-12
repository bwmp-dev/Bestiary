package dev.bwmp.bestiary.util;

import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.expression.ExpressionEngine;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code "4-9"} amount form.
 * <p>
 * Only used where it is unambiguous — drop amounts, experience, currency —
 * because {@code 4-9} is also a perfectly good subtraction, and
 * treating it as a range everywhere would silently change the meaning of every
 * expression containing a minus sign. Elsewhere, {@code <random.4to9>} is the
 * unambiguous spelling and the only one accepted.
 */
public final class Ranges {

    private static final Pattern RANGE =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*$");

    private Ranges() {
    }

    public static Expression compile(ExpressionEngine engine, String text, String location, double fallback) {
        if (text == null || text.isBlank()) {
            return dev.bwmp.bestiary.api.skill.Constant.of(fallback);
        }
        Matcher matcher = RANGE.matcher(text);
        if (!matcher.matches()) {
            return engine.compileNumber(text, location);
        }
        double low = Double.parseDouble(matcher.group(1));
        double high = Double.parseDouble(matcher.group(2));
        return new RangeExpression(text, Math.min(low, high), Math.max(low, high));
    }

    private static final class RangeExpression implements Expression {

        private final String source;
        private final double low;
        private final double high;

        private RangeExpression(String source, double low, double high) {
            this.source = source;
            this.low = low;
            this.high = high;
        }

        @Override
        public double asDouble(SkillContext context, Target target) {
            if (low == high) {
                return low;
            }
            // Inclusive at both ends, matching <random.AtoB>, so "4-9" can roll
            // a 9 rather than topping out one short of it.
            boolean integral = low == Math.rint(low) && high == Math.rint(high);
            if (integral) {
                return ThreadLocalRandom.current().nextLong((long) low, (long) high + 1);
            }
            return ThreadLocalRandom.current().nextDouble(low, high);
        }

        @Override
        public String asString(SkillContext context, Target target) {
            return source;
        }

        @Override
        public boolean isConstant() {
            return low == high;
        }

        @Override
        public String source() {
            return source;
        }
    }
}
