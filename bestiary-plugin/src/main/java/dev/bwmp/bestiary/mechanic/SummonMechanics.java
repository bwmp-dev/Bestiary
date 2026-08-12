package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Summoning, ownership and removal. */
public final class SummonMechanics {

    private SummonMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("summon", Mechanics.type(
                MechanicMeta.builder("summon")
                        .description("Spawns Bestiary mobs at the target.")
                        .requires(TargetKind.ANY)
                        .required("type", "the mob id", "t", "mob", "m")
                        .param("amount", "how many", "1", "a", "count")
                        .param("level", "level to spawn at; 0 inherits the caster's", "0", "l")
                        .param("radius", "scatter radius around the target", "0", "r", "spread")
                        .param("inherit_target", "give the summons the caster's target", "true")
                        .param("owner", "record the caster as owner, so @children finds them", "true")
                        .build(),
                config -> {
                    NamespacedKey mob = key(config.raw("type", ""));
                    Expression amount = config.number("amount", 1);
                    Expression level = config.number("level", 0);
                    Expression radius = config.number("radius", 0);
                    boolean inheritTarget = config.bool("inherit_target", true);
                    boolean recordOwner = config.bool("owner", true);
                    return (context, target) -> {
                        int count = Math.max(1, Math.min(64, amount.asInt(context, target)));
                        double spread = radius.asDouble(context, target);
                        int spawnLevel = level.asInt(context, target);
                        if (spawnLevel <= 0) {
                            MobInstance caster = StateMechanics.instanceOf(engine, context.caster());
                            spawnLevel = caster == null ? 1 : caster.level();
                        }

                        boolean any = false;
                        for (int index = 0; index < count; index++) {
                            if (!context.charge(1)) {
                                break;
                            }
                            Location where = spread <= 0
                                    ? target.location()
                                    : Shapes.randomNear(target.location(), spread, true);
                            Optional<BestiaryMob> spawned = engine.mobs().spawn(mob, where, spawnLevel);
                            if (spawned.isEmpty()) {
                                continue;
                            }
                            any = true;
                            LivingEntity entity = spawned.get().entity();
                            if (recordOwner) {
                                entity.getPersistentDataContainer().set(engine.keys().owner,
                                        PersistentDataType.STRING, context.caster().getUniqueId().toString());
                            }
                            if (inheritTarget && entity instanceof org.bukkit.entity.Mob
                                    && context.caster() instanceof org.bukkit.entity.Mob) {
                                ((org.bukkit.entity.Mob) entity).setTarget(
                                        ((org.bukkit.entity.Mob) context.caster()).getTarget());
                            }
                            MobInstance summoner = StateMechanics.instanceOf(engine, context.caster());
                            if (summoner != null) {
                                summoner.fire(TriggerKind.SUMMON, "", entity, null);
                            }
                        }
                        return Mechanics.result(any);
                    };
                }));

        into.put("summon_passenger", Mechanics.type(
                MechanicMeta.builder("summon_passenger")
                        .description("Spawns a mob riding the target.")
                        .requires(TargetKind.ENTITY)
                        .required("type", "the mob id", "t", "mob", "m")
                        .build(),
                config -> {
                    NamespacedKey mob = key(config.raw("type", ""));
                    return (context, target) -> {
                        Entity carrier = target.entity();
                        if (carrier == null) {
                            return MechanicResult.FAIL;
                        }
                        Optional<BestiaryMob> spawned =
                                engine.mobs().spawn(mob, carrier.getLocation(), 0);
                        if (spawned.isEmpty()) {
                            return MechanicResult.FAIL;
                        }
                        return Mechanics.result(carrier.addPassenger(spawned.get().entity()));
                    };
                }));

        into.put("summon_area", Mechanics.type(
                MechanicMeta.builder("summon_area")
                        .description("Spawns mobs on the points of a shape.")
                        .requires(TargetKind.ANY)
                        .required("type", "the mob id", "t", "mob", "m")
                        .param("shape", "a nested shape block", "")
                        .param("level", "level to spawn at", "0", "l")
                        .build(),
                config -> {
                    NamespacedKey mob = key(config.raw("type", ""));
                    dev.bwmp.bestiary.util.ShapeSpec shape =
                            dev.bwmp.bestiary.util.ShapeSpec.of(config.section("shape"));
                    Expression level = config.number("level", 0);
                    return (context, target) -> {
                        List<Location> points = shape.points(context, target, target.location());
                        boolean any = false;
                        for (Location point : points) {
                            if (!context.charge(1)) {
                                break;
                            }
                            if (engine.mobs().spawn(mob, point, level.asInt(context, target)).isPresent()) {
                                any = true;
                            }
                        }
                        return Mechanics.result(any);
                    };
                }));

