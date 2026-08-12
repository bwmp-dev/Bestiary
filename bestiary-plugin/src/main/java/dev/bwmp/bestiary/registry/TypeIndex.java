package dev.bwmp.bestiary.registry;

import dev.bwmp.bestiary.api.skill.ParameterSpec;
import dev.bwmp.keystone.registry.OwnedRegistry;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Name-to-type lookup over an {@link OwnedRegistry}.
 * <p>
 * Config writes {@code set_health}, {@code setHealth} or
 * {@code bestiary:set_health} and means the same thing every time, so the index
 * is keyed on the normalized name and rebuilt whenever the registry changes.
 * Rebuilding on change rather than normalizing per lookup matters: this is on
 * the load path for every line of every skill, and an addon registering a type
 * must be visible immediately.
 *
 * @param <T> the registered type
 */
public final class TypeIndex<T> {

    private final OwnedRegistry<T> registry;
    private final String description;
    private volatile Map<String, NamespacedKey> byName = Map.of();

    public TypeIndex(OwnedRegistry<T> registry, String description) {
        this.registry = registry;
        this.description = description;
        registry.onChanged(this::rebuild);
        rebuild();
    }

    private void rebuild() {
        Map<String, NamespacedKey> index = new LinkedHashMap<>();
        for (NamespacedKey id : registry.ids()) {
            index.put(id.toString().toLowerCase(Locale.ROOT), id);
            // The bare name is a convenience, and first registration wins so a
            // late addon cannot quietly steal `damage` from the built-in one.
            index.putIfAbsent(ParameterSpec.normalize(id.getKey()), id);
        }
        this.byName = Map.copyOf(index);
    }

    public Optional<T> find(String written) {
        if (written == null || written.isBlank()) {
            return Optional.empty();
        }
        String text = written.trim().toLowerCase(Locale.ROOT);
        NamespacedKey id = byName.get(text);
        if (id == null && text.indexOf(':') < 0) {
            id = byName.get(ParameterSpec.normalize(text));
        }
        if (id == null && text.indexOf(':') >= 0) {
            // A namespaced id whose key half was written in the other spelling.
            int colon = text.indexOf(':');
            id = byName.get(text.substring(0, colon) + ":"
                    + ParameterSpec.normalize(text.substring(colon + 1)));
        }
        return id == null ? Optional.empty() : registry.get(id);
    }

    public boolean contains(String written) {
        return find(written).isPresent();
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (NamespacedKey id : registry.ids()) {
            names.add(id.getNamespace().equals("bestiary") ? id.getKey() : id.toString());
        }
        names.sort(String::compareTo);
        return names;
    }

    public OwnedRegistry<T> registry() {
        return registry;
    }

    public String description() {
        return description;
    }
}
