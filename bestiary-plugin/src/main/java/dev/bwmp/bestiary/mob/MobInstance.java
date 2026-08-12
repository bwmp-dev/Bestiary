package dev.bwmp.bestiary.mob;

import dev.bwmp.bestiary.api.event.BestiaryPhaseChangeEvent;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.combat.DamageLedger;
import dev.bwmp.bestiary.combat.ThreatTable;
import dev.bwmp.bestiary.skill.CompiledCondition;
import dev.bwmp.bestiary.skill.CompiledSkill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A live instance of a mob definition, and everything that only it knows. */
public final class MobInstance implements BestiaryMob {

    private final MobManager manager;
    private final UUID uniqueId;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<CompiledMob.Binding, Long> timerElapsed = new HashMap<>();
    private final Set<Double> firedThresholds = new HashSet<>();
    private final Set<UUID> nearbyPlayers = new HashSet<>();
    private final DamageLedger ledger = new DamageLedger();
    private final Location spawnLocation;

    private volatile CompiledMob compiled;
    private volatile LivingEntity entity;
    private volatile int level;
    private volatile int phaseIndex;
    private volatile String anchorId;
    private volatile ThreatTable threat;
    private volatile BestiaryTask pollTask;
    private volatile boolean removed;
    private volatile boolean inCombat;
    private volatile long lastCombatMillis;

    MobInstance(MobManager manager, CompiledMob compiled, LivingEntity entity, int level, String anchorId) {
        this.manager = manager;
        this.compiled = compiled;
        this.entity = entity;
        this.uniqueId = entity.getUniqueId();
        this.level = Math.max(1, level);
        this.anchorId = anchorId == null ? "" : anchorId;
        this.spawnLocation = entity.getLocation();
        this.threat = compiled.definition().threat().enabled()
                ? new ThreatTable(compiled.definition().threat())
                : null;
    }

    // --- BestiaryMob ------------------------------------------------------

    @Override
    public MobDefinition definition() {
        return compiled.definition();
    }

    @Override
    public LivingEntity entity() {
        return entity;
    }

