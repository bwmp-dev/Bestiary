package dev.bwmp.bestiary.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Name-to-enum lookup that survives the 1.20.5 registry rename.
 * <p>
 * Several Bukkit enums became registries with new key names between 1.19.4 and
 * 26.x — {@code Particle.REDSTONE} is {@code dust}, {@code PotionEffectType}
 * lost {@code getByName} — so nothing here references a constant. Each lookup
 * tries the enum name, then the registry key, then a small table of renames,
 * and caches the answer.
 * <p>
 * Sounds are deliberately absent: every {@code playSound} overload taking a
 * {@code String} key exists on the whole supported band, so a sound is passed
 * through as text and never resolved at all.
 */
public final class Registries {

    private static final Map<String, Particle> PARTICLES = new HashMap<>();
    private static final Map<String, PotionEffectType> EFFECTS = new HashMap<>();
    private static final Map<String, EntityType> ENTITY_TYPES = new HashMap<>();

    private static final Map<String, String> PARTICLE_RENAMES = Map.of(
            "redstone", "dust",
            "spell_mob", "effect",
            "spell_mob_ambient", "entity_effect",
            "spell", "instant_effect",
            "spell_instant", "instant_effect",
            "spell_witch", "witch",
            "villager_happy", "happy_villager",
            "villager_angry", "angry_villager",
            "town_aura", "mycelium",
            "water_drop", "rain");

    private Registries() {
    }

    public static synchronized Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = normalize(name);
        if (PARTICLES.containsKey(key)) {
            return PARTICLES.get(key);
        }
        Particle resolved = enumByName(Particle.class, key);
        if (resolved == null) {
            resolved = enumByName(Particle.class, PARTICLE_RENAMES.getOrDefault(key, key));
        }
        if (resolved == null) {
            resolved = fromRegistry("PARTICLE_TYPE", key, Particle.class);
        }
        if (resolved == null) {
            // The rename table is bidirectional in practice: a config written
            // for 26.x says `dust`, one written for 1.19 says `redstone`, and
            // both must work on both.
            for (Map.Entry<String, String> entry : PARTICLE_RENAMES.entrySet()) {
                if (entry.getValue().equals(key)) {
                    resolved = enumByName(Particle.class, entry.getKey());
                    if (resolved != null) {
                        break;
                    }
                }
            }
        }
        PARTICLES.put(key, resolved);
        return resolved;
    }

    public static synchronized PotionEffectType potionEffect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = normalize(name);
        if (EFFECTS.containsKey(key)) {
            return EFFECTS.get(key);
        }
        PotionEffectType resolved = fromRegistry("EFFECT", key, PotionEffectType.class);
        if (resolved == null) {
            resolved = fromRegistry("POTION_EFFECT_TYPE", key, PotionEffectType.class);
        }
        if (resolved == null) {
            try {
                Method byName = PotionEffectType.class.getMethod("getByName", String.class);
                Object value = byName.invoke(null, key);
                resolved = value instanceof PotionEffectType ? (PotionEffectType) value : null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                resolved = null;
            }
        }
        EFFECTS.put(key, resolved);
        return resolved;
    }

    public static synchronized EntityType entityType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = normalize(name);
        if (ENTITY_TYPES.containsKey(key)) {
            return ENTITY_TYPES.get(key);
        }
        EntityType resolved = enumByName(EntityType.class, key);
        if (resolved == null) {
            resolved = fromRegistry("ENTITY_TYPE", key, EntityType.class);
        }
        ENTITY_TYPES.put(key, resolved);
        return resolved;
    }

    public static Material material(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(name.trim());
        return material != null ? material : Material.matchMaterial(normalize(name).toUpperCase(Locale.ROOT));
    }

    private static String normalize(String name) {
        String text = name.trim().toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        return colon < 0 ? text : text.substring(colon + 1);
    }

    private static <T> T enumByName(Class<T> type, String key) {
        try {
            Method valueOf = type.getMethod("valueOf", String.class);
            Object value = valueOf.invoke(null, key.toUpperCase(Locale.ROOT));
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static <T> T fromRegistry(String registryField, String key, Class<T> type) {
        try {
            Class<?> registry = Class.forName("org.bukkit.Registry");
            Object instance = registry.getField(registryField).get(null);
            if (instance == null) {
                return null;
            }
            Method get = registry.getMethod("get", NamespacedKey.class);
            Object value = get.invoke(instance, NamespacedKey.minecraft(key));
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