        into.put("totem", Mechanics.type(
                MechanicMeta.builder("totem")
                        .description("Summons a mob that removes itself after a set time.")
                        .requires(TargetKind.ANY)
                        .required("type", "the mob id", "t", "mob", "m")
                        .param("duration", "how long it lasts", "10s", "d")
                        .param("level", "level to spawn at", "0", "l")
                        .build(),
                config -> {
                    NamespacedKey mob = key(config.raw("type", ""));
                    long duration = config.ticks("duration", 200L);
                    Expression level = config.number("level", 0);
                    return (context, target) -> {
                        Optional<BestiaryMob> spawned =
                                engine.mobs().spawn(mob, target.location(), level.asInt(context, target));
                        if (spawned.isEmpty()) {
                            return MechanicResult.FAIL;
                        }
                        BestiaryMob summoned = spawned.get();
                        engine.scheduler().atEntityLater(summoned.entity(),
                                () -> summoned.remove(false), duration);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("doppelganger", Mechanics.type(
                MechanicMeta.builder("doppelganger")
                        .description("Summons a copy of the caster's definition at the target.")
                        .requires(TargetKind.ANY)
                        .param("level", "level to spawn at; 0 inherits", "0", "l")
                        .param("health_percent", "starting health as a share of maximum", "100", "hp")
                        .build(),
                config -> {
                    Expression level = config.number("level", 0);
                    Expression healthPercent = config.number("health_percent", 100);
                    return (context, target) -> {
                        MobInstance caster = StateMechanics.instanceOf(engine, context.caster());
                        if (caster == null) {
                            return MechanicResult.FAIL;
                        }
                        int spawnLevel = level.asInt(context, target);
                        Optional<BestiaryMob> copy = engine.mobs().spawn(caster.definition().id(),
                                target.location(), spawnLevel <= 0 ? caster.level() : spawnLevel);
                        if (copy.isEmpty()) {
                            return MechanicResult.FAIL;
                        }
                        LivingEntity entity = copy.get().entity();
                        double share = Math.max(1.0d, Math.min(100.0d,
                                healthPercent.asDouble(context, target))) / 100.0d;
                        entity.setHealth(Math.max(1.0d, DamageMechanics.maxHealth(entity) * share));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("remove", Mechanics.type(
                MechanicMeta.builder("remove")
                        .description("Removes the target entity outright.")
                        .requires(TargetKind.ENTITY)
                        .param("permanent", "also clear its anchor's memory of it", "false")
                        .build(),
                config -> {
                    boolean permanent = config.bool("permanent", false);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        MobInstance instance = engine.mobs().instance(entity);
                        if (instance != null) {
                            instance.remove(permanent);
                        } else {
                            entity.remove();
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("despawn", Mechanics.type(
                MechanicMeta.builder("despawn")
                        .description("Removes the caster. Its anchor will bring it back on the next visit.")
                        .requires(TargetKind.NONE)
                        .build(),
                config -> (context, target) -> {
                    MobInstance instance = StateMechanics.instanceOf(engine, context.caster());
                    if (instance == null) {
                        context.caster().remove();
                        return MechanicResult.HALT;
                    }
                    instance.remove(false);
                    return MechanicResult.HALT;
                }));

        into.put("set_owner", Mechanics.type(
                MechanicMeta.builder("set_owner")
                        .description("Records the caster as the target's owner.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    Entity entity = target.entity();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    entity.getPersistentDataContainer().set(engine.keys().owner,
                            PersistentDataType.STRING, context.caster().getUniqueId().toString());
                    return MechanicResult.SUCCESS;
                }));

        into.put("set_parent", Mechanics.type(
                MechanicMeta.builder("set_parent")
                        .description("Records the target as the caster's owner. The mirror of set_owner.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    Entity entity = target.entity();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    context.caster().getPersistentDataContainer().set(engine.keys().owner,
                            PersistentDataType.STRING, entity.getUniqueId().toString());
                    return MechanicResult.SUCCESS;
                }));
    }

    static NamespacedKey key(String written) {
        NamespacedKey key = dev.bwmp.bestiary.mob.MobManager.parseKey(written);
        if (key == null) {
            throw new IllegalArgumentException("'" + written + "' is not a mob id");
        }
        return key;
    }
}
