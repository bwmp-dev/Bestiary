package dev.bwmp.bestiary.expression;

/**
 * The infix evaluator: {@code + - * / %}, unary minus, parentheses, doubles
 * throughout, whitespace insignificant.
 * <p>
 * Deliberately small and deliberately not configurable. One pinned behaviour
 * means exactly one thing to implement and one thing to document; adding
 * operators later is a breaking change to every config that
 * used the character for something else.
 */
public final class Arithmetic {

    private final String source;
    private int position;

    private Arithmetic(String source) {
        this.source = source;
    }

    /**
     * @throws NumberFormatException when the text is not an expression; callers
     *                               turn that into the documented fallback
     */
    public static double evaluate(String text) {
        if (text == null) {
            throw new NumberFormatException("empty expression");
        }
        Arithmetic parser = new Arithmetic(text);
        double value = parser.expression();
        parser.skipSpace();
        if (parser.position < parser.source.length()) {
            throw new NumberFormatException("unexpected '" + parser.source.charAt(parser.position)
                    + "' at " + parser.position + " in '" + text + "'");
        }
        return value;
    }

    /** True when the text parses, without the caller having to catch. */
    public static boolean parses(String text) {
        try {
            evaluate(text);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private double expression() {
        double value = term();
        while (true) {
            skipSpace();
            if (consume('+')) {
                value += term();
            } else if (consume('-')) {
                value -= term();
            } else {
                return value;
            }
        }
    }

    private double term() {
        double value = unary();
        while (true) {
            skipSpace();
            if (consume('*')) {
                value *= unary();
            } else if (consume('/')) {
                double divisor = unary();
                // Division by zero yields Infinity in Java, which then
                // propagates through a damage roll as something the server will
                // happily apply. Zero is the honest answer for a config typo.
                value = divisor == 0.0d ? 0.0d : value / divisor;
            } else if (consume('%')) {
                double divisor = unary();
                value = divisor == 0.0d ? 0.0d : value % divisor;
            } else {
                return value;
            }
        }
    }

    private double unary() {
        skipSpace();
        if (consume('-')) {
            return -unary();
        }
        if (consume('+')) {
            return unary();
        }
        return atom();
    }

    private double atom() {
        skipSpace();
        if (position >= source.length()) {
            throw new NumberFormatException("expression ends early: '" + source + "'");
        }
        if (consume('(')) {
            double value = expression();
            skipSpace();
            if (!consume(')')) {
                throw new NumberFormatException("missing ')' in '" + source + "'");
            }
            return value;
        }

        int start = position;
        while (position < source.length()) {
            char character = source.charAt(position);
            if (Character.isDigit(character) || character == '.') {
                position++;
            } else if ((character == 'e' || character == 'E') && position > start
                    && position + 1 < source.length()
                    && (Character.isDigit(source.charAt(position + 1))
                        || source.charAt(position + 1) == '-'
                        || source.charAt(position + 1) == '+')) {
                position += 2;
            } else {
                break;
            }
        }
        if (start == position) {
            throw new NumberFormatException("expected a number at " + position + " in '" + source + "'");
        }
        return Double.parseDouble(source.substring(start, position));
    }

    private boolean consume(char expected) {
        skipSpace();
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipSpace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }
}
