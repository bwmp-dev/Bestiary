package dev.bwmp.bestiary;

import dev.bwmp.bestiary.api.BestiaryAPI;
import dev.bwmp.bestiary.api.ai.AiGoalType;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargeterType;
import dev.bwmp.bestiary.skill.CompiledSkill;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The published API, over the same objects the plugin uses internally. */
public final class BestiaryApiImpl implements BestiaryAPI {

    private final BestiaryPlugin plugin;

    BestiaryApiImpl(BestiaryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<MobDefinition> mob(NamespacedKey id) {
        return plugin.content().mob(id);
    }

    @Override
    public Collection<MobDefinition> mobs() {
        return plugin.content().mobs();
    }

    @Override
    public Collection<String> skillIds() {
        return plugin.content().namedSkillIds();
    }

    @Override
    public Collection<String> dropTableIds() {
        return plugin.content().dropTables().keySet();
    }

    @Override
    public Optional<BestiaryMob> resolve(Entity entity) {
        return plugin.mobs().resolve(entity);
    }

    @Override
    public Collection<BestiaryMob> activeMobs() {
        return plugin.mobs().activeMobs();
    }

    @Override
    public Optional<BestiaryMob> spawn(NamespacedKey id, Location location, int level) {
        return plugin.mobs().spawn(id, location, level);
    }

    @Override
    public boolean castSkill(String skillId, Entity caster, List<Target> targets, double power) {
        CompiledSkill skill = plugin.content().skill(skillId);
        if (skill == null || caster == null) {
            return false;
        }
        return plugin.executor().cast(skill, caster, null, caster.getLocation(),
                targets == null ? List.of() : targets, power, null, null);
    }

    @Override
    public void registerMechanicType(Plugin owner, NamespacedKey id, MechanicType type) {
        plugin.registries().mechanics().register(owner, id, type);
    }

    @Override
    public void registerTargeterType(Plugin owner, NamespacedKey id, TargeterType type) {
        plugin.registries().targeters().register(owner, id, type);
    }

    @Override
    public void registerConditionType(Plugin owner, NamespacedKey id, ConditionType type) {
        plugin.registries().conditions().register(owner, id, type);
    }

    @Override
    public void registerGoalType(Plugin owner, NamespacedKey id, AiGoalType type) {
        plugin.registries().goals().register(owner, id, type);
        plugin.ai().registerGoalType(id.toString(), type);
    }

    @Override
    public int killCount(Player player, NamespacedKey mob) {
        return plugin.stats().kills(player, mob);
    }

    @Override
    public int totalKillCount(Player player) {
        return plugin.stats().total(player);
    }

    @Override
    public long anchorCooldownMillis(String anchorId) {
        return plugin.anchors().cooldownRemaining(anchorId);
    }

    @Override
    public BestiaryScheduler scheduler() {
        return plugin.scheduler();
    }
}
