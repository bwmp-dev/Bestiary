package dev.bwmp.bestiary.api.mob;

import dev.bwmp.bestiary.api.ai.AiDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a creature is.
 * <p>
 * Every Bestiary mob is a vanilla base type with overrides — registering new
 * entity types is a registry-level NMS commitment that breaks on every
 * Minecraft release and buys almost nothing the Goal API and display entities
 * cannot fake.
 */
public final class MobDefinition {

    private final NamespacedKey id;
    private final EntityType type;
    private final String display;
    private final double health;
    private final double damage;
    private final double armor;
    private final double armorToughness;
    private final double knockbackResistance;
    private final double movementSpeed;
    private final double followRange;
    private final double scale;
    private final MobOptions options;
    private final Map<EquipmentSlot, String> equipment;
    private final Map<EquipmentSlot, Float> equipmentDropChance;
    private final String faction;
    private final ThreatSettings threat;
    private final AiDefinition ai;
    private final List<PhaseDefinition> phases;
    private final List<SkillNode> skills;
    private final BossbarDefinition bossbar;
    private final DamageModifiers damageModifiers;
    private final String dropTable;
    private final String levelModifier;
    private final int defaultLevel;
    private final String modelEngineModel;
    private final boolean suppressExternalXp;
    private final String source;
    private final int revision;

