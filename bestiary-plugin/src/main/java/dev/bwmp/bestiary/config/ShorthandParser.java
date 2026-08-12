package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.ConditionNode;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.config.TargeterNode;
import dev.bwmp.bestiary.api.config.TriggerNode;
import dev.bwmp.bestiary.api.skill.TriggerKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one-line front-end.
 * <p>
 * <pre>
 * line      := mechanic targeter? trigger? condition*
 * mechanic  := name args?
 * targeter  := "@" name args? (" of " targeter)?
 * trigger   := "~on" name (":" value)?
 * condition := "?" "!"? name args?
 * args      := "{" pair (";" pair)* "}"
 * pair      := key "=" value
 * value     := bare-token | quoted-string | args
 * </pre>
 *
 * Desugaring is purely mechanical because an {@code args} block produces
 * exactly the map the equivalent YAML would: {@code particle{shape={type=ring;
 * radius=6}}} and the nested YAML mapping build the same {@link Args} graph,
 * and both then hit the same compiler. That is the whole reason for carrying
 * two front-ends over one AST.
 */
public final class ShorthandParser {

    private ShorthandParser() {
    }

    public static SkillNode parseLine(String line, String source) {
        List<String> tokens = tokenize(line, source);
        if (tokens.isEmpty()) {
            throw new ParseException(source, "empty skill line");
        }

        Split mechanic = splitNameAndArgs(tokens.get(0), source);
        if (mechanic.name.isEmpty()) {
            throw new ParseException(source, "skill line does not start with a mechanic name: '" + line + "'");
        }

        TargeterNode targeter = null;
        TriggerNode trigger = null;
        List<ConditionNode> conditions = new ArrayList<>();

        for (int index = 1; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equalsIgnoreCase("of")) {
                if (targeter == null) {
                    throw new ParseException(source, "'of' with no targeter before it in '" + line + "'");
                }
                if (index + 1 >= tokens.size()) {
                    throw new ParseException(source, "'of' with no targeter after it in '" + line + "'");
                }
                String next = tokens.get(++index);
                if (!next.startsWith("@")) {
                    throw new ParseException(source, "'of' must be followed by a targeter, found '" + next + "'");
                }
                targeter = appendSource(targeter, parseTargeter(next, source));
                continue;
            }

            char lead = token.charAt(0);
            switch (lead) {
                case '@':
                    if (targeter != null) {
                        throw new ParseException(source,
                                "two targeters on one line; use '@a of @b' to compose them: '" + line + "'");
                    }
                    targeter = parseTargeter(token, source);
                    break;
                case '~':
                    if (trigger != null) {
                        throw new ParseException(source, "two triggers on one line: '" + line + "'");
                    }
                    trigger = parseTrigger(token, source);
                    break;
                case '?':
                    conditions.add(parseCondition(token, source));
                    break;
                default:
                    throw new ParseException(source, "unexpected '" + token
                            + "' in '" + line + "' (expected @targeter, ~trigger or ?condition)");
            }
        }

