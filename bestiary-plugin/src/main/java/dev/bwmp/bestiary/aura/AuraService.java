package dev.bwmp.bestiary.aura;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.skill.CompiledSkill;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named, timed, stackable effects attached to an entity.
 * <p>
 * Each aura owns one task scheduled at its holder, so the work runs on the
 * thread that owns the entity — the Folia-correct placement, and the one that
 * makes an aura's {@code onTick} skill safe to touch the world.
 */
public final class AuraService {

    /** One live aura on one entity. */
    public static final class Aura {

        private final AuraSpec spec;
        private final UUID holder;
        private final UUID giver;
        private volatile int stacks = 1;
        private volatile long remainingTicks;
        private volatile BestiaryTask task;

        Aura(AuraSpec spec, UUID holder, UUID giver) {
            this.spec = spec;
            this.holder = holder;
            this.giver = giver;
            this.remainingTicks = spec.durationTicks();
        }

        public AuraSpec spec() {
            return spec;
        }

        public int stacks() {
            return stacks;
        }

        public long remainingTicks() {
            return remainingTicks;
        }

        public UUID giver() {
            return giver;
        }

        public UUID holder() {
            return holder;
        }
    }

    private final Engine engine;
    private final Map<UUID, Map<String, Aura>> byEntity = new ConcurrentHashMap<>();

    public AuraService(Engine engine) {
        this.engine = engine;
    }

    public void apply(LivingEntity holder, Entity giver, AuraSpec spec) {
        if (holder == null || !holder.isValid()) {
            return;
        }
        String key = key(spec.name());
        Map<String, Aura> auras = byEntity.computeIfAbsent(holder.getUniqueId(), id -> new ConcurrentHashMap<>());

        Aura existing = auras.get(key);
        if (existing != null) {
            if (existing.stacks < spec.maxStacks()) {
                existing.stacks++;
            }
            if (spec.refreshOnStack()) {
                existing.remainingTicks = spec.durationTicks();
            }
            cast(spec.onStack(), holder, giver);
            return;
        }

        Aura aura = new Aura(spec, holder.getUniqueId(), giver == null ? null : giver.getUniqueId());
        auras.put(key, aura);
        cast(spec.onStart(), holder, giver);

        aura.task = engine.scheduler().atEntityTimer(holder, () -> tick(holder, key, aura),
                spec.intervalTicks(), spec.intervalTicks());
    }

    private void tick(LivingEntity holder, String key, Aura aura) {
        if (!holder.isValid() || holder.isDead()) {
            end(holder.getUniqueId(), key, holder, false);
            return;
        }
        if (aura.spec.cancelOnGiverDeath() && aura.giver != null) {
            Entity giver = engine.plugin().getServer().getEntity(aura.giver);
            if (giver == null || !giver.isValid()) {
                end(holder.getUniqueId(), key, holder, true);
                return;
            }
        }

        aura.remainingTicks -= aura.spec.intervalTicks();
        for (int stack = 0; stack < aura.stacks; stack++) {
            cast(aura.spec.onTick(), holder, resolveGiver(aura));
        }

        if (aura.remainingTicks <= 0L) {
            end(holder.getUniqueId(), key, holder, true);
        }
    }

    private Entity resolveGiver(Aura aura) {
        return aura.giver == null ? null : engine.plugin().getServer().getEntity(aura.giver);
    }

    public boolean has(Entity entity, String name) {
        Map<String, Aura> auras = entity == null ? null : byEntity.get(entity.getUniqueId());
        return auras != null && auras.containsKey(key(name));
    }

    public int stacks(Entity entity, String name) {
        Map<String, Aura> auras = entity == null ? null : byEntity.get(entity.getUniqueId());
        if (auras == null) {
            return 0;
        }
        Aura aura = auras.get(key(name));
        return aura == null ? 0 : aura.stacks;
    }

    public List<String> names(Entity entity) {
        Map<String, Aura> auras = entity == null ? null : byEntity.get(entity.getUniqueId());
        return auras == null ? List.of() : List.copyOf(auras.keySet());
    }

    public void remove(Entity entity, String name) {
        if (entity == null) {
            return;
        }
        end(entity.getUniqueId(), key(name), entity instanceof LivingEntity ? (LivingEntity) entity : null, true);
    }

    /** Called when an aura's holder takes damage, for {@code cancel_on_damage}. */
    public void onDamaged(Entity entity) {
        Map<String, Aura> auras = byEntity.get(entity.getUniqueId());
        if (auras == null) {
            return;
        }
        for (Map.Entry<String, Aura> entry : Map.copyOf(auras).entrySet()) {
            if (entry.getValue().spec.cancelOnDamage()) {
                end(entity.getUniqueId(), entry.getKey(),
                        entity instanceof LivingEntity ? (LivingEntity) entity : null, true);
            }
        }
    }

    public void clear(Entity entity) {
        if (entity == null) {
            return;
        }
        Map<String, Aura> auras = byEntity.remove(entity.getUniqueId());
        if (auras == null) {
            return;
        }
        for (Aura aura : auras.values()) {
            if (aura.task != null) {
                aura.task.cancel();
            }
        }
    }

    public void shutdown() {
        for (Map<String, Aura> auras : byEntity.values()) {
            for (Aura aura : auras.values()) {
                if (aura.task != null) {
                    aura.task.cancel();
                }
            }
        }
        byEntity.clear();
    }

    private void end(UUID holderId, String key, LivingEntity holder, boolean runOnEnd) {
        Map<String, Aura> auras = byEntity.get(holderId);
        if (auras == null) {
            return;
        }
        Aura aura = auras.remove(key);
        if (aura == null) {
            return;
        }
        if (aura.task != null) {
            aura.task.cancel();
        }
        if (auras.isEmpty()) {
            byEntity.remove(holderId);
        }
        if (runOnEnd && holder != null && holder.isValid()) {
            cast(aura.spec.onEnd(), holder, resolveGiver(aura));
        }
    }

    private void cast(String skillId, LivingEntity holder, Entity giver) {
        if (skillId == null || skillId.isEmpty()) {
            return;
        }
        CompiledSkill skill = engine.content().skill(skillId);
        if (skill == null) {
            return;
        }
        // The caster is whoever applied the aura when they are still around, so
        // <caster.*> in a damage-over-time still means the boss. Otherwise the
        // holder casts on itself, which keeps a lingering effect working after
        // its source is gone.
        Entity caster = giver != null && giver.isValid() ? giver : holder;
        engine.executor().cast(skill, caster, holder, holder.getLocation(),
                List.of(Target.of(holder)), 1.0d, null, null);
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
