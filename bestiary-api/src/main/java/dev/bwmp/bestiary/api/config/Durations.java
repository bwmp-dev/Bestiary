package dev.bwmp.bestiary.api.config;

/**
 * Duration parsing, defined once because it appears in three places that must
 * agree: cooldowns, {@code ~onTimer}, and every mechanic taking a duration.
 * <p>
 * A bare integer is ticks. The suffixes {@code t}, {@code s}, {@code m} and
 * {@code h} are ticks, seconds, minutes and hours, so {@code 8s} is 160 ticks
 * everywhere.
 */
public final class Durations {

    private Durations() {
    }

    public static long parseTicks(String value) {
        if (value == null) {
            throw new IllegalArgumentException("duration is missing");
        }
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("duration is empty");
        }

        long multiplier = 1L;
        char suffix = text.charAt(text.length() - 1);
        if (!Character.isDigit(suffix)) {
            switch (suffix) {
                case 't':
                    multiplier = 1L;
                    break;
                case 's':
                    multiplier = 20L;
                    break;
                case 'm':
                    multiplier = 20L * 60;
                    break;
                case 'h':
                    multiplier = 20L * 60 * 60;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "unknown duration suffix '" + suffix + "' in '" + value + "' (expected t, s, m or h)");
            }
            text = text.substring(0, text.length() - 1).trim();
        }

        try {
            // Parsed as a double so "1.5s" works; ticks are integral, so the
            // result is rounded rather than truncated.
            return Math.round(Double.parseDouble(text) * multiplier);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + value + "' is not a duration");
        }
    }

    public static long parseTicks(String value, long fallback) {
        try {
            return parseTicks(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** Renders ticks back to the shortest exact form, for the GUI and info. */
    public static String render(long ticks) {
        if (ticks % (20L * 60 * 60) == 0 && ticks != 0) {
            return (ticks / (20L * 60 * 60)) + "h";
        }
        if (ticks % (20L * 60) == 0 && ticks != 0) {
            return (ticks / (20L * 60)) + "m";
        }
        if (ticks % 20L == 0 && ticks != 0) {
            return (ticks / 20L) + "s";
        }
        return Long.toString(ticks);
    }

    /** A human-readable countdown, for placeholders and messages. */
    public static String humanize(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h ");
        }
        if (hours > 0 || minutes > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(remainder).append('s');
        return builder.toString();
    }
}
