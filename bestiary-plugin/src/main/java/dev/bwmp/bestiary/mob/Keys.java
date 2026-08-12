package dev.bwmp.bestiary.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * The persistent data keys a spawned mob carries.
 * <p>
 * {@link MobResolver} is the only code that reads {@link #id}, so the schema
 * has exactly one consumer — the same discipline Sigil applies to
 * {@code sigil:id}.
 */
public final class Keys {

    /** {@code "aether:valkyrie_champion"} — the only identity. */
    public final NamespacedKey id;
    /** Definition revision when spawned, compared on adoption. */
    public final NamespacedKey revision;
    public final NamespacedKey level;
    /** Owning spawn anchor, when spawned from one. */
    public final NamespacedKey anchor;
    /** JSON-encoded mob-scoped variables. */
    public final NamespacedKey variables;
    public final NamespacedKey phase;
    public final NamespacedKey spawnLocation;
    public final NamespacedKey owner;

    public Keys(Plugin plugin) {
        this.id = new NamespacedKey(plugin, "id");
        this.revision = new NamespacedKey(plugin, "rev");
        this.level = new NamespacedKey(plugin, "level");
        this.anchor = new NamespacedKey(plugin, "anchor");
        this.variables = new NamespacedKey(plugin, "vars");
        this.phase = new NamespacedKey(plugin, "phase");
        this.spawnLocation = new NamespacedKey(plugin, "spawn");
        this.owner = new NamespacedKey(plugin, "owner");
    }
}
