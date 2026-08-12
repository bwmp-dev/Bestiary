package dev.bwmp.bestiary.api.config;

import dev.bwmp.bestiary.api.skill.ParameterSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parameters of one parsed line, keyed by normalized name.
 * <p>
 * Values are {@code String}, {@code List<Object>} or a nested {@code Args} —
 * exactly the three shapes YAML produces, which is what makes desugaring
 * mechanical: {@code particle{shape={type=ring;radius=6}}} builds the same
 * object graph as the equivalent YAML map, and both then hit one parser.
 */
public final class Args {

    public static final Args EMPTY = new Args(Map.of());

    private final Map<String, Object> values;

    private Args(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static Args of(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> normalized.put(ParameterSpec.normalize(key), value));
        return new Args(normalized);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean contains(String key) {
        return values.containsKey(ParameterSpec.normalize(key));
    }

    public Object get(String key) {
        return values.get(ParameterSpec.normalize(key));
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** A copy with canonical parameter names applied. Used once, at compile. */
    public Args canonicalized(java.util.function.UnaryOperator<String> canonical) {
        Map<String, Object> renamed = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> renamed.put(canonical.apply(key), value));
        return new Args(renamed);
    }

    public Args with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(ParameterSpec.normalize(key), value);
        return new Args(copy);
    }

    public Args without(String key) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.remove(ParameterSpec.normalize(key));
        return new Args(copy);
    }

    /** Round-trips to shorthand, for {@code /bestiary info} and the importer. */
    public String toShorthand() {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(';');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(render(entry.getValue()));
        }
        return builder.append('}').toString();
    }

    private static String render(Object value) {
        if (value instanceof Args) {
            return ((Args) value).toShorthand();
        }
        if (value instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object element : (List<?>) value) {
                parts.add(render(element));
            }
            return String.join(",", parts);
        }
        String text = String.valueOf(value);
        boolean needsQuotes = text.isEmpty()
                || text.indexOf(' ') >= 0 || text.indexOf(';') >= 0
                || text.indexOf('=') >= 0 || text.indexOf('}') >= 0;
        return needsQuotes ? '"' + text.replace("\"", "\\\"") + '"' : text;
    }

    @Override
    public String toString() {
        return toShorthand();
    }

    public static final class Builder {

        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            values.put(ParameterSpec.normalize(key), value);
            return this;
        }

        public boolean has(String key) {
            return values.containsKey(ParameterSpec.normalize(key));
        }

        public Args build() {
            return Args.of(values);
        }
    }
}
