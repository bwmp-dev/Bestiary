package dev.bwmp.bestiary.listener;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.event.BestiaryMobDeathEvent;
import dev.bwmp.bestiary.api.mob.DamageModifiers;
import dev.bwmp.bestiary.api.skill.TriggerKind;
import dev.bwmp.bestiary.mechanic.BlockMechanics;
import dev.bwmp.bestiary.mechanic.PlayerMechanics;
import dev.bwmp.bestiary.mob.MobInstance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;

/**
 * Turns Bukkit events into Bestiary triggers.
 * <p>
 * Everything here is cheap on the common path: the first thing each handler
 * does is ask {@code MobManager} whether the entity is a Bestiary mob at all,
 * which is one map lookup, and returns if it is not.
 */
public final class MobListener implements Listener {

    private final Engine engine;

    public MobListener(Engine engine) {
        this.engine = engine;
    }

    // --- damage -----------------------------------------------------------

    /**
     * Damage modifiers are applied at LOW so other plugins see the adjusted
     * number, and immunity is checked here rather than inside each mechanic.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        MobInstance instance = engine.mobs().instance(event.getEntity());
        if (instance == null) {
            return;
        }
        DamageModifiers modifiers = instance.definition().damageModifiers();
        if (modifiers.isIdentity()) {
            return;
        }

        boolean projectile = event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE;
        boolean magic = event.getCause() == EntityDamageEvent.DamageCause.MAGIC
                || event.getCause() == EntityDamageEvent.DamageCause.WITHER
                || event.getCause() == EntityDamageEvent.DamageCause.POISON;
        double multiplier = modifiers.multiplierFor(event.getCause(), projectile, magic);

        if (multiplier <= 0.0d) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = playerBehind(event.getDamager());

        MobInstance victim = engine.mobs().instance(event.getEntity());
        if (victim != null) {
            engine.auras().onDamaged(event.getEntity());
            victim.enterCombat(event.getDamager());
            if (attacker != null) {
                victim.ledger().record(attacker, event.getFinalDamage());
                if (victim.threatTable() != null) {
                    victim.threatTable().addDamage(attacker, event.getFinalDamage());
                }
                victim.fire(TriggerKind.DAMAGED_BY_PLAYER, "", attacker, event);
            }
            victim.fire(TriggerKind.DAMAGED, "", event.getDamager(), event);
            victim.checkHealthThresholds();
        }

        MobInstance aggressor = engine.mobs().instance(event.getDamager());
        if (aggressor != null) {
            aggressor.enterCombat(event.getEntity());
            aggressor.fire(TriggerKind.ATTACK, "", event.getEntity(), event);
        }
    }

    // --- death ------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        MobInstance instance = engine.mobs().instance(entity);

        // A Bestiary mob killing a vanilla mob can suppress that mob's drops,
        // which is what stops a boss's minions carpeting an arena in rotten
        // flesh.
        if (instance == null) {
            LivingEntity killer = entity.getKiller();
            MobInstance killerMob = killer == null ? null : engine.mobs().instance(killer);
            if (killerMob == null && entity.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) entity.getLastDamageCause()).getDamager();
                killerMob = engine.mobs().instance(damager);
            }
            if (killerMob != null && killerMob.definition().options().preventMobKillDrops()) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            return;
        }

        if (instance.definition().options().preventOtherDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player killer = entity.getKiller();
        List<Player> contributors = instance.ledger().contributors();

        instance.fire(TriggerKind.DEATH, "", killer, null);
        if (killer != null) {
            MobInstance killerMob = engine.mobs().instance(killer);
            if (killerMob != null) {
                killerMob.fire(TriggerKind.KILL, "", entity, null);
            }
            engine.stats().recordKill(killer.getUniqueId(), instance.definition().id());
            engine.storage().recordKill(killer.getUniqueId(), instance.definition().id());
        }

        Bukkit.getPluginManager().callEvent(
                new BestiaryMobDeathEvent(instance, killer, contributors));

        engine.drops().rollForDeath(instance, killer, contributors, entity.getLocation());

        if (!instance.anchorId().isEmpty()) {
            engine.anchors().onKilled(instance.anchorId());
        }
        engine.mobs().forget(instance);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKillPlayer(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent)) {
            return;
        }
        Entity damager = ((EntityDamageByEntityEvent) event.getEntity().getLastDamageCause()).getDamager();
        MobInstance instance = engine.mobs().instance(damager);
        if (instance != null) {
            instance.fire(TriggerKind.KILL_PLAYER, "", event.getEntity(), null);
        }
    }

    // --- targeting --------------------------------------------------------

    /**
     * Threat overrides vanilla's last-attacker heuristic, factions stop mobs
     * eating each other, and Citizens NPCs are never adopted as targets.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        MobInstance instance = engine.mobs().instance(event.getEntity());
        if (instance == null) {
            return;
        }

        LivingEntity wanted = event.getTarget();
        if (wanted != null && engine.hooks().isNpc(wanted) && !engine.settings().adoptCitizens()) {
            event.setCancelled(true);
            return;
        }

        String faction = instance.definition().faction();
        if (!faction.isEmpty() && wanted != null) {
            MobInstance other = engine.mobs().instance(wanted);
            if (other != null && faction.equalsIgnoreCase(other.definition().faction())) {
                event.setCancelled(true);
                return;
            }
        }

        if (instance.threatTable() != null) {
            Player top = instance.threatTable().select();
            if (top != null && !top.equals(wanted)) {
                event.setTarget(top);
            }
        }
    }

    // --- other triggers ---------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        MobInstance instance = engine.mobs().instance(event.getRightClicked());
        if (instance != null) {
            instance.fire(TriggerKind.INTERACT, "", event.getPlayer(), event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Entity)) {
            return;
        }
        MobInstance instance = engine.mobs().instance((Entity) projectile.getShooter());
        if (instance != null) {
            instance.fire(TriggerKind.PROJECTILE_HIT, "", event.getHitEntity(), null);
        }
    }

    /** {@code prevent_sunburn}, so a daylight boss does not quietly cook itself. */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        MobInstance instance = engine.mobs().instance(event.getEntity());
        if (instance != null && instance.definition().options().preventSunburn()) {
            event.setCancelled(true);
        }
    }

    // --- lifecycle --------------------------------------------------------

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (engine.anchors().adoptEntity(entity)) {
                continue;
            }
            engine.mobs().adopt(entity);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            engine.mobs().unload(entity);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        engine.storage().loadKills(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BlockMechanics.forget(event.getPlayer().getUniqueId());
        PlayerMechanics.forget(event.getPlayer().getUniqueId());
    }

    /** The player behind a hit, following a projectile back to its shooter. */
    private static Player playerBehind(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        if (damager instanceof Mob && ((Mob) damager).getTarget() instanceof Player) {
            return null;
        }
        return null;
    }
}