    private MobDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.display = builder.display;
        this.health = builder.health;
        this.damage = builder.damage;
        this.armor = builder.armor;
        this.armorToughness = builder.armorToughness;
        this.knockbackResistance = builder.knockbackResistance;
        this.movementSpeed = builder.movementSpeed;
        this.followRange = builder.followRange;
        this.scale = builder.scale;
        this.options = builder.options;
        this.equipment = Map.copyOf(builder.equipment);
        this.equipmentDropChance = Map.copyOf(builder.equipmentDropChance);
        this.faction = builder.faction;
        this.threat = builder.threat;
        this.ai = builder.ai;
        this.phases = List.copyOf(builder.phases);
        this.skills = List.copyOf(builder.skills);
        this.bossbar = builder.bossbar;
        this.damageModifiers = builder.damageModifiers;
        this.dropTable = builder.dropTable;
        this.levelModifier = builder.levelModifier;
        this.defaultLevel = builder.defaultLevel;
        this.modelEngineModel = builder.modelEngineModel;
        this.suppressExternalXp = builder.suppressExternalXp;
        this.source = builder.source;
        this.revision = builder.revision;
    }

    public static Builder builder(NamespacedKey id, EntityType type) {
        return new Builder(id, type);
    }

    public NamespacedKey id() {
        return id;
    }

    public EntityType type() {
        return type;
    }

    /** MiniMessage source. Rendered by the plugin, never handed out as a Component. */
    public String display() {
        return display;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public double armor() {
        return armor;
    }

    public double armorToughness() {
        return armorToughness;
    }

    public double knockbackResistance() {
        return knockbackResistance;
    }

    /** Negative means "leave the base type's value alone". */
    public double movementSpeed() {
        return movementSpeed;
    }

    public double followRange() {
        return followRange;
    }

    public double scale() {
        return scale;
    }

    public MobOptions options() {
        return options;
    }

    /** Slot to item id: a Sigil id when one is installed, a material otherwise. */
    public Map<EquipmentSlot, String> equipment() {
        return equipment;
    }

    public Map<EquipmentSlot, Float> equipmentDropChance() {
        return equipmentDropChance;
    }

    /** Mobs of the same faction do not target each other. Empty means none. */
    public String faction() {
        return faction;
    }

    public ThreatSettings threat() {
        return threat;
    }

    public AiDefinition ai() {
        return ai;
    }

    public List<PhaseDefinition> phases() {
        return phases;
    }

    /** Mechanic lines with triggers attached. */
    public List<SkillNode> skills() {
        return skills;
    }

    public BossbarDefinition bossbar() {
        return bossbar;
    }

    public DamageModifiers damageModifiers() {
        return damageModifiers;
    }

    /** Drop table id, empty when the mob drops nothing of its own. */
    public String dropTable() {
        return dropTable;
    }

    /**
     * An expression scaling attributes by {@code <caster.level>}, empty when
     * levels do not scale this mob.
     */
    public String levelModifier() {
        return levelModifier;
    }

    public int defaultLevel() {
        return defaultLevel;
    }

    public String modelEngineModel() {
        return modelEngineModel;
    }

    /**
     * Suppresses mcMMO and Jobs XP for this mob. A 400 HP boss otherwise
     * distorts both economies.
     */
    public boolean suppressExternalXp() {
        return suppressExternalXp;
    }

    public String source() {
        return source;
    }

    /**
     * Bumped whenever the definition changes.
     * <p>
     * Matters more here than for items: a boss alive across a reload holds a
     * stale definition, and this is what the adopter compares against the
     * {@code bestiary:rev} it wrote at spawn.
     */
    public int revision() {
        return revision;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public static final class Builder {

        private final NamespacedKey id;
        private final EntityType type;
        private String display = "";
        private double health = -1;
        private double damage = -1;
        private double armor = -1;
        private double armorToughness = -1;
        private double knockbackResistance = -1;
        private double movementSpeed = -1;
        private double followRange = -1;
        private double scale = -1;
        private MobOptions options = MobOptions.DEFAULT;
        private final Map<EquipmentSlot, String> equipment = new LinkedHashMap<>();
        private final Map<EquipmentSlot, Float> equipmentDropChance = new LinkedHashMap<>();
        private String faction = "";
        private ThreatSettings threat = ThreatSettings.DISABLED;
        private AiDefinition ai = AiDefinition.NONE;
        private List<PhaseDefinition> phases = List.of();
        private List<SkillNode> skills = List.of();
        private BossbarDefinition bossbar = BossbarDefinition.NONE;
        private DamageModifiers damageModifiers = DamageModifiers.NONE;
        private String dropTable = "";
        private String levelModifier = "";
        private int defaultLevel = 1;
        private String modelEngineModel = "";
        private boolean suppressExternalXp;
        private String source = "";
        private int revision;

        private Builder(NamespacedKey id, EntityType type) {
            this.id = id;
            this.type = type;
        }

        public Builder display(String value) {
            this.display = value == null ? "" : value;
            return this;
        }

        public Builder health(double value) {
            this.health = value;
            return this;
        }

        public Builder damage(double value) {
            this.damage = value;
            return this;
        }

        public Builder armor(double value) {
            this.armor = value;
            return this;
        }

        public Builder armorToughness(double value) {
            this.armorToughness = value;
            return this;
        }

        public Builder knockbackResistance(double value) {
            this.knockbackResistance = value;
            return this;
        }

        public Builder movementSpeed(double value) {
            this.movementSpeed = value;
            return this;
        }

        public Builder followRange(double value) {
            this.followRange = value;
            return this;
        }

        public Builder scale(double value) {
            this.scale = value;
            return this;
        }

        public Builder options(MobOptions value) {
            this.options = value == null ? MobOptions.DEFAULT : value;
            return this;
        }

        public Builder equip(EquipmentSlot slot, String itemId, float dropChance) {
            equipment.put(slot, itemId);
            equipmentDropChance.put(slot, dropChance);
            return this;
        }

        public Builder faction(String value) {
            this.faction = value == null ? "" : value;
            return this;
        }

        public Builder threat(ThreatSettings value) {
            this.threat = value == null ? ThreatSettings.DISABLED : value;
            return this;
        }

        public Builder ai(AiDefinition value) {
            this.ai = value == null ? AiDefinition.NONE : value;
            return this;
        }

        public Builder phases(List<PhaseDefinition> value) {
            this.phases = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public Builder skills(List<SkillNode> value) {
            this.skills = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public Builder bossbar(BossbarDefinition value) {
            this.bossbar = value == null ? BossbarDefinition.NONE : value;
            return this;
        }

        public Builder damageModifiers(DamageModifiers value) {
            this.damageModifiers = value == null ? DamageModifiers.NONE : value;
            return this;
        }

        public Builder dropTable(String value) {
            this.dropTable = value == null ? "" : value;
            return this;
        }

        public Builder levelModifier(String value) {
            this.levelModifier = value == null ? "" : value;
            return this;
        }

        public Builder defaultLevel(int value) {
            this.defaultLevel = Math.max(1, value);
            return this;
        }

        public Builder modelEngineModel(String value) {
            this.modelEngineModel = value == null ? "" : value;
            return this;
        }

        public Builder suppressExternalXp(boolean value) {
            this.suppressExternalXp = value;
            return this;
        }

        public Builder source(String value) {
            this.source = value == null ? "" : value;
            return this;
        }

        public Builder revision(int value) {
            this.revision = value;
            return this;
        }

        public MobDefinition build() {
            return new MobDefinition(this);
        }
    }
}
