package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.skill.Constant;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.util.Throttle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Compiles parameter text into {@link Expression}s.
 * <p>
 * The evaluation order is pinned, and implemented literally:
 * every {@code <...>} placeholder is substituted to its string value first,
 * then — in numeric contexts only — the result is parsed as an infix
 * expression. Substituting first is what makes {@code <mob.var.formula>} able
 * to contain arithmetic; parsing first would not.
 * <p>
 * A bracketed token whose first path segment is not a registered namespace is
 * left exactly as written. That is the difference between an unresolvable
 * placeholder — {@code <caster.nonsense>}, which is a config bug and warns —
 * and a MiniMessage tag such as {@code <gradient:#e8d9a0:#c9a227>}, which is
 * not addressed to Bestiary at all and must survive untouched.
 */
public final class ExpressionEngine {

    private final List<PlaceholderResolver> resolvers = new ArrayList<>();
    private final Map<String, PlaceholderResolver> byNamespace = new HashMap<>();
    private final Throttle warnings = Throttle.perMinute();
    private volatile BiConsumer<String, String> warner = (source, message) -> {
    };

    public ExpressionEngine() {
    }

    public ExpressionEngine register(PlaceholderResolver resolver) {
        resolvers.add(resolver);
        for (String namespace : resolver.namespaces()) {
            byNamespace.put(namespace.toLowerCase(java.util.Locale.ROOT), resolver);
        }
        return this;
    }

    public void onWarning(BiConsumer<String, String> warner) {
        this.warner = warner == null ? (source, message) -> {
        } : warner;
    }

    public void resetWarnings() {
        warnings.reset();
    }

    public Set<String> namespaces() {
        return Set.copyOf(byNamespace.keySet());
    }

    public Expression compileNumber(String source, String location) {
        return compile(source, location, true);
    }

    /** A parameter used in a string context: substitution only, no arithmetic. */
    public Expression compileText(String source, String location) {
        return compile(source, location, false);
    }

    private Expression compile(String source, String location, boolean numeric) {
        String text = source == null ? "" : source;
        List<Segment> segments = split(text);

        boolean hasPlaceholder = false;
        for (Segment segment : segments) {
            if (segment.placeholder) {
                hasPlaceholder = true;
                break;
            }
        }

        if (!hasPlaceholder) {
            if (!numeric) {
                return Constant.of(text);
            }
            // Folded once at load. A literal 9 must not pay for an expression
            // parse on every one of a boss's ten thousand damage ticks.
            if (Arithmetic.parses(text)) {
                return new FoldedNumber(text, Arithmetic.evaluate(text));
            }
            return Constant.of(text);
        }
        return new Compiled(text, location, segments, numeric);
    }

    /**
     * Splits into literal runs and placeholder tokens.
     * <p>
     * A {@code <} that does not open a placeholder in a registered namespace is
     * literal, which is checked here rather than at evaluation so a MiniMessage
     * tag never even enters the resolution path.
     */
    private List<Segment> split(String text) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();

        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character != '<') {
                literal.append(character);
                index++;
                continue;
            }

            int close = text.indexOf('>', index + 1);
            int nextOpen = text.indexOf('<', index + 1);
            boolean wellFormed = close > index + 1 && (nextOpen < 0 || nextOpen > close);
            String inner = wellFormed ? text.substring(index + 1, close) : "";

            if (!wellFormed || !isOurs(inner)) {
                literal.append(character);
                index++;
                continue;
            }

            if (literal.length() > 0) {
                segments.add(Segment.literal(literal.toString()));
                literal.setLength(0);
            }
            segments.add(Segment.placeholder(inner));
            index = close + 1;
        }

        if (literal.length() > 0) {
            segments.add(Segment.literal(literal.toString()));
        }
        return segments;
    }

    private boolean isOurs(String inner) {
        if (inner.isEmpty() || inner.indexOf(' ') >= 0) {
            return false;
        }
        int dot = inner.indexOf('.');
        String namespace = (dot < 0 ? inner : inner.substring(0, dot)).toLowerCase(java.util.Locale.ROOT);
        return byNamespace.containsKey(namespace);
    }

    private String resolveOne(String key, SkillContext context, Target target, String location) {
        int dot = key.indexOf('.');
        String namespace = (dot < 0 ? key : key.substring(0, dot)).toLowerCase(java.util.Locale.ROOT);
        PlaceholderResolver resolver = byNamespace.get(namespace);
        String value = resolver == null ? null : resolver.resolve(key, context, target);
        if (value != null) {
            return value;
        }

        // Silently propagating NaN through a damage roll is the alternative,
        // and it is worse.
        String skill = context == null ? "?" : context.skillId();
        if (warnings.allow(skill + "|" + key)) {
            warner.accept(location, "unresolvable placeholder <" + key + "> in skill '" + skill + "'");
        }
        return null;
    }

    private static final class Segment {

        private final String text;
        private final boolean placeholder;

        private Segment(String text, boolean placeholder) {
            this.text = text;
            this.placeholder = placeholder;
        }

        private static Segment literal(String text) {
            return new Segment(text, false);
        }

        private static Segment placeholder(String key) {
            return new Segment(key, true);
        }
    }

    /** A literal number, parsed once at load. */
    private static final class FoldedNumber implements Expression {

        private final String source;
        private final double value;

        private FoldedNumber(String source, double value) {
            this.source = source;
            this.value = value;
        }

        @Override
        public double asDouble(SkillContext context, Target target) {
            return value;
        }

        @Override
        public String asString(SkillContext context, Target target) {
            return source;
        }

        @Override
        public boolean isConstant() {
            return true;
        }

        @Override
        public String source() {
            return source;
        }
    }

    private final class Compiled implements Expression {

        private final String source;
        private final String location;
        private final List<Segment> segments;
        private final boolean numeric;

        private Compiled(String source, String location, List<Segment> segments, boolean numeric) {
            this.source = source;
            this.location = location;
            this.segments = List.copyOf(segments);
            this.numeric = numeric;
        }

        @Override
        public double asDouble(SkillContext context, Target target) {
            String substituted = substitute(context, target, true);
            if (!numeric) {
                try {
                    return Double.parseDouble(substituted.trim());
                } catch (NumberFormatException ignored) {
                    return 0.0d;
                }
            }
            try {
                return Arithmetic.evaluate(substituted);
            } catch (NumberFormatException exception) {
                String skill = context == null ? "?" : context.skillId();
                if (warnings.allow(skill + "|expr|" + source)) {
                    warner.accept(location, "'" + source + "' resolved to '" + substituted
                            + "', which is not an expression; using 0");
                }
                return 0.0d;
            }
        }

        @Override
        public String asString(SkillContext context, Target target) {
            return substitute(context, target, false);
        }

        private String substitute(SkillContext context, Target target, boolean numericContext) {
            StringBuilder builder = new StringBuilder(source.length() + 16);
            for (Segment segment : segments) {
                if (!segment.placeholder) {
                    builder.append(segment.text);
                    continue;
                }
                String value = resolveOne(segment.text, context, target, location);
                if (value == null) {
                    builder.append(numericContext ? "0" : "");
                } else {
                    builder.append(value);
                }
            }
            return builder.toString();
        }

        @Override
        public boolean isConstant() {
            return false;
        }

        @Override
        public String source() {
            return source;
        }
    }
}
