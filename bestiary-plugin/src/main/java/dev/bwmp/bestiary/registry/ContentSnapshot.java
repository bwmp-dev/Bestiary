package dev.bwmp.bestiary.registry;

import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.config.BestiarySettings;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.mob.CompiledMob;
import dev.bwmp.bestiary.skill.CompiledSkill;
import dev.bwmp.bestiary.spawn.RandomSpawnRule;
import dev.bwmp.bestiary.spawn.SpawnRegion;
import dev.bwmp.bestiary.spawn.SpawnerDefinition;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a load produced, as one immutable value.
 * <p>
 * Swapped in atomically on a {@code volatile} field, so a {@code /bestiary
 * reload} is safe to observe from any thread including Folia's: a reader either
 * sees the old world or the new one, never a half-applied reload.
 */
public final class ContentSnapshot {

    public static final ContentSnapshot EMPTY = new ContentSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), null);

    private final Map<String, CompiledSkill> skills;
    private final Map<String, dev.bwmp.bestiary.api.config.SkillDefinition> skillDefinitions;
    private final Map<NamespacedKey, CompiledMob> mobs;
    private final Map<String, DropTable> dropTables;
    private final Map<String, SpawnerDefinition> spawners;
    private final List<RandomSpawnRule> randomSpawns;
    private final Map<String, SpawnRegion> regions;
    private final BestiarySettings settings;

    public ContentSnapshot(Map<String, CompiledSkill> skills,
                           Map<String, dev.bwmp.bestiary.api.config.SkillDefinition> skillDefinitions,
                           Map<NamespacedKey, CompiledMob> mobs,
                           Map<String, DropTable> dropTables,
                           Map<String, SpawnerDefinition> spawners,
                           List<RandomSpawnRule> randomSpawns,
                           Map<String, SpawnRegion> regions,
                           BestiarySettings settings) {
        this.skills = Map.copyOf(skills);
        this.skillDefinitions = Map.copyOf(skillDefinitions);
        this.mobs = Map.copyOf(mobs);
        this.dropTables = Map.copyOf(dropTables);
        this.spawners = Map.copyOf(spawners);
        this.randomSpawns = List.copyOf(randomSpawns);
        this.regions = Map.copyOf(regions);
        this.settings = settings;
    }

    public CompiledSkill skill(String id) {
        return id == null ? null : skills.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, CompiledSkill> skills() {
        return skills;
    }

    /**
     * The parsed AST behind a named skill, kept so the GUI can edit structure
     * rather than text. Null for the synthetic skills lifted out of inline
     * blocks, which have no file of their own to write back to.
     */
    public dev.bwmp.bestiary.api.config.SkillDefinition skillDefinition(String id) {
        return id == null ? null : skillDefinitions.get(id.toLowerCase(Locale.ROOT));
    }

    public CompiledMob compiledMob(NamespacedKey id) {
        return id == null ? null : mobs.get(id);
    }

    public Optional<MobDefinition> mob(NamespacedKey id) {
        CompiledMob compiled = compiledMob(id);
        return compiled == null ? Optional.empty() : Optional.of(compiled.definition());
    }

    public Collection<MobDefinition> mobs() {
        List<MobDefinition> definitions = new ArrayList<>(mobs.size());
        for (CompiledMob compiled : mobs.values()) {
            definitions.add(compiled.definition());
        }
        return definitions;
    }

    public Map<NamespacedKey, CompiledMob> compiledMobs() {
        return mobs;
    }

    public DropTable dropTable(String id) {
        return id == null ? null : dropTables.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, DropTable> dropTables() {
        return dropTables;
    }

    public Map<String, SpawnerDefinition> spawners() {
        return spawners;
    }

    public List<RandomSpawnRule> randomSpawns() {
        return randomSpawns;
    }

    public Map<String, SpawnRegion> regions() {
        return regions;
    }

    public BestiarySettings settings() {
        return settings;
    }

    /** Named ids only; the synthetic ids of inline blocks are noise in a list. */
    public List<String> namedSkillIds() {
        return skills.values().stream()
                .filter(skill -> !skill.anonymous())
                .map(CompiledSkill::id)
                .sorted()
                .toList();
    }
}
