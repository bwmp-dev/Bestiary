package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.skill.ExecutionLimits;
import dev.bwmp.keystone.config.ManagedConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** {@code config.yml}, parsed once per load into an immutable value. */
public final class BestiarySettings {

    private final ExecutionLimits limits;
    private final String storageType;
    private final String storageFile;
    private final String storageHost;
    private final int storagePort;
    private final String storageDatabase;
    private final String storageUser;
    private final String storagePassword;

    private final Map<EntityType, NamespacedKey> anchorTypes;
    private final String anchorTagPrefix;
    private final long anchorRespawnCooldownTicks;
    private final double anchorActivationRange;
    private final long anchorScanPeriodTicks;

    private final int particleBatchPerPlayer;
    private final double particleViewDistance;
    private final long spawnerScanPeriodTicks;
    private final int randomSpawnBudgetPerWorld;

    private final boolean suppressMcmmoByDefault;
    private final boolean suppressJobsByDefault;
    private final boolean adoptCitizens;
    private final boolean debug;
    private final boolean builtinContent;

    private BestiarySettings(Builder builder) {
        this.limits = builder.limits;
        this.storageType = builder.storageType;
        this.storageFile = builder.storageFile;
        this.storageHost = builder.storageHost;
        this.storagePort = builder.storagePort;
        this.storageDatabase = builder.storageDatabase;
        this.storageUser = builder.storageUser;
        this.storagePassword = builder.storagePassword;
        this.anchorTypes = Map.copyOf(builder.anchorTypes);
        this.anchorTagPrefix = builder.anchorTagPrefix;
        this.anchorRespawnCooldownTicks = builder.anchorRespawnCooldownTicks;
        this.anchorActivationRange = builder.anchorActivationRange;
        this.anchorScanPeriodTicks = builder.anchorScanPeriodTicks;
        this.particleBatchPerPlayer = builder.particleBatchPerPlayer;
        this.particleViewDistance = builder.particleViewDistance;
        this.spawnerScanPeriodTicks = builder.spawnerScanPeriodTicks;
        this.randomSpawnBudgetPerWorld = builder.randomSpawnBudgetPerWorld;
        this.suppressMcmmoByDefault = builder.suppressMcmmoByDefault;
        this.suppressJobsByDefault = builder.suppressJobsByDefault;
        this.adoptCitizens = builder.adoptCitizens;
        this.debug = builder.debug;
        this.builtinContent = builder.builtinContent;
    }

    public static BestiarySettings load(ManagedConfig config) {
        Builder builder = new Builder();
        builder.limits = new ExecutionLimits(
                config.integer("limits.max-depth", 32),
                config.integer("limits.max-mechanics-per-execution", 4000),
                config.integer("limits.max-targets", 64),
                config.decimal("limits.tick-budget-ms", 5.0d));

        builder.storageType = config.string("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        builder.storageFile = config.string("storage.file", "bestiary.db");
        builder.storageHost = config.string("storage.host", "localhost");
        builder.storagePort = config.integer("storage.port", 3306);
        builder.storageDatabase = config.string("storage.database", "bestiary");
        builder.storageUser = config.string("storage.user", "root");
        builder.storagePassword = config.string("storage.password", "");

        ConfigurationSection anchors = config.section("anchors.types");
        if (anchors != null) {
            for (String key : anchors.getKeys(false)) {
                EntityType type = parseEntityType(key);
                NamespacedKey mob = parseKey(anchors.getString(key, ""));
                if (type != null && mob != null) {
                    builder.anchorTypes.put(type, mob);
                }
            }
        }
        builder.anchorTagPrefix = config.string("anchors.tag-prefix", "bestiary:");
        builder.anchorRespawnCooldownTicks = Durations.parseTicks(
                config.string("anchors.respawn-cooldown", "30m"), 20L * 60 * 30);
        builder.anchorActivationRange = config.decimal("anchors.activation-range", 64.0d);
        builder.anchorScanPeriodTicks = Math.max(20L, config.longValue("anchors.scan-period-ticks", 20L));

        builder.particleBatchPerPlayer = config.integer("performance.particles-per-player-per-tick", 400);
        builder.particleViewDistance = config.decimal("performance.particle-view-distance", 48.0d);
        builder.spawnerScanPeriodTicks = Math.max(20L, config.longValue("performance.spawner-scan-period-ticks", 40L));
        builder.randomSpawnBudgetPerWorld = config.integer("performance.random-spawn-budget-per-world", 40);

        builder.suppressMcmmoByDefault = config.bool("hooks.suppress-mcmmo-xp", true);
        builder.suppressJobsByDefault = config.bool("hooks.suppress-jobs-xp", true);
        builder.adoptCitizens = config.bool("hooks.target-citizens-npcs", false);
        builder.debug = config.bool("debug", false);
        builder.builtinContent = config.bool("builtin-content", true);
        return new BestiarySettings(builder);
    }

    private static EntityType parseEntityType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static NamespacedKey parseKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        int colon = text.indexOf(':');
        if (colon < 0) {
            return new NamespacedKey("bestiary", text);
        }
        return new NamespacedKey(text.substring(0, colon), text.substring(colon + 1));
    }

