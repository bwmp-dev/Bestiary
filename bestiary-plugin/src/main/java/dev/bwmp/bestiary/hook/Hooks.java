package dev.bwmp.bestiary.hook;

import dev.bwmp.keystone.config.LoadReport;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Every optional integration, probed once and reported once.
 * <p>
 * All of them are reflective. That is deliberate: a hard dependency on any of
 * these would pin Bestiary to one of their versions, and every one of them
 * exists to answer a question Bestiary can survive not having an answer to.
 */
public final class Hooks {

    private final SigilHook sigil;
    private final VaultHook vault;
    private final RegionHook regions;
    private final PlaceholderHook placeholders;
    private final ModelEngineHook modelEngine;
    private final QuestHook quests;
    private final ExternalXpHook externalXp;
    private final boolean citizens;

    public Hooks(Plugin plugin) {
        this.sigil = new SigilHook();
        this.vault = new VaultHook();
        this.regions = new RegionHook();
        this.placeholders = new PlaceholderHook();
        this.modelEngine = new ModelEngineHook();
        this.quests = new QuestHook();
        this.externalXp = new ExternalXpHook(plugin);
        this.citizens = SigilHook.pluginEnabled("Citizens");
    }

    public SigilHook sigil() {
        return sigil;
    }

    public VaultHook vault() {
        return vault;
    }

    public RegionHook regions() {
        return regions;
    }

    public PlaceholderHook placeholders() {
        return placeholders;
    }

    public ModelEngineHook modelEngine() {
        return modelEngine;
    }

    public QuestHook quests() {
        return quests;
    }

    public ExternalXpHook externalXp() {
        return externalXp;
    }

    public boolean citizensPresent() {
        return citizens;
    }

    /** True for a Citizens NPC, which Bestiary mobs never adopt as a target. */
    public boolean isNpc(Entity entity) {
        return citizens && entity != null && entity.hasMetadata("NPC");
    }

    /** One block at startup naming what is wired up and what is not. */
    public void describe(LoadReport report) {
        line(report, "Sigil", sigil.present(), "drop table items resolve to materials only");
        line(report, "Vault", vault.present(), "the currency mechanic is a no-op");
        line(report, "WorldGuard", regions.worldGuardPresent(), "the in_region condition is always false");
        line(report, "GriefPrevention", regions.griefPreventionPresent(), "the in_claim condition is always false");
        line(report, "PlaceholderAPI", placeholders.present(), "<papi.*> placeholders resolve to nothing");
        line(report, "ModelEngine", modelEngine.present(), "mobs render as their base entity type");
        line(report, "Citizens", citizens, "NPCs are not excluded from targeting");
        line(report, "mcMMO", externalXp.mcmmoPresent(), "no XP suppression needed");
        line(report, "Jobs", externalXp.jobsPresent(), "no XP suppression needed");

        if (!quests.present()) {
            report.downgrade("hook:AetherCore", "absent; the quest_progress mechanic is a no-op");
        } else if (!quests.supportsProgress()) {
            report.downgrade("hook:AetherCore", "present, but its QuestService exposes no incremental "
                    + "progress method; quest_progress can only complete a quest, not advance it");
        }
    }

    private static void line(LoadReport report, String name, boolean present, String consequence) {
        if (!present) {
            report.downgrade("hook:" + name, "absent; " + consequence);
        }
    }
}