        return new SkillNode(mechanic.name, mechanic.args, targeter, conditions, trigger, List.of(), source);
    }

    public static TargeterNode parseTargeter(String token, String source) {
        String body = token.startsWith("@") ? token.substring(1) : token;
        Split split = splitNameAndArgs(body, source);
        if (split.name.isEmpty()) {
            throw new ParseException(source, "targeter has no name: '" + token + "'");
        }
        return new TargeterNode(split.name, split.args, null);
    }

    public static ConditionNode parseCondition(String token, String source) {
        String body = token.startsWith("?") ? token.substring(1) : token;
        boolean negated = body.startsWith("!");
        if (negated) {
            body = body.substring(1);
        }
        Split split = splitNameAndArgs(body, source);
        if (split.name.isEmpty()) {
            throw new ParseException(source, "condition has no name: '" + token + "'");
        }
        return new ConditionNode(split.name, split.args, negated);
    }

    public static TriggerNode parseTrigger(String token, String source) {
        String body = token.startsWith("~") ? token.substring(1) : token;
        String parameter = "";
        int colon = body.indexOf(':');
        if (colon >= 0) {
            parameter = body.substring(colon + 1);
            body = body.substring(0, colon);
        }
        TriggerKind kind = TriggerKind.parse(body).orElseThrow(() ->
                new ParseException(source, "unknown trigger '" + token + "'"));
        return new TriggerNode(kind, parameter);
    }

    /**
     * Parses an {@code {a=1;b=2}} block. The braces are optional so the same
     * routine reads a bare {@code a=1;b=2}, which is what the MythicMobs
     * importer sees on some lines.
     */
    public static Args parseArgs(String text, String source) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            return Args.EMPTY;
        }
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return Args.EMPTY;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (String pair : splitTopLevel(body, ';', source)) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equals = indexOfTopLevel(trimmed, '=');
            if (equals < 0) {
                // A bare flag. `{silent}` and `{silent=true}` mean the same
                // thing, which is what people write.
                values.put(trimmed, "true");
                continue;
            }
            String key = trimmed.substring(0, equals).trim();
            String value = trimmed.substring(equals + 1).trim();
            if (key.isEmpty()) {
                throw new ParseException(source, "parameter with no name in '" + text + "'");
            }
            values.put(key, parseValue(value, source));
        }
        return Args.of(values);
    }

    private static Object parseValue(String value, String source) {
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return parseArgs(value, source);
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            for (String element : splitTopLevel(value.substring(1, value.length() - 1), ',', source)) {
                String trimmed = element.trim();
                if (!trimmed.isEmpty()) {
                    list.add(parseValue(trimmed, source));
                }
            }
            return list;
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return unquote(value);
        }
        return value;
    }

    /** Splits on whitespace outside braces, brackets and quotes. */
    static List<String> tokenize(String line, String source) {
        List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
                continue;
            }
            if (quoted && character == '\\') {
                current.append(character);
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }
            if (!quoted) {
                if (character == '{' || character == '[') {
                    depth++;
                } else if (character == '}' || character == ']') {
                    depth--;
                    if (depth < 0) {
                        throw new ParseException(source, "unbalanced '" + character + "' in '" + line + "'");
                    }
                }
                if (Character.isWhitespace(character) && depth == 0) {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
            }
            current.append(character);
        }

        if (quoted) {
            throw new ParseException(source, "unterminated quote in '" + line + "'");
        }
        if (depth != 0) {
            throw new ParseException(source, "unbalanced braces in '" + line + "'");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Splits on {@code delimiter} at the top nesting level of its own block. */
    static List<String> splitTopLevel(String text, char delimiter, String source) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
                continue;
            }
            if (quoted && character == '\\') {
                current.append(character);
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }
            if (!quoted) {
                if (character == '{' || character == '[') {
                    depth++;
                } else if (character == '}' || character == ']') {
                    depth--;
                    if (depth < 0) {
                        throw new ParseException(source, "unbalanced '" + character + "' in '" + text + "'");
                    }
                } else if (character == delimiter && depth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(character);
        }
        if (quoted) {
            throw new ParseException(source, "unterminated quote in '" + text + "'");
        }
        parts.add(current.toString());
        return parts;
    }

    private static int indexOfTopLevel(String text, char needle) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (character == '{' || character == '[') {
                depth++;
            } else if (character == '}' || character == ']') {
                depth--;
            } else if (character == needle && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static String unquote(String value) {
        String inner = value.substring(1, value.length() - 1);
        StringBuilder builder = new StringBuilder(inner.length());
        for (int index = 0; index < inner.length(); index++) {
            char character = inner.charAt(index);
            if (character == '\\' && index + 1 < inner.length()) {
                builder.append(inner.charAt(++index));
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static TargeterNode appendSource(TargeterNode head, TargeterNode tail) {
        // `@a of @b of @c` chains rightwards, so the new source attaches to the
        // deepest node rather than replacing the one already there.
        if (head.source() == null) {
            return new TargeterNode(head.type(), head.args(), tail);
        }
        return new TargeterNode(head.type(), head.args(), appendSource(head.source(), tail));
    }

    private static Split splitNameAndArgs(String token, String source) {
        int brace = token.indexOf('{');
        if (brace < 0) {
            return new Split(token, Args.EMPTY);
        }
        if (!token.endsWith("}")) {
            throw new ParseException(source, "unbalanced braces in '" + token + "'");
        }
        return new Split(token.substring(0, brace), parseArgs(token.substring(brace), source));
    }

    private static final class Split {
        private final String name;
        private final Args args;

        private Split(String name, Args args) {
            this.name = name.trim();
            this.args = args;
        }
    }
}
