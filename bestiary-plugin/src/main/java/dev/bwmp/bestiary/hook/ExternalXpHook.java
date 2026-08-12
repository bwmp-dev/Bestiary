package dev.bwmp.bestiary.hook;

import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * Suppresses mcMMO and Jobs XP for a Bestiary mob.
 * <p>
 * Not speculative: where both are installed they are tuned against a known mob
 * set. Dropping a 400 HP boss into that without a switch would quietly break
 * the tuning.
 * <p>
 * Done with the metadata keys both plugins already check for spawner-spawned
 * mobs, rather than by cancelling their XP events. That is the supported route,
 * it costs nothing when neither plugin is installed, and it does not require
 * compiling against either.
 */
public final class ExternalXpHook {

    private static final String MCMMO_KEY = "mcMMO: Spawned Entity";
    private static final String JOBS_KEY = "jobsMobSpawner";

    private final Plugin plugin;
    private final boolean mcmmo;
    private final boolean jobs;

    ExternalXpHook(Plugin plugin) {
        this.plugin = plugin;
        this.mcmmo = SigilHook.pluginEnabled("mcMMO");
        this.jobs = SigilHook.pluginEnabled("Jobs");
    }

    public boolean mcmmoPresent() {
        return mcmmo;
    }

    public boolean jobsPresent() {
        return jobs;
    }

    public void suppress(Entity entity, boolean suppressMcmmo, boolean suppressJobs) {
        if (entity == null) {
            return;
        }
        if (suppressMcmmo && mcmmo) {
            entity.setMetadata(MCMMO_KEY, new FixedMetadataValue(plugin, true));
        }
        if (suppressJobs && jobs) {
            entity.setMetadata(JOBS_KEY, new FixedMetadataValue(plugin, true));
        }
    }
}
