package dev.bwmp.bestiary.config;

import dev.bwmp.bestiary.api.ai.NavigationKind;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a mob out of real YAML, through the same path the loader uses.
 * <p>
 * The nested-block test is a regression guard: Bukkit's {@code getValues(false)}
 * hands nested mappings back as {@code MemorySection} rather than {@code Map},
 * so every {@code instanceof Map} check downstream answered false and whole
 * {@code ai:} and {@code options:} blocks were silently skipped — no error, no
 * warning, just a boss with no goals. Found on a live server, and this is what
 * stops it coming back.
 */
class MobParserTest {

    private static final String YAML = String.join("\n",
            "example_boss:",
            "  type: ravager",
            "  display: \"<gold>Example\"",
            "  health: 200",
            "  damage: 10",
            "  options:",
            "    despawn: false",
            "    prevent_other_drops: true",
            "    silent: true",
            "  threat:",
            "    enabled: true",
            "    decay: 0.05",
            "  ai:",
            "    navigation: flying",
            "    goals:",
            "      - clear: [ MOVE, TARGET ]",
            "      - bestiary:melee_attack{speed=1.0}",
            "      - type: bestiary:look_at_target",
            "        priority: 4",
            "  phases:",
            "    - name: ground",
            "      until: { health_percent: \"<= 60\" }",
            "    - name: enraged",
            "      on_enter: example_enrage",
            "  bossbar:",
            "    title: \"<gold>Example\"",
            "    color: yellow",
            "    style: segmented_10",
            "    range: 48",
            "  skills:",
            "    - skill{s=example_shockwave} ~onTimer:160 ?phase{is=ground}",
            "  drops: example_boss_drops");

    private static MobDefinition parse() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(YAML);
        Map<String, Object> section = SkillParser.deepValues(
                yaml.getConfigurationSection("example_boss"));
        return MobParser.parse(new NamespacedKey("bestiary", "example_boss"), section, "test.yml", 1);
    }

    @Test
    void nestedBlocksAreRead() throws InvalidConfigurationException {
        MobDefinition definition = parse();

        assertFalse(definition.options().despawn());
        assertTrue(definition.options().preventOtherDrops());
        assertTrue(definition.options().silent());

        assertTrue(definition.threat().enabled());
        assertEquals(0.05d, definition.threat().decayPerSecond(), 1.0e-9d);

        assertEquals(3, definition.ai().goals().size());
        assertTrue(definition.ai().goals().get(0).isClear());
        assertEquals(2, definition.ai().goals().get(0).clears().size());
        assertEquals("bestiary:melee_attack", definition.ai().goals().get(1).type());
        assertEquals("1.0", definition.ai().goals().get(1).args().get("speed"));
        assertEquals(4, definition.ai().goals().get(2).priority());
        assertEquals(NavigationKind.FLYING, definition.ai().navigation());

        assertTrue(definition.bossbar().enabled());
        assertEquals(48.0d, definition.bossbar().range(), 1.0e-9d);
    }

    @Test
    void phasesCarryTheirExitConditions() throws InvalidConfigurationException {
        MobDefinition definition = parse();

        assertEquals(2, definition.phases().size());
        assertEquals("ground", definition.phases().get(0).name());
        assertEquals(1, definition.phases().get(0).until().size());
        assertEquals("healthpercent", definition.phases().get(0).until().get(0).type());
        // The value is offered under both names so numeric and string
        // conditions each find it under the one they declared.
        assertEquals("<= 60", definition.phases().get(0).until().get(0).args().get("amount"));
        assertEquals("<= 60", definition.phases().get(0).until().get(0).args().get("is"));

        assertTrue(definition.phases().get(1).until().isEmpty());
        assertEquals("example_enrage", definition.phases().get(1).onEnter());
    }

    @Test
    void skillLinesKeepTheirTriggerAndConditions() throws InvalidConfigurationException {
        MobDefinition definition = parse();

        assertEquals(1, definition.skills().size());
        var line = definition.skills().get(0);
        assertEquals("skill", line.type());
        assertEquals("example_shockwave", line.args().get("s"));
        assertEquals(160L, line.trigger().parameterAsTicks(0));
        assertEquals("phase", line.conditions().get(0).type());
    }

    @Test
    void deepValuesHashOnContent() throws InvalidConfigurationException {
        YamlConfiguration first = new YamlConfiguration();
        first.loadFromString(YAML);
        YamlConfiguration second = new YamlConfiguration();
        second.loadFromString(YAML.replace("health: 200", "health: 250"));

        String unchanged = SkillParser.deepValues(first.getConfigurationSection("example_boss")).toString();
        String changed = SkillParser.deepValues(second.getConfigurationSection("example_boss")).toString();

        // The revision is a hash of exactly this string. Before nested sections
        // were flattened it read "MemorySection[path='...']", which is identical
        // for two different definitions at the same path — so a boss alive
        // across a reload would never have been re-bound.
        assertEquals(unchanged, SkillParser.deepValues(
                first.getConfigurationSection("example_boss")).toString());
        assertTrue(unchanged.contains("200"));
        assertFalse(unchanged.equals(changed), "content changed but the hash source did not");
    }
}
