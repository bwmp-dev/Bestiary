package dev.bwmp.bestiary.metrics;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.ai.AiBridge;
import dev.bwmp.bestiary.hook.Hooks;
import dev.bwmp.bestiary.registry.ContentSnapshot;
import dev.bwmp.keystone.metrics.Chart;
import dev.bwmp.keystone.metrics.KeystoneMetrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Bestiary reports, and where to.
 * <p>
 * Every sampler reads either the published {@link ContentSnapshot} — immutable
 * and swapped in one write — or a concurrent structure. None of them walk a
 * collection a reload mutates: bStats samples on its own thread on Folia, so a
 * sampler iterating live state would be a ConcurrentModificationException that
 * appears on exactly one platform and nowhere a developer would look for it.
 */
public final class BestiaryMetrics {

    private static final int BSTATS_SERVICE_ID = 33366;
    private static final String TELEMETRY_URL = "https://plugins.metrics.bwmp.dev";
    private static final String TELEMETRY_PROJECT = "bestiary";

    private BestiaryMetrics() {
    }

    public static void start(BestiaryPlugin plugin) {
        KeystoneMetrics.builder(plugin.keystone())
                .bstats(BSTATS_SERVICE_ID)
                .telemetry(TELEMETRY_URL, TELEMETRY_PROJECT)
                .chart(Chart.multiLine("content", () -> content(plugin)))
                .chart(Chart.singleLine("active_mobs", () -> plugin.mobs().instances().size()))
                .chart(Chart.simplePie("storage", () -> plugin.settings().storageType()))
                .chart(Chart.simplePie("ai", () -> ai(plugin.ai())))
                .chart(Chart.advancedPie("integrations", () -> integrations(plugin.hooks())))
                .chart(Chart.advancedPie("addons", () -> addons(plugin)))
                .chart(Chart.simplePie("builtin_content",
                        () -> plugin.settings().builtinContent() ? "Enabled" : "Disabled"))
                .start();
    }

    private static Map<String, Integer> content(BestiaryPlugin plugin) {
        ContentSnapshot content = plugin.content();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("mobs", content.mobs().size());
        counts.put("skills", content.skills().size());
        counts.put("droptables", content.dropTables().size());
        counts.put("spawners", content.spawners().size());
        return counts;
    }

    /**
     * Which of the three AI tiers this server ended up on.
     * <p>
     * The split matters because the two lower tiers are silent degradations:
     * a mob keeps working with approximated movement, so the only way to know
     * how many servers never get the real thing is to count them.
     */
    private static String ai(AiBridge ai) {
        if (!ai.available()) {
            return "None";
        }
        return ai.nmsAvailable() ? "Goal API + NMS" : "Goal API";
    }

    private static Map<String, Integer> integrations(Hooks hooks) {
        Map<String, Integer> present = new LinkedHashMap<>();
        record(present, "Sigil", hooks.sigil().present());
        record(present, "Vault", hooks.vault().present());
        record(present, "WorldGuard", hooks.regions().worldGuardPresent());
        record(present, "GriefPrevention", hooks.regions().griefPreventionPresent());
        record(present, "PlaceholderAPI", hooks.placeholders().present());
        record(present, "ModelEngine", hooks.modelEngine().present());
        record(present, "Citizens", hooks.citizensPresent());
        record(present, "mcMMO", hooks.externalXp().mcmmoPresent());
        record(present, "Jobs", hooks.externalXp().jobsPresent());
        record(present, "AetherCore", hooks.quests().present());
        return present;
    }

    private static void record(Map<String, Integer> into, String name, boolean present) {
        if (present) {
            into.put(name, 1);
        }
    }

    private static Map<String, Integer> addons(BestiaryPlugin plugin) {
        String name = plugin.getName();
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other == plugin) {
                continue;
            }
            if (other.getDescription().getDepend().contains(name)
                    || other.getDescription().getSoftDepend().contains(name)) {
                found.put(other.getName(), 1);
            }
        }
        return found;
    }
}
