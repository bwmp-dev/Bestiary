package dev.bwmp.bestiary;

import dev.bwmp.bestiary.ai.AiBridge;
import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.aura.AuraService;
import dev.bwmp.bestiary.combat.ImmunityService;
import dev.bwmp.bestiary.config.BestiarySettings;
import dev.bwmp.bestiary.drop.DropService;
import dev.bwmp.bestiary.expression.ExpressionEngine;
import dev.bwmp.bestiary.hook.Hooks;
import dev.bwmp.bestiary.mob.Keys;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.presentation.BossbarService;
import dev.bwmp.bestiary.registry.BestiaryRegistries;
import dev.bwmp.bestiary.registry.ContentSnapshot;
import dev.bwmp.bestiary.skill.SkillExecutor;
import dev.bwmp.bestiary.spawn.AnchorService;
import dev.bwmp.bestiary.stats.StatsView;
import dev.bwmp.bestiary.storage.BestiaryStorage;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * The services every part of Bestiary reaches for, as one interface.
 * <p>
 * Implemented by {@code BestiaryPlugin}. Mechanics, goals and listeners depend
 * on this rather than on the plugin class, which keeps the dependency graph a
 * fan-out from one interface instead of a cycle through the main class — and
 * makes it obvious what the extension surface actually touches.
 */
public interface Engine {

    JavaPlugin plugin();

    Logger logger();

    BestiaryScheduler scheduler();

    BestiaryRegistries registries();

    /** The current snapshot. Read once per operation, never held across ticks. */
    ContentSnapshot content();

    BestiarySettings settings();

    SkillExecutor executor();

    ExpressionEngine expressions();

    Keys keys();

    MobManager mobs();

    DropService drops();

    AuraService auras();

    BossbarService bossbars();

    ImmunityService immunity();

    Hooks hooks();

    AnchorService anchors();

    dev.bwmp.bestiary.spawn.SpawnService spawns();

    dev.bwmp.bestiary.debug.DebugService debug();

    BestiaryStorage storage();

    AiBridge ai();

    MessageService messages();

    StatsView stats();
}
