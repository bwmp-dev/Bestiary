package dev.bwmp.bestiary.api.skill;

/** An {@link Expression} that ignores its input. */
public final class Constant implements Expression {

    private final double number;
    private final String text;

    private Constant(double number, String text) {
        this.number = number;
        this.text = text;
    }

    public static Expression of(double value) {
        return new Constant(value, trim(value));
    }

    public static Expression of(String value) {
        String text = value == null ? "" : value;
        double number;
        try {
            number = Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            number = 0.0d;
        }
        return new Constant(number, text);
    }

    @Override
    public double asDouble(SkillContext context, Target target) {
        return number;
    }

    @Override
    public String asString(SkillContext context, Target target) {
        return text;
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public String source() {
        return text;
    }

    /** {@code 9.0} reads badly in a message; {@code 9} is what was written. */
    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
