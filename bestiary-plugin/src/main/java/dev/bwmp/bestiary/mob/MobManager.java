package dev.bwmp.bestiary.mob;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.event.BestiaryMobSpawnEvent;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.mob.MobOptions;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.expression.Attributes;
import dev.bwmp.bestiary.skill.CompiledSkill;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.bestiary.util.Json;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns, adopts, re-binds and forgets Bestiary mobs.
 * <p>
 * The only code that reads {@code bestiary:id}, so the PDC schema has exactly
 * one consumer.
 */
public final class MobManager {

    /** What vanilla scales a baby animal's own model to. */
    private static final double BABY_MODEL_SCALE = 0.5d;
    private static final long GROWTH_CHECK_TICKS = 100L;

    private final Engine engine;
    private final Map<UUID, MobInstance> active = new ConcurrentHashMap<>();

    public MobManager(Engine engine) {
        this.engine = engine;
    }

    // --- identity ---------------------------------------------------------

    public Optional<BestiaryMob> resolve(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        MobInstance instance = active.get(entity.getUniqueId());
        if (instance != null) {
            return Optional.of(instance);
        }
        // Not in the live map: either the chunk-load adopter has not run yet,
        // or this is a mob from before a restart. Adopting here rather than
        // returning empty is what makes a lookup from an event handler correct
        // regardless of ordering.
        return Optional.ofNullable(adopt(entity));
    }

    public MobInstance instance(Entity entity) {
        return entity == null ? null : active.get(entity.getUniqueId());
    }

    public Collection<BestiaryMob> activeMobs() {
        return List.copyOf(active.values());
    }

    public Collection<MobInstance> instances() {
        return List.copyOf(active.values());
    }

