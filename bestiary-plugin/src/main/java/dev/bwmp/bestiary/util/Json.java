package dev.bwmp.bestiary.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flat JSON object writer and reader for variable maps.
 * <p>
 * Deliberately hand-rolled and deliberately flat. Variables are scalars —
 * strings, numbers, booleans — so a general JSON library would be a dependency
 * bought for one shape of data, and the shipped jar is already carrying
 * Keystone and Adventure. Nested structures are not supported because nothing
 * produces them: a variable that needs structure wants to be several variables.
 */
public final class Json {

    private Json() {
    }

    public static String write(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    /** Never throws: a corrupt blob costs the variables, not the mob. */
    public static Map<String, Object> read(String json) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (json == null) {
            return values;
        }
        String text = json.trim();
        if (text.length() < 2 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}') {
            return values;
        }
        text = text.substring(1, text.length() - 1);

        int index = 0;
        while (index < text.length()) {
            while (index < text.length() && (Character.isWhitespace(text.charAt(index)) || text.charAt(index) == ',')) {
                index++;
            }
            if (index >= text.length() || text.charAt(index) != '"') {
                break;
            }
            StringBuilder key = new StringBuilder();
            index = readString(text, index, key);
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length() || text.charAt(index) != ':') {
                break;
            }
            index++;
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length()) {
                break;
            }

            if (text.charAt(index) == '"') {
                StringBuilder value = new StringBuilder();
                index = readString(text, index, value);
                values.put(key.toString(), value.toString());
            } else {
                int start = index;
                while (index < text.length() && text.charAt(index) != ',') {
                    index++;
                }
                String raw = text.substring(start, index).trim();
                values.put(key.toString(), scalar(raw));
            }
        }
        return values;
    }

    private static Object scalar(String raw) {
        if (raw.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (raw.equalsIgnoreCase("null")) {
            return "";
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private static int readString(String text, int index, StringBuilder out) {
        index++;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length()) {
                char next = text.charAt(++index);
                switch (next) {
                    case 'n':
                        out.append('\n');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    default:
                        out.append(next);
                        break;
                }
                index++;
                continue;
            }
            if (character == '"') {
                return index + 1;
            }
            out.append(character);
            index++;
        }
        return index;
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(character);
                    break;
            }
        }
        return builder.toString();
    }
}
