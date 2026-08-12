package dev.bwmp.bestiary.hook;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.text.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * The {@code %bestiary_*%} expansion.
 * <p>
 * Registering one rather than only consuming PlaceholderAPI is what lets TAB,
 * FancyHolograms, DiscordSRV and MiniMOTD show boss state without any of them
 * knowing Bestiary exists.
 * <p>
 * <b>Every placeholder is answerable without a database round-trip.</b>
 * PlaceholderAPI resolves on the main thread, often once per player per tick
 * for a TAB header, so kill counts and anchor cooldowns come from the in-memory
 * view maintained alongside the storage writes and are never queried on demand.
 * <p>
 * The expansion lives in bestiary-plugin and is not part of the public API: a
 * third party wanting this data programmatically uses {@code BestiaryAPI}, so
 * there is one source of truth and the placeholder set can change without a
 * breaking API release.
 */
public final class BestiaryExpansion extends PlaceholderExpansion {

    private final Engine engine;

    public BestiaryExpansion(Engine engine) {
        this.engine = engine;
    }

    @Override
    public String getIdentifier() {
        return "bestiary";
    }

    @Override
    public String getAuthor() {
        return "bwmp";
    }

    @Override
    public String getVersion() {
        return engine.plugin().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // Survives a PlaceholderAPI reload; without this the expansion vanishes
        // and every placeholder starts rendering as raw text.
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String request = params.toLowerCase(Locale.ROOT);
        Player player = offline == null ? null : offline.getPlayer();

        if (request.equals("kills_total")) {
            return player == null ? "0" : Integer.toString(engine.stats().total(player));
        }
        if (request.startsWith("kills_")) {
            NamespacedKey mob = MobManager.parseKey(request.substring("kills_".length()));
            return player == null || mob == null ? "0"
                    : Integer.toString(engine.stats().kills(player, mob));
        }
        if (request.startsWith("last_kill_")) {
            NamespacedKey mob = MobManager.parseKey(request.substring("last_kill_".length()));
            if (player == null || mob == null) {
                return "";
            }
            long since = engine.stats().sinceLastKill(player, mob);
            return since < 0 ? "" : Durations.humanize(since);
        }
        if (request.startsWith("anchor_ready_")) {
            String anchor = request.substring("anchor_ready_".length());
            return engine.anchors().cooldownRemaining(anchor) <= 0 ? "true" : "false";
        }
        if (request.startsWith("anchor_cooldown_")) {
            String anchor = request.substring("anchor_cooldown_".length());
            long remaining = engine.anchors().cooldownRemaining(anchor);
            return remaining <= 0 ? "0s" : Durations.humanize(remaining);
        }
        if (request.equals("active_count")) {
            return Integer.toString(engine.mobs().activeMobs().size());
        }
        if (request.startsWith("nearest_")) {
            NamespacedKey mob = MobManager.parseKey(request.substring("nearest_".length()));
            if (player == null || mob == null) {
                return "";
            }
            double best = Double.MAX_VALUE;
            for (MobInstance instance : engine.mobs().instances()) {
                if (!instance.definition().id().equals(mob)
                        || !instance.entity().getWorld().equals(player.getWorld())) {
                    continue;
                }
                best = Math.min(best, instance.entity().getLocation().distance(player.getLocation()));
            }
            return best == Double.MAX_VALUE ? "" : Long.toString(Math.round(best));
        }
        if (request.equals("engaged")) {
            MobInstance engaged = engagedWith(player);
            return engaged == null ? "" : Text.plain(engaged.definition().display());
        }
        if (request.equals("engaged_hp_percent")) {
            MobInstance engaged = engagedWith(player);
            if (engaged == null) {
                return "";
            }
            double max = engine.mobs().maxHealth(engaged.entity());
            return max <= 0 ? "0"
                    : Long.toString(Math.round(engaged.entity().getHealth() / max * 100.0d));
        }
        if (request.startsWith("var_")) {
            Object value = engine.executor().globalVariables().get(request.substring("var_".length()));
            return value == null ? "" : String.valueOf(value);
        }
        if (request.startsWith("mobvar_")) {
            MobInstance engaged = engagedWith(player);
            if (engaged == null) {
                return "";
            }
            Object value = engaged.variables().get(request.substring("mobvar_".length()));
            return value == null ? "" : String.valueOf(value);
        }
        return null;
    }

    /**
     * The boss this player is fighting: the nearest live mob whose threat table
     * or damage ledger has them on it. Both are already in memory.
     */
    private MobInstance engagedWith(Player player) {
        if (player == null) {
            return null;
        }
        MobInstance best = null;
        double nearest = Double.MAX_VALUE;
        for (MobInstance instance : engine.mobs().instances()) {
            if (!instance.entity().getWorld().equals(player.getWorld())) {
                continue;
            }
            boolean engaged = instance.ledger().dealtBy(player) > 0
                    || instance.threat(player).orElse(0.0d) > 0;
            if (!engaged) {
                continue;
            }
            double distance = instance.entity().getLocation().distanceSquared(player.getLocation());
            if (distance < nearest) {
                nearest = distance;
                best = instance;
            }
        }
        return best;
    }
}
