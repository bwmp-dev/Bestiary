package dev.bwmp.bestiary;

import dev.bwmp.bestiary.ai.AiBridge;
import dev.bwmp.bestiary.api.BestiaryAPI;
import dev.bwmp.bestiary.api.ai.AiController;
import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.bestiary.aura.AuraService;
import dev.bwmp.bestiary.combat.ImmunityService;
import dev.bwmp.bestiary.command.BestiaryCommand;
import dev.bwmp.bestiary.condition.BuiltinConditions;
import dev.bwmp.bestiary.config.BestiarySettings;
import dev.bwmp.bestiary.config.ContentLoader;
import dev.bwmp.bestiary.debug.DebugService;
import dev.bwmp.bestiary.drop.DropService;
import dev.bwmp.bestiary.expression.ContextPlaceholders;
import dev.bwmp.bestiary.expression.ExpressionEngine;
import dev.bwmp.bestiary.expression.MathPlaceholders;
import dev.bwmp.bestiary.expression.PapiPlaceholders;
import dev.bwmp.bestiary.expression.PlaceholderServices;
import dev.bwmp.bestiary.expression.VariablePlaceholders;
import dev.bwmp.bestiary.gui.ChatPrompts;
import dev.bwmp.bestiary.hook.Hooks;
import dev.bwmp.bestiary.listener.BreedListener;
import dev.bwmp.bestiary.listener.MobListener;
import dev.bwmp.bestiary.listener.SpawnListener;
import dev.bwmp.bestiary.mechanic.BuiltinMechanics;
import dev.bwmp.bestiary.mechanic.PlayerMechanics;
import dev.bwmp.bestiary.metrics.BestiaryMetrics;
import dev.bwmp.bestiary.mob.Keys;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.presentation.BossbarService;
import dev.bwmp.bestiary.registry.BestiaryRegistries;
import dev.bwmp.bestiary.registry.ContentSnapshot;
import dev.bwmp.bestiary.scheduler.SchedulerBridge;
import dev.bwmp.bestiary.skill.SkillCompiler;
import dev.bwmp.bestiary.skill.SkillExecutor;
import dev.bwmp.bestiary.spawn.AnchorService;
import dev.bwmp.bestiary.spawn.SpawnService;
import dev.bwmp.bestiary.stats.StatsView;
import dev.bwmp.bestiary.storage.BestiaryStorage;
import dev.bwmp.bestiary.targeter.BuiltinTargeters;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.keystone.Keystone;
import dev.bwmp.keystone.KeystoneHandle;
import dev.bwmp.keystone.compat.Platform;
import dev.bwmp.keystone.config.LoadReport;
import dev.bwmp.keystone.config.ManagedConfig;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * A custom-mob and skill engine.
 * <p>
 * Everything reaches its dependencies through {@link Engine}, which this class
 * implements — so the dependency graph is a fan-out from one interface rather
 * than a web through the main class.
 */
public final class BestiaryPlugin extends JavaPlugin implements Engine {

    private KeystoneHandle keystone;
    private MessageService messages;
    private ManagedConfig mainConfig;
    private BestiarySettings settings;

    private SchedulerBridge scheduler;
    private BestiaryRegistries registries;
    private ExpressionEngine expressions;
    private SkillExecutor executor;
    private SkillCompiler compiler;
    private Keys keys;

    private Hooks hooks;
    private MobManager mobs;
    private DropService drops;
    private AuraService auras;
    private BossbarService bossbars;
    private ImmunityService immunity;
    private AnchorService anchors;
    private SpawnService spawns;
    private BestiaryStorage storage;
    private AiBridge ai;
    private StatsView stats;
    private DebugService debug;
    private ChatPrompts prompts;