    @Override
    public UUID uniqueId() {
        return uniqueId;
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public String phase() {
        List<CompiledMob.Phase> phases = compiled.phases();
        return phases.isEmpty() ? "" : phases.get(Math.min(phaseIndex, phases.size() - 1)).definition().name();
    }

    @Override
    public Map<String, Object> variables() {
        return variables;
    }

    @Override
    public String anchorId() {
        return anchorId;
    }

    @Override
    public OptionalDouble threat(Player player) {
        ThreatTable table = threat;
        return table == null ? OptionalDouble.empty() : OptionalDouble.of(table.of(player));
    }

    @Override
    public double damageDealtBy(Player player) {
        return ledger.dealtBy(player);
    }

    @Override
    public void cast(String skillId) {
        manager.cast(this, skillId, null, null);
    }

    @Override
    public void signal(String name) {
        fire(TriggerKind.SIGNAL, name, null, null);
    }

    @Override
    public void remove(boolean permanent) {
        manager.remove(this, permanent);
    }

    // --- internals --------------------------------------------------------

    public CompiledMob compiled() {
        return compiled;
    }

    public DamageLedger ledger() {
        return ledger;
    }

    public ThreatTable threatTable() {
        return threat;
    }

    public Location spawnLocation() {
        return spawnLocation.clone();
    }

    public boolean removed() {
        return removed;
    }

    void markRemoved() {
        removed = true;
    }

    void level(int value) {
        this.level = Math.max(1, value);
    }

    void anchorId(String value) {
        this.anchorId = value == null ? "" : value;
    }

    void entity(LivingEntity replacement) {
        this.entity = replacement;
    }

    void phaseIndex(int index) {
        this.phaseIndex = Math.max(0, index);
    }

    public int phaseIndexValue() {
        return phaseIndex;
    }

    void pollTask(BestiaryTask task) {
        this.pollTask = task;
    }

    BestiaryTask pollTask() {
        return pollTask;
    }

    /**
     * Re-binds to a new definition revision in place.
     * <p>
     * Current health and variables are preserved, and attribute maxima are only
     * re-applied if the value changed — so reloading does not silently heal a
     * boss mid-fight.
     */
    void rebind(CompiledMob replacement) {
        this.compiled = replacement;
        this.threat = replacement.definition().threat().enabled()
                ? (threat != null ? threat : new ThreatTable(replacement.definition().threat()))
                : null;
        timerElapsed.clear();
        firedThresholds.clear();
    }

    public boolean inCombat() {
        return inCombat;
    }

    public void enterCombat(Entity aggressor) {
        lastCombatMillis = System.currentTimeMillis();
        if (!inCombat) {
            inCombat = true;
            fire(TriggerKind.COMBAT_ENTER, "", aggressor, null);
        }
    }

    void tickCombat() {
        if (inCombat && System.currentTimeMillis() - lastCombatMillis > 10_000L) {
            inCombat = false;
            fire(TriggerKind.COMBAT_EXIT, "", null, null);
        }
    }

    // --- triggers ---------------------------------------------------------

    /** Runs every binding of {@code kind} whose parameter matches. */
    public void fire(TriggerKind kind, String parameter, Entity trigger, Cancellable event) {
        if (removed || entity == null || !entity.isValid()) {
            return;
        }
        for (CompiledMob.Binding binding : compiled.bindings(kind)) {
            if (!binding.parameter().isEmpty() && parameter != null && !parameter.isEmpty()
                    && !binding.parameter().equalsIgnoreCase(parameter)) {
                continue;
            }
            manager.run(this, binding.skill(), trigger, event);
        }
    }

    /** The per-mob polling task. Only started when a polled trigger is declared. */
    void poll(long elapsedTicks) {
        if (removed || entity == null || !entity.isValid()) {
            return;
        }

        if (threat != null) {
            threat.decay();
            threat.prune(entity, Math.max(16.0d, compiled.definition().followRange()));
        }
        tickCombat();
        advancePhaseIfDue();

        for (CompiledMob.Binding binding : compiled.bindings(TriggerKind.TIMER)) {
            long accumulated = timerElapsed.merge(binding, elapsedTicks, Long::sum);
            if (accumulated >= binding.periodTicks()) {
                timerElapsed.put(binding, accumulated % Math.max(1L, binding.periodTicks()));
                manager.run(this, binding.skill(), null, null);
            }
        }

        if (compiled.declares(TriggerKind.TICK)) {
            for (CompiledMob.Binding binding : compiled.bindings(TriggerKind.TICK)) {
                manager.run(this, binding.skill(), null, null);
            }
        }

        if (compiled.declares(TriggerKind.PLAYER_NEAR) || compiled.declares(TriggerKind.PLAYER_LEAVE)) {
            pollProximity();
        }
    }

    private void pollProximity() {
        double range = compiled.playerNearRange();
        Set<UUID> current = new HashSet<>();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= range * range) {
                current.add(player.getUniqueId());
                if (nearbyPlayers.add(player.getUniqueId())) {
                    fire(TriggerKind.PLAYER_NEAR, "", player, null);
                }
            }
        }
        nearbyPlayers.removeIf(id -> {
            if (current.contains(id)) {
                return false;
            }
            Player player = Bukkit.getPlayer(id);
            fire(TriggerKind.PLAYER_LEAVE, "", player, null);
            return true;
        });
    }

    /** Health-threshold triggers fire once each, on the way down. */
    public void checkHealthThresholds() {
        if (removed || entity == null || !entity.isValid()) {
            return;
        }
        double max = manager.maxHealth(entity);
        if (max <= 0.0d) {
            return;
        }
        double percent = entity.getHealth() / max * 100.0d;
        for (CompiledMob.Binding binding : compiled.bindings(TriggerKind.HEALTH_THRESHOLD)) {
            if (percent <= binding.threshold() && firedThresholds.add(binding.threshold())) {
                manager.run(this, binding.skill(), null, null);
            }
        }
        advancePhaseIfDue();
    }

    /** Phases are entered in declaration order; a phase sits until its conditions hold. */
    public void advancePhaseIfDue() {
        List<CompiledMob.Phase> phases = compiled.phases();
        if (phases.isEmpty() || phaseIndex >= phases.size() - 1) {
            return;
        }
        CompiledMob.Phase phase = phases.get(phaseIndex);
        if (phase.terminal()) {
            return;
        }
        Target self = Target.of(entity);
        if (!CompiledCondition.allPass(phase.until(), manager.contextFor(this), self)) {
            return;
        }
        enterPhase(phaseIndex + 1);
    }

    public void enterPhase(int index) {
        List<CompiledMob.Phase> phases = compiled.phases();
        if (index < 0 || index >= phases.size() || index == phaseIndex) {
            return;
        }
        CompiledMob.Phase from = phases.get(phaseIndex);
        CompiledMob.Phase to = phases.get(index);
        phaseIndex = index;

        if (!from.definition().onExit().isEmpty()) {
            manager.cast(this, from.definition().onExit(), null, null);
        }
        if (!to.definition().onEnter().isEmpty()) {
            manager.cast(this, to.definition().onEnter(), null, null);
        }
        fire(TriggerKind.PHASE, to.definition().name(), null, null);
        Bukkit.getPluginManager().callEvent(
                new BestiaryPhaseChangeEvent(this, from.definition().name(), to.definition().name()));
        manager.onPhaseChanged(this);
    }

    /** Skills a mob can be told to cast by id, resolved against the content snapshot. */
    CompiledSkill skill(String id) {
        return manager.skill(id);
    }

    @Override
    public String toString() {
        return definition().id() + " (" + uniqueId + ")";
    }
}
