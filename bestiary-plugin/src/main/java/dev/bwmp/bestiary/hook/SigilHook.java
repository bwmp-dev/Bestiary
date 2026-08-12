package dev.bwmp.bestiary.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Item resolution through Sigil, falling back to {@code minecraft:} materials.
 * <p>
 * Reached reflectively rather than by depending on {@code sigil-api}: Bestiary
 * has to run on a server without Sigil, and a hard dependency on a published
 * artifact would also pin Bestiary to a Sigil version for the sake of two
 * method calls.
 */
public final class SigilHook {

    private final boolean present;
    private Object api;
    private Method itemMethod;
    private Method createStackMethod;

    SigilHook() {
        boolean available = false;
        try {
            Class<?> apiClass = Class.forName("dev.bwmp.sigil.api.SigilAPI");
            Object optional = apiClass.getMethod("get").invoke(null);
            Optional<?> resolved = (Optional<?>) optional;
            if (resolved.isPresent()) {
                this.api = resolved.get();
                this.itemMethod = apiClass.getMethod("item", NamespacedKey.class);
                Class<?> customItem = Class.forName("dev.bwmp.sigil.api.item.CustomItem");
                this.createStackMethod = customItem.getMethod("createStack", int.class);
                available = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Absent, or present but not yet enabled. Either way: materials.
        }
        this.present = available;
    }

    public boolean present() {
        return present;
    }

    /**
     * @param id {@code sigil:sky_glaive}, {@code minecraft:diamond} or a bare
     *           material name
     * @return null when nothing resolves, which callers turn into a load-time
     *         error against the drop table rather than a silent nothing at kill
     *         time
     */
    public ItemStack resolveItem(String id, int amount) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String text = id.trim().toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        String namespace = colon < 0 ? "" : text.substring(0, colon);
        String key = colon < 0 ? text : text.substring(colon + 1);

        if (!namespace.equals("minecraft") && !namespace.isEmpty() && present) {
            ItemStack custom = fromSigil(new NamespacedKey(namespace, key), amount);
            if (custom != null) {
                return custom;
            }
        }

        Material material = Material.matchMaterial(namespace.isEmpty() ? key : namespace + ":" + key);
        if (material == null) {
            material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
        }
        return material == null ? null : new ItemStack(material, Math.max(1, amount));
    }

    public ItemStack resolveItem(String id) {
        return resolveItem(id, 1);
    }

    private ItemStack fromSigil(NamespacedKey id, int amount) {
        try {
            Optional<?> item = (Optional<?>) itemMethod.invoke(api, id);
            if (item.isEmpty()) {
                return null;
            }
            Object stack = createStackMethod.invoke(item.get(), Math.max(1, amount));
            return stack instanceof ItemStack ? (ItemStack) stack : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** True when the id names a Sigil item that does not exist. */
    public boolean isUnresolvableSigilId(String id) {
        if (id == null || id.indexOf(':') < 0) {
            return false;
        }
        String namespace = id.substring(0, id.indexOf(':')).toLowerCase(Locale.ROOT);
        if (namespace.equals("minecraft")) {
            return false;
        }
        return resolveItem(id, 1) == null;
    }

    static boolean pluginEnabled(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null
                && Bukkit.getPluginManager().isPluginEnabled(name);
    }
}