    @Override
    public void onEnable() {
        keystone = Keystone.bootstrap(this);
        messages = keystone.messages();

        saveDefaultResources();
        mainConfig = new ManagedConfig(this, "config.yml");
        settings = BestiarySettings.load(mainConfig);

        scheduler = new SchedulerBridge(keystone.scheduler());
        keys = new Keys(this);
        stats = new StatsView();
        immunity = new ImmunityService();
        hooks = new Hooks(this);

        registries = new BestiaryRegistries(
                keystone.registry("mechanic type"),
                keystone.registry("targeter type"),
                keystone.registry("condition type"),
                keystone.registry("AI goal type"));

        expressions = new ExpressionEngine();
        executor = new SkillExecutor(scheduler, getLogger());
        executor.limits(settings.limits());

        mobs = new MobManager(this);
        auras = new AuraService(this);
        bossbars = new BossbarService(this);
        drops = new DropService(this);
        storage = new BestiaryStorage(this);
        anchors = new AnchorService(this);
        spawns = new SpawnService(this);
        debug = new DebugService(messages);
        ai = new AiBridge(this);
        prompts = new ChatPrompts(this);

        registerPlaceholders();
        BuiltinConditions.EngineHolder.set(this);
        registerBuiltinTypes();

        compiler = new SkillCompiler(registries, expressions,
                entity -> mobs.instance(entity), settings.limits().maxTargets());
        executor.bind(id -> content().skill(id), entity -> mobs.instance(entity));
        executor.onGlobalVariablesChanged(values -> storage.saveGlobals(values));

        LoadReport report = new LoadReport();
        ai.initialise(keystone.platform(), report);
        bindAiEngine();
        hooks.describe(report);

        storage.open();
        executor.globalVariables().putAll(storage.loadGlobals());
        loadContent(report);
        anchors.load();
        spawns.load();
        report.print(getLogger(), "Bestiary definitions");

        registerListeners();
        new BestiaryCommand(this, messages).build().bind(this, "bestiary");

        getServer().getServicesManager().register(BestiaryAPI.class, new BestiaryApiImpl(this),
                this, ServicePriority.Normal);
        registerExpansion();
        startTasks();

        // Last, so every sampler it registers has something to read. Shutdown
        // is wired to the Keystone handle, so there is nothing to undo.
        BestiaryMetrics.start(this);
    }

