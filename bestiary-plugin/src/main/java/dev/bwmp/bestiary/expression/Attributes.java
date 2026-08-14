package dev.bwmp.bestiary.expression;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Attribute lookup by name, never by static field.
 * <p>
 * At 1.20.5 {@code Attribute} became a registry and its constants were renamed:
 * {@code GENERIC_MAX_HEALTH} became {@code minecraft:max_health}. A static field
 * reference compiles against one of those and fails to link against the other,
 * so every attribute in Bestiary is resolved through here.
 * <p>
 * Both lookup paths are reflective, including {@code Registry.ATTRIBUTE} —
 * which does not exist in the 1.19.4 API this module compiles against, so
 * naming it directly would not compile at all.
 */
public final class Attributes {

    private static final Map<String, Attribute> CACHE = new HashMap<>();
    private static final Object REGISTRY = findRegistry();
    private static final Method REGISTRY_GET = findRegistryGet();

    private Attributes() {
    }

    /**
     * @param legacyName the pre-1.20.5 constant, e.g. {@code GENERIC_MAX_HEALTH}
     */
    public static synchronized Attribute byLegacyName(String legacyName) {
        if (legacyName == null || legacyName.isEmpty()) {
            return null;
        }
        if (CACHE.containsKey(legacyName)) {
            return CACHE.get(legacyName);
        }
        Attribute resolved = lookup(legacyName);
        CACHE.put(legacyName, resolved);
        return resolved;
    }

    public static Attribute byConfigName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String cleaned = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace(' ', '_');
        Attribute direct = byLegacyName(cleaned);
        if (direct != null) {
            return direct;
        }
        for (String prefix : new String[]{"GENERIC_", "PLAYER_", "ZOMBIE_", "HORSE_"}) {
            Attribute prefixed = byLegacyName(prefix + cleaned);
            if (prefixed != null) {
                return prefixed;
            }
        }
        return null;
    }

    private static Attribute lookup(String legacyName) {
        Attribute fromEnum = fromEnum(legacyName);
        if (fromEnum != null) {
            return fromEnum;
        }
        return fromRegistry(modernKey(legacyName));
    }

    private static Attribute fromEnum(String legacyName) {
        try {
            Method valueOf = Attribute.class.getMethod("valueOf", String.class);
            Object value = valueOf.invoke(null, legacyName);
            return value instanceof Attribute ? (Attribute) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Absent once Attribute stops being an enum, and throwing an
            // InvocationTargetException wrapping IllegalArgumentException for a
            // name this server does not have. Both mean "try the registry".
            return null;
        }
    }

    private static Attribute fromRegistry(String key) {
        if (REGISTRY == null || REGISTRY_GET == null) {
            return null;
        }
        try {
            Object value = REGISTRY_GET.invoke(REGISTRY, NamespacedKey.minecraft(key));
            return value instanceof Attribute ? (Attribute) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object findRegistry() {
        try {
            Class<?> registry = Class.forName("org.bukkit.Registry");
            return registry.getField("ATTRIBUTE").get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findRegistryGet() {
        try {
            return Class.forName("org.bukkit.Registry").getMethod("get", NamespacedKey.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String modernKey(String legacyName) {
        String lower = legacyName.toLowerCase(Locale.ROOT);
        for (String prefix : new String[]{"generic_", "player_", "zombie_", "horse_"}) {
            if (lower.startsWith(prefix)) {
                return lower.substring(prefix.length());
            }
        }
        return lower;
    }
}
