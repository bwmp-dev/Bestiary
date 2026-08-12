package dev.bwmp.bestiary.api.skill;

import java.util.List;
import java.util.Locale;

/**
 * One declared parameter, with its shorthand aliases.
 * <p>
 * The alias table lives with the mechanic rather than in the parser. That is
 * the whole reason {@code s=} can mean {@code sound} on one line and something
 * else on another without the parser knowing anything about either.
 */
public final class ParameterSpec {

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final String defaultValue;

    private ParameterSpec(String name, List<String> aliases, String description, String defaultValue) {
        this.name = normalize(name);
        this.aliases = aliases.stream().map(ParameterSpec::normalize).toList();
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue == null ? "" : defaultValue;
    }

    public static ParameterSpec of(String name, String description, String defaultValue, String... aliases) {
        return new ParameterSpec(name, List.of(aliases), description, defaultValue);
    }

    public static ParameterSpec required(String name, String description, String... aliases) {
        return new ParameterSpec(name, List.of(aliases), description, null);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String description() {
        return description;
    }

    /** Empty means required. */
    public String defaultValue() {
        return defaultValue;
    }

    public boolean isRequired() {
        return defaultValue.isEmpty();
    }

    /**
     * Lowercases and strips underscores, so {@code ignore_armor} and
     * {@code ignoreArmor} are the same key. Applied to config keys and to
     * declared names alike, which is what makes the two definition forms
     * interchangeable.
     */
    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(key.length());
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character != '_' && character != '-') {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        String base = name + (aliases.isEmpty() ? "" : " (" + String.join(", ", aliases) + ")");
        return base + (isRequired() ? " [required]" : " = " + defaultValue)
                + (description.isEmpty() ? "" : " — " + description);
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