    public ExecutionLimits limits() {
        return limits;
    }

    public String storageType() {
        return storageType;
    }

    public String storageFile() {
        return storageFile;
    }

    public String storageHost() {
        return storageHost;
    }

    public int storagePort() {
        return storagePort;
    }

    public String storageDatabase() {
        return storageDatabase;
    }

    public String storageUser() {
        return storageUser;
    }

    public String storagePassword() {
        return storagePassword;
    }

    /**
     * Entity type to mob id.
     * <p>
     * Identity comes from the entity type rather than from NBT because of a
     * Terra bug: a TerraScript {@code entity()} call carrying NBT most likely
     * spawns nothing at all. The adopter still reads
     * tags first, so this stays a fallback rather than the only path.
     */
    public Map<EntityType, NamespacedKey> anchorTypes() {
        return anchorTypes;
    }

    public String anchorTagPrefix() {
        return anchorTagPrefix;
    }

    public long anchorRespawnCooldownTicks() {
        return anchorRespawnCooldownTicks;
    }

    public double anchorActivationRange() {
        return anchorActivationRange;
    }

    public long anchorScanPeriodTicks() {
        return anchorScanPeriodTicks;
    }

    public int particleBatchPerPlayer() {
        return particleBatchPerPlayer;
    }

    public double particleViewDistance() {
        return particleViewDistance;
    }

    public long spawnerScanPeriodTicks() {
        return spawnerScanPeriodTicks;
    }

    public int randomSpawnBudgetPerWorld() {
        return randomSpawnBudgetPerWorld;
    }

    public boolean suppressMcmmoByDefault() {
        return suppressMcmmoByDefault;
    }

    public boolean suppressJobsByDefault() {
        return suppressJobsByDefault;
    }

    public boolean adoptCitizens() {
        return adoptCitizens;
    }

    public boolean debug() {
        return debug;
    }

    public boolean builtinContent() {
        return builtinContent;
    }

    private static final class Builder {
        private ExecutionLimits limits = ExecutionLimits.DEFAULT;
        private String storageType = "sqlite";
        private String storageFile = "bestiary.db";
        private String storageHost = "localhost";
        private int storagePort = 3306;
        private String storageDatabase = "bestiary";
        private String storageUser = "root";
        private String storagePassword = "";
        private final Map<EntityType, NamespacedKey> anchorTypes = new LinkedHashMap<>();
        private String anchorTagPrefix = "bestiary:";
        private long anchorRespawnCooldownTicks = 20L * 60 * 30;
        private double anchorActivationRange = 64.0d;
        private long anchorScanPeriodTicks = 20L;
        private int particleBatchPerPlayer = 400;
        private double particleViewDistance = 48.0d;
        private long spawnerScanPeriodTicks = 40L;
        private int randomSpawnBudgetPerWorld = 40;
        private boolean suppressMcmmoByDefault = true;
        private boolean suppressJobsByDefault = true;
        private boolean adoptCitizens;
        private boolean debug;
        private boolean builtinContent = true;
    }
}