    @Override
    public void onDisable() {
        if (mobs != null) {
            mobs.shutdown();
        }
        if (bossbars != null) {
            bossbars.shutdown();
        }
        if (auras != null) {
            auras.shutdown();
        }
        PlayerMechanics.shutdown();
        if (storage != null) {
            storage.close();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (keystone != null) {
            keystone.shutdown();
        }
    }

    // --- wiring -----------------------------------------------------------

    private void saveDefaultResources() {
        for (String resource : List.of("config.yml", "messages.yml",
                "skills/example.yml", "mobs/bestiary/example.yml", "droptables/example.yml")) {
            if (!new File(getDataFolder(), resource).exists() && getResource(resource) != null) {
                saveResource(resource, false);
            }
        }
        for (String directory : List.of("skills", "mobs", "droptables", "spawners")) {
            //noinspection ResultOfMethodCallIgnored
            new File(getDataFolder(), directory).mkdirs();
        }
    }

    private void registerPlaceholders() {
        PlaceholderServices services = new PlaceholderServices() {
            @Override
            public BestiaryMob mobOf(Entity entity) {
                return mobs.instance(entity);
            }

            @Override
            public Object globalVariable(String name) {
                return executor.globalVariables().get(name);
            }

            @Override
            public String placeholderApi(Player player, String placeholder) {
                return hooks.placeholders().resolve(player, placeholder);
            }

            @Override
            public String plainText(String miniMessage) {
                return Text.plain(miniMessage);
            }
        };
        expressions.register(new ContextPlaceholders(services))
                .register(new MathPlaceholders())
                .register(new VariablePlaceholders(services))
                .register(new PapiPlaceholders(services));
        expressions.onWarning((source, message) -> getLogger().warning("[expr] " + source + ": " + message));
    }

    private void registerBuiltinTypes() {
        BuiltinMechanics.all(this).forEach((name, type) ->
                registries.mechanics().registerInternal(key(name), type));
        BuiltinTargeters.all(this).forEach((name, type) ->
                registries.targeters().registerInternal(key(name), type));
        BuiltinConditions.all(this, () -> compiler).forEach((name, type) ->
                registries.conditions().registerInternal(key(name), type));
    }

    private NamespacedKey key(String name) {
        // NamespacedKey rejects underscores nowhere, but does reject capitals,
        // so the canonical lowercase name is used verbatim.
        return new NamespacedKey(this, name.toLowerCase(java.util.Locale.ROOT));
    }

    private void bindAiEngine() {
        AiController controller = ai.controller();
        if (controller == null) {
            return;
        }
        controller.bindEngine(
                (mob, skillId) -> {
                    MobInstance instance = mobs.instance(mob);
                    if (instance != null) {
                        instance.cast(skillId);
                    }
                },
                mob -> {
                    MobInstance instance = mobs.instance(mob);
                    if (instance == null) {
                        return null;
                    }
                    if (!instance.anchorId().isEmpty()) {
                        var anchor = anchors.byId(instance.anchorId()).orElse(null);
                        if (anchor != null && anchor.location() != null) {
                            return anchor.location();
                        }
                    }
                    return instance.spawnLocation();
                });
        for (NamespacedKey id : registries.goals().ids()) {
            AiGoalType type = registries.goals().get(id).orElse(null);
            if (type != null) {
                controller.registerGoalType(id.toString(), type);
            }
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new BreedListener(this), this);
        getServer().getPluginManager().registerEvents(prompts, this);
    }

    private void registerExpansion() {
        // The one hook that cannot be reflective: providing an expansion means
        // subclassing PlaceholderExpansion. The class is named here and nowhere
        // else, so on a server without PlaceholderAPI it is never loaded.
        if (!Platform.classExists("me.clip.placeholderapi.expansion.PlaceholderExpansion")) {
            return;
        }
        try {
            new dev.bwmp.bestiary.hook.BestiaryExpansion(this).register();
            getLogger().info("Registered the %bestiary_*% PlaceholderAPI expansion.");
        } catch (RuntimeException | LinkageError exception) {
            getLogger().warning("Could not register the PlaceholderAPI expansion: " + exception);
        }
    }

    private void startTasks() {
        scheduler.runTimer(anchors::scan, 100L, settings.anchorScanPeriodTicks());
        scheduler.runTimer(spawns::scan, 120L, settings.spawnerScanPeriodTicks());
        scheduler.runTimer(() -> {
            immunity.purgeExpired();
            executor.purgeCooldowns();
            storage.purgeStaleMobState();
        }, 20L * 60, 20L * 60);
        // Mob state is written periodically as well as on unload: a crash
        // between the two costs an in-progress fight, which is the documented
        // worst case for that table.
        scheduler.runTimer(() -> mobs.instances().forEach(mobs::persist), 20L * 120, 20L * 120);
    }

    private void loadContent(LoadReport report) {
        ContentSnapshot snapshot = new ContentLoader(this).load(compiler, settings, report);
        registries.publish(snapshot);
    }

    public void reloadBestiary() {
        mainConfig.reload();
        messages.reload();
        settings = BestiarySettings.load(mainConfig);
        executor.limits(settings.limits());
        executor.resetThrottles();
        expressions.resetWarnings();

        compiler = new SkillCompiler(registries, expressions,
                entity -> mobs.instance(entity), settings.limits().maxTargets());

        LoadReport report = new LoadReport();
        loadContent(report);
        anchors.load();
        spawns.load();
        report.print(getLogger(), "Bestiary definitions");

        mobs.rebindAll();
    }

    // --- Engine -----------------------------------------------------------

    @Override
    public JavaPlugin plugin() {
        return this;
    }

    @Override
    public Logger logger() {
        return getLogger();
    }

    @Override
    public BestiaryScheduler scheduler() {
        return scheduler;
    }

    @Override
    public BestiaryRegistries registries() {
        return registries;
    }

    @Override
    public ContentSnapshot content() {
        return registries.content();
    }

    @Override
    public BestiarySettings settings() {
        return settings;
    }

    @Override
    public SkillExecutor executor() {
        return executor;
    }

    @Override
    public ExpressionEngine expressions() {
        return expressions;
    }

    @Override
    public Keys keys() {
        return keys;
    }

    @Override
    public MobManager mobs() {
        return mobs;
    }

    @Override
    public DropService drops() {
        return drops;
    }

    @Override
    public AuraService auras() {
        return auras;
    }

    @Override
    public BossbarService bossbars() {
        return bossbars;
    }

    @Override
    public ImmunityService immunity() {
        return immunity;
    }

    @Override
    public Hooks hooks() {
        return hooks;
    }

    @Override
    public AnchorService anchors() {
        return anchors;
    }

    @Override
    public SpawnService spawns() {
        return spawns;
    }

    @Override
    public DebugService debug() {
        return debug;
    }

    @Override
    public BestiaryStorage storage() {
        return storage;
    }

    @Override
    public AiBridge ai() {
        return ai;
    }

    @Override
    public MessageService messages() {
        return messages;
    }

    @Override
    public StatsView stats() {
        return stats;
    }

    public KeystoneHandle keystone() {
        return keystone;
    }

    public ChatPrompts prompts() {
        return prompts;
    }

    public SkillCompiler compiler() {
        return compiler;
    }
}
