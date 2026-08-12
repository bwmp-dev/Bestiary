package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.skill.Constant;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.expression.ExpressionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@link MechanicConfig} over a parsed {@link Args} block.
 * <p>
 * Every numeric and string getter compiles an {@link Expression} rather than
 * returning a value, which is what makes level scaling and variable-driven
 * skills possible. The plain {@code integer} / {@code decimal} / {@code bool}
 * getters exist for parameters that genuinely cannot vary per execution —
 * a targeter's {@code limit}, a mechanic's {@code shape} — and they resolve at
 * load, so a placeholder written there is a load-time error rather than a
 * silent zero.
 */
public final class ArgsConfig implements MechanicConfig {

    private final Args args;
    private final ExpressionEngine engine;
    private final String source;

    public ArgsConfig(Args args, ExpressionEngine engine, String source) {
        this.args = args == null ? Args.EMPTY : args;
        this.engine = engine;
        this.source = source == null ? "" : source;
    }

    public Args args() {
        return args;
    }

    @Override
    public boolean contains(String key) {
        return args.contains(key);
    }

    @Override
    public Set<String> keys() {
        return args.keys();
    }

    @Override
    public String raw(String key, String fallback) {
        Object value = args.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    @Override
    public Expression number(String key, double fallback) {
        Object value = args.get(key);
        if (value == null) {
            return Constant.of(fallback);
        }
        return engine.compileNumber(String.valueOf(value), source);
    }

    @Override
    public Expression text(String key, String fallback) {
        Object value = args.get(key);
        if (value == null) {
            return Constant.of(fallback);
        }
        return engine.compileText(String.valueOf(value), source);
    }

    @Override
    public Expression require(String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new ParseException(source, "required parameter '" + key + "' is missing"
                    + (args.isEmpty() ? "" : "; got " + args.keys()));
        }
        return engine.compileNumber(String.valueOf(value), source);
    }

    /** A required parameter used as text; the same message, no arithmetic. */
    public Expression requireText(String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new ParseException(source, "required parameter '" + key + "' is missing"
                    + (args.isEmpty() ? "" : "; got " + args.keys()));
        }
        return engine.compileText(String.valueOf(value), source);
    }

    @Override
    public int integer(String key, int fallback) {
        return (int) Math.round(decimal(key, fallback));
    }

    @Override
    public double decimal(String key, double fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            throw new ParseException(source, "'" + key + "' must be a plain number here, found '" + text + "'");
        }
    }

    @Override
    public boolean bool(String key, boolean fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes") || text.equals("on") || text.equals("1")) {
            return true;
        }
        if (text.equals("false") || text.equals("no") || text.equals("off") || text.equals("0")) {
            return false;
        }
        throw new ParseException(source, "'" + key + "' must be true or false, found '" + text + "'");
    }

    @Override
    public long ticks(String key, long fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Durations.parseTicks(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new ParseException(source, "'" + key + "': " + exception.getMessage());
        }
    }

    @Override
    public List<String> stringList(String key) {
        Object value = args.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List) {
            List<String> strings = new ArrayList<>();
            for (Object element : (List<?>) value) {
                strings.add(String.valueOf(element));
            }
            return strings;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        // Shorthand has no list literal for the common case, so a
        // comma-separated value is one. `types=zombie,skeleton` is what people
        // write, and rejecting it to insist on `[zombie,skeleton]` buys nothing.
        List<String> strings = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                strings.add(trimmed);
            }
        }
        return strings;
    }

    @Override
    public MechanicConfig section(String key) {
        Object value = args.get(key);
        if (value instanceof Args) {
            return new ArgsConfig((Args) value, engine, source + "/" + key);
        }
        return MechanicConfig.EMPTY;
    }

    /** The raw nested block, for compiling child structures. */
    public Args sectionArgs(String key) {
        Object value = args.get(key);
        return value instanceof Args ? (Args) value : Args.EMPTY;
    }

    /** Raw list entries, for {@code drops:} and {@code goals:} style blocks. */
    public List<Object> rawList(String key) {
        Object value = args.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        return List.of();
    }

    @Override
    public <E extends Enum<E>> E enumValue(Class<E> type, String key, E fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim().replace(' ', '_').replace('-', '_');
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(text)) {
                return candidate;
            }
        }
        StringBuilder known = new StringBuilder();
        for (E candidate : type.getEnumConstants()) {
            if (known.length() > 0) {
                known.append(", ");
            }
            known.append(candidate.name().toLowerCase(Locale.ROOT));
        }
        throw new ParseException(source, "'" + key + "' is '" + text + "', expected one of: " + known);
    }

    @Override
    public String source() {
        return source;
    }

    public ExpressionEngine engine() {
        return engine;
    }

    /** A view of a nested block, keeping the engine and extending the location. */
    public ArgsConfig child(Args nested, String suffix) {
        return new ArgsConfig(nested, engine, source + "/" + suffix);
    }
}
