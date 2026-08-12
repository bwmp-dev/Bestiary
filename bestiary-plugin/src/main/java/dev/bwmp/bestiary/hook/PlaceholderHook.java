package dev.bwmp.bestiary.hook;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * PlaceholderAPI, consumed as {@code <papi.*>} inside expressions.
 * <p>
 * Providing the {@code %bestiary_*%} expansion is a different concern and lives
 * in {@code BestiaryExpansion}; this half only reads other plugins' state, so a
 * condition can gate on a Jobs level or a rank without Bestiary hooking that
 * plugin directly.
 */
public final class PlaceholderHook {

    private Method setPlaceholders;

    PlaceholderHook() {
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.setPlaceholders = api.getMethod("setPlaceholders",
                    org.bukkit.OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.setPlaceholders = null;
        }
    }

    public boolean present() {
        return setPlaceholders != null;
    }

    /**
     * @return null when PlaceholderAPI is absent, or when the placeholder came
     *         back unchanged — which is how PlaceholderAPI says "I don't know
     *         that one", and which the expression engine turns into a warning
     */
    public String resolve(Player player, String placeholder) {
        if (setPlaceholders == null) {
            return null;
        }
        String wrapped = "%" + placeholder + "%";
        try {
            Object result = setPlaceholders.invoke(null, player, wrapped);
            String text = String.valueOf(result);
            return wrapped.equals(text) ? null : text;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