    /** The mob id written on an entity, or null. */
    public NamespacedKey idOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer()
                .get(engine.keys().id, PersistentDataType.STRING);
        return raw == null ? null : parseKey(raw);
    }

    public static NamespacedKey parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = text.indexOf(':');
        try {
            return colon < 0
                    ? new NamespacedKey("bestiary", text)
                    : new NamespacedKey(text.substring(0, colon), text.substring(colon + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // --- spawning ---------------------------------------------------------

    public Optional<BestiaryMob> spawn(NamespacedKey id, Location location, int level) {
        return spawn(id, location, level, "");
    }

    public Optional<BestiaryMob> spawn(NamespacedKey id, Location location, int level, String anchorId) {
        CompiledMob compiled = engine.content().compiledMob(id);
        if (compiled == null) {
            return Optional.empty();
        }
        return spawn(compiled, location, level, anchorId);
    }

    public Optional<BestiaryMob> spawn(CompiledMob compiled, Location location, int level, String anchorId) {
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        MobDefinition definition = compiled.definition();
        Entity spawned;
        try {
            spawned = world.spawnEntity(location, definition.type());
        } catch (IllegalArgumentException exception) {
            engine.logger().warning("Cannot spawn " + definition.id() + ": " + exception.getMessage());
            return Optional.empty();
        }
        if (!(spawned instanceof LivingEntity)) {
            spawned.remove();
            engine.logger().warning("Mob " + definition.id() + " uses non-living type "
                    + definition.type() + "; skipped.");
            return Optional.empty();
        }

        LivingEntity living = (LivingEntity) spawned;
        int effectiveLevel = level > 0 ? level : definition.defaultLevel();
        writeIdentity(living, definition, effectiveLevel, anchorId);
        applyDefinition(living, compiled, effectiveLevel, true);

        MobInstance instance = new MobInstance(this, compiled, living, effectiveLevel, anchorId);
        active.put(living.getUniqueId(), instance);
        startPolling(instance);
        engine.bossbars().attach(instance);
        engine.ai().apply(living, definition.ai());

        BestiaryMobSpawnEvent event = new BestiaryMobSpawnEvent(instance);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            remove(instance, true);
            return Optional.empty();
        }

        instance.fire(TriggerKind.FIRST_SPAWN, "", null, null);
        instance.fire(TriggerKind.SPAWN, "", null, null);
        return Optional.of(instance);
    }

    private void writeIdentity(LivingEntity entity, MobDefinition definition, int level, String anchorId) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(engine.keys().id, PersistentDataType.STRING, definition.id().toString());
        data.set(engine.keys().revision, PersistentDataType.INTEGER, definition.revision());
        data.set(engine.keys().level, PersistentDataType.INTEGER, level);
        if (anchorId != null && !anchorId.isEmpty()) {
            data.set(engine.keys().anchor, PersistentDataType.STRING, anchorId);
        }
        Location location = entity.getLocation();
        data.set(engine.keys().spawnLocation, PersistentDataType.STRING,
                location.getWorld().getName() + "," + location.getX() + ","
                        + location.getY() + "," + location.getZ());
    }

    /**
     * Applies attributes, options, equipment and name.
     *
     * @param fresh false when re-binding a mob that has been alive across a
     *              reload, in which case attribute maxima are only touched when
     *              the configured value actually changed — otherwise reloading
     *              silently heals a boss mid-fight
     */
    public void applyDefinition(LivingEntity entity, CompiledMob compiled, int level, boolean fresh) {
        MobDefinition definition = compiled.definition();
        double scale = levelScale(definition, level);

        applyAttribute(entity, "GENERIC_MAX_HEALTH", definition.health() * scale, fresh);
        applyAttribute(entity, "GENERIC_ATTACK_DAMAGE", definition.damage() * scale, true);
        applyAttribute(entity, "GENERIC_ARMOR", definition.armor(), true);
        applyAttribute(entity, "GENERIC_ARMOR_TOUGHNESS", definition.armorToughness(), true);
        applyAttribute(entity, "GENERIC_KNOCKBACK_RESISTANCE", definition.knockbackResistance(), true);
        applyAttribute(entity, "GENERIC_MOVEMENT_SPEED", definition.movementSpeed(), true);
        applyAttribute(entity, "GENERIC_FOLLOW_RANGE", definition.followRange(), true);
        applyAttribute(entity, "GENERIC_SCALE", definition.scale(), true);

        if (fresh && definition.health() > 0) {
            entity.setHealth(Math.min(definition.health() * scale, maxHealth(entity)));
        }

        MobOptions options = definition.options();
        // setRemoveWhenFarAway is the PersistenceRequired flag. Entity#setPersistent
        // is a DIFFERENT flag, controls chunk saving, defaults to true, and
        // setting it false would delete the mob permanently on unload. It is
        // deliberately left alone.
        entity.setRemoveWhenFarAway(options.despawn());
        entity.setSilent(options.silent());
        entity.setGlowing(options.glowing());
        entity.setInvulnerable(options.invulnerable());
        entity.setGravity(options.gravity());
        entity.setCollidable(options.collidable());
        entity.setCanPickupItems(false);
        if (entity instanceof Mob) {
            ((Mob) entity).setAware(options.ai());
        }

        String name = compiled.renderedName();
        if (!name.isEmpty()) {
            entity.setCustomName(name);
            entity.setCustomNameVisible(options.alwaysShowName());
        }

        applyEquipment(entity, definition);
        engine.hooks().modelEngine().applyModel(entity, definition.modelEngineModel());
        applyBabyModelScale(entity, definition);
    }

    /**
     * Matches the model to a baby's size, and follows it back up when it grows.
     * <p>
     * Vanilla halves a baby animal's own model; ModelEngine draws its own and
     * knows nothing about age, so without this a bred baby wears a full-size
     * model. There is no Bukkit event for growing up, hence the watcher — it
     * only exists while a baby does, and cancels itself the moment it matures.
     */
    private void applyBabyModelScale(LivingEntity entity, MobDefinition definition) {
        if (definition.modelEngineModel().isEmpty() || !(entity instanceof Ageable)) {
            return;
        }
        Ageable ageable = (Ageable) entity;
        if (ageable.isAdult()) {
            return;
        }

        engine.hooks().modelEngine().scaleModel(entity, BABY_MODEL_SCALE);

        BestiaryTask[] watcher = new BestiaryTask[1];
        watcher[0] = engine.scheduler().atEntityTimer(entity, () -> {
            if (watcher[0] == null) {
                return;
            }
            if (!entity.isValid()) {
                watcher[0].cancel();
                return;
            }
            if (ageable.isAdult()) {
                engine.hooks().modelEngine().scaleModel(entity, 1.0d);
                watcher[0].cancel();
            }
        }, GROWTH_CHECK_TICKS, GROWTH_CHECK_TICKS);
    }

    private void applyEquipment(LivingEntity entity, MobDefinition definition) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        if (definition.options().preventRandomEquipment() && definition.equipment().isEmpty()) {
            // A vanilla zombie spawns holding whatever the difficulty rolled.
            // For a defined mob that is never intentional.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                setSlot(equipment, slot, null, 0.0f);
            }
            return;
        }
        for (Map.Entry<EquipmentSlot, String> entry : definition.equipment().entrySet()) {
            ItemStack stack = engine.hooks().sigil().resolveItem(entry.getValue());
            float dropChance = definition.equipmentDropChance().getOrDefault(entry.getKey(), 0.0f);
            setSlot(equipment, entry.getKey(), stack, dropChance);
        }
    }

    private void setSlot(EntityEquipment equipment, EquipmentSlot slot, ItemStack stack, float dropChance) {
        try {
            switch (slot) {
                case HEAD:
                    equipment.setHelmet(stack);
                    equipment.setHelmetDropChance(dropChance);
                    break;
                case CHEST:
                    equipment.setChestplate(stack);
                    equipment.setChestplateDropChance(dropChance);
                    break;
                case LEGS:
                    equipment.setLeggings(stack);
                    equipment.setLeggingsDropChance(dropChance);
                    break;
                case FEET:
                    equipment.setBoots(stack);
                    equipment.setBootsDropChance(dropChance);
                    break;
                case HAND:
                    equipment.setItemInMainHand(stack);
                    equipment.setItemInMainHandDropChance(dropChance);
                    break;
                case OFF_HAND:
                    equipment.setItemInOffHand(stack);
                    equipment.setItemInOffHandDropChance(dropChance);
                    break;
                default:
                    break;
            }
        } catch (UnsupportedOperationException ignored) {
            // Some entity types have no equipment slots at all; the definition
            // asking for one is a config problem, not a crash.
        }
    }

    private void applyAttribute(LivingEntity entity, String legacyName, double value, boolean force) {
        if (value < 0) {
            return;
        }
        Attribute attribute = Attributes.byLegacyName(legacyName);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (!force && Math.abs(instance.getBaseValue() - value) < 1.0e-6d) {
            return;
        }
        instance.setBaseValue(value);
    }

    public double maxHealth(LivingEntity entity) {
        Attribute attribute = Attributes.byLegacyName("GENERIC_MAX_HEALTH");
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        return instance == null ? entity.getHealth() : instance.getValue();
    }

    private double levelScale(MobDefinition definition, int level) {
        if (definition.levelModifier().isEmpty() || level <= 1) {
            return 1.0d;
        }
        try {
            String substituted = definition.levelModifier().replace("<level>", Integer.toString(level));
            return Math.max(0.01d, dev.bwmp.bestiary.expression.Arithmetic.evaluate(substituted));
        } catch (NumberFormatException exception) {
            engine.logger().warning("Level modifier for " + definition.id() + " is not an expression: "
                    + definition.levelModifier());
            return 1.0d;
        }
    }

    // --- adoption ---------------------------------------------------------

    /**
     * Re-adopts a mob found in a loaded chunk: skill tasks restarted, goals
     * re-registered, bossbar rebuilt. A restart mid-fight resumes correctly
     * because none of that state lives only in memory.
     */
    public MobInstance adopt(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isValid()) {
            return null;
        }
        MobInstance existing = active.get(entity.getUniqueId());
        if (existing != null) {
            return existing;
        }
        NamespacedKey id = idOf(entity);
        if (id == null) {
            return null;
        }
        CompiledMob compiled = engine.content().compiledMob(id);
        if (compiled == null) {
            return null;
        }

        LivingEntity living = (LivingEntity) entity;
        PersistentDataContainer data = living.getPersistentDataContainer();
        Integer storedRevision = data.get(engine.keys().revision, PersistentDataType.INTEGER);
        Integer storedLevel = data.get(engine.keys().level, PersistentDataType.INTEGER);
        String anchor = data.get(engine.keys().anchor, PersistentDataType.STRING);
        String variables = data.get(engine.keys().variables, PersistentDataType.STRING);
        String phase = data.get(engine.keys().phase, PersistentDataType.STRING);

        MobInstance instance = new MobInstance(this, compiled, living,
                storedLevel == null ? compiled.definition().defaultLevel() : storedLevel,
                anchor == null ? "" : anchor);
        if (variables != null) {
            instance.variables().putAll(Json.read(variables));
        }
        if (phase != null) {
            List<CompiledMob.Phase> phases = compiled.phases();
            for (int index = 0; index < phases.size(); index++) {
                if (phases.get(index).definition().name().equalsIgnoreCase(phase)) {
                    instance.phaseIndex(index);
                    break;
                }
            }
        }

        active.put(living.getUniqueId(), instance);

        boolean stale = storedRevision == null || storedRevision != compiled.definition().revision();
        if (stale) {
            applyDefinition(living, compiled, instance.level(), false);
            data.set(engine.keys().revision, PersistentDataType.INTEGER, compiled.definition().revision());
        }

        startPolling(instance);
        engine.bossbars().attach(instance);
        engine.ai().apply(living, compiled.definition().ai());
        engine.storage().restoreMobState(instance);
        return instance;
    }

    // --- lifecycle --------------------------------------------------------

    private void startPolling(MobInstance instance) {
        long period = instance.compiled().pollPeriodTicks();
        if (period <= 0L) {
            return;
        }
        BestiaryTask task = engine.scheduler().atEntityTimer(instance.entity(),
                () -> {
                    if (instance.removed() || !instance.entity().isValid()) {
                        BestiaryTask running = instance.pollTask();
                        if (running != null) {
                            running.cancel();
                        }
                        return;
                    }
                    instance.poll(period);
                }, period, period);
        instance.pollTask(task);
    }

    public void persist(MobInstance instance) {
        LivingEntity entity = instance.entity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(engine.keys().variables, PersistentDataType.STRING, Json.write(instance.variables()));
        data.set(engine.keys().phase, PersistentDataType.STRING, instance.phase());
        data.set(engine.keys().level, PersistentDataType.INTEGER, instance.level());
        engine.storage().saveMobState(instance);
    }

    /** Called on chunk unload: tasks cancelled, threat persisted, entity left alone. */
    public void unload(Entity entity) {
        MobInstance instance = active.remove(entity.getUniqueId());
        if (instance == null) {
            return;
        }
        persist(instance);
        BestiaryTask task = instance.pollTask();
        if (task != null) {
            task.cancel();
        }
        engine.bossbars().detach(instance);
    }

    public void remove(MobInstance instance, boolean permanent) {
        active.remove(instance.uniqueId());
        instance.markRemoved();
        BestiaryTask task = instance.pollTask();
        if (task != null) {
            task.cancel();
        }
        engine.bossbars().detach(instance);
        engine.auras().clear(instance.entity());
        engine.immunity().forget(instance.entity());
        if (permanent && !instance.anchorId().isEmpty()) {
            engine.anchors().forget(instance.anchorId());
        }
        engine.storage().deleteMobState(instance.uniqueId());
        LivingEntity entity = instance.entity();
        if (entity != null && entity.isValid()) {
            instance.fire(TriggerKind.DESPAWN, "", null, null);
            entity.remove();
        }
    }

    /** Called on death, after drops. Keeps the entity, drops the bookkeeping. */
    public void forget(MobInstance instance) {
        active.remove(instance.uniqueId());
        instance.markRemoved();
        BestiaryTask task = instance.pollTask();
        if (task != null) {
            task.cancel();
        }
        engine.bossbars().detach(instance);
        engine.auras().clear(instance.entity());
        engine.immunity().forget(instance.entity());
        engine.storage().deleteMobState(instance.uniqueId());
    }

    public void shutdown() {
        for (MobInstance instance : List.copyOf(active.values())) {
            persist(instance);
            BestiaryTask task = instance.pollTask();
            if (task != null) {
                task.cancel();
            }
            engine.bossbars().detach(instance);
        }
        active.clear();
    }

    /** Re-binds every live mob after a reload, preserving health and variables. */
    public void rebindAll() {
        for (MobInstance instance : List.copyOf(active.values())) {
            CompiledMob replacement = engine.content().compiledMob(instance.definition().id());
            if (replacement == null) {
                remove(instance, false);
                continue;
            }
            if (replacement.definition().revision() == instance.definition().revision()) {
                continue;
            }
            BestiaryTask task = instance.pollTask();
            if (task != null) {
                task.cancel();
            }
            instance.rebind(replacement);
            applyDefinition(instance.entity(), replacement, instance.level(), false);
            startPolling(instance);
            engine.bossbars().detach(instance);
            engine.bossbars().attach(instance);
            engine.ai().apply(instance.entity(), replacement.definition().ai());
        }
    }

    // --- skills -----------------------------------------------------------

    public CompiledSkill skill(String id) {
        return engine.content().skill(id);
    }

    public void run(MobInstance instance, CompiledSkill skill, Entity trigger, Cancellable event) {
        var tracer = engine.debug().tracerFor(instance);
        long started = tracer == null ? 0L : System.nanoTime();
        engine.executor().cast(skill, instance.entity(), trigger, instance.entity().getLocation(),
                List.of(), 1.0d, event, tracer);
        if (tracer != null) {
            engine.debug().record(instance, skill.id(), System.nanoTime() - started);
        }
    }

    public void cast(MobInstance instance, String skillId, Entity trigger, Cancellable event) {
        CompiledSkill skill = skill(skillId);
        if (skill == null) {
            engine.logger().warning("Mob " + instance.definition().id()
                    + " asked for unknown skill '" + skillId + "'");
            return;
        }
        run(instance, skill, trigger, event);
    }

    /** A throwaway context for evaluating a mob's own phase conditions. */
    public SkillContext contextFor(MobInstance instance) {
        return engine.executor()
                .newExecution(instance.entity(), null, instance.entity().getLocation(), null)
                .contextForConditions();
    }

    public void onPhaseChanged(MobInstance instance) {
        engine.bossbars().refresh(instance);
        persist(instance);
    }

    public Target targetOf(MobInstance instance) {
        return Target.of(instance.entity());
    }

    /** Every live mob of one definition, for {@code /bestiary kill}. */
    public List<MobInstance> byDefinition(NamespacedKey id) {
        List<MobInstance> matches = new ArrayList<>();
        for (MobInstance instance : active.values()) {
            if (instance.definition().id().equals(id)) {
                matches.add(instance);
            }
        }
        return matches;
    }

    public boolean isAnchorType(EntityType type) {
        return engine.settings().anchorTypes().containsKey(type);
    }

    public Engine engine() {
        return engine;
    }
}
