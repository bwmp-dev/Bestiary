package dev.bwmp.bestiary.gui;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.skill.CompiledSkill;
import dev.bwmp.bestiary.spawn.AnchorRecord;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.keystone.gui.GuiButton;
import dev.bwmp.keystone.gui.PaginatedMenu;
import dev.bwmp.keystone.text.LegacyRenderer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A browser over mobs, skills, drop tables, spawners and anchors: inspect,
 * spawn, test-cast, jump to anchor.
 * <p>
 * Editing is <b>structural, not textual</b> — {@link SkillEditorMenu} manipulates
 * the {@code SkillNode} graph and re-serialises to canonical structured YAML.
 * That is the payoff for choosing structured YAML as the canonical form: a GUI
 * over an inline DSL would have to round-trip strings and would mangle comments
 * and formatting on every save. A skill edited here comes back structured, and
 * the docs say so plainly.
 */
public final class BrowserMenu extends PaginatedMenu<Object> {

    public enum View {
        MOBS("Mobs", Material.ZOMBIE_HEAD),
        SKILLS("Skills", Material.BOOK),
        DROPS("Drop tables", Material.CHEST),
        SPAWNERS("Spawners", Material.SPAWNER),
        ANCHORS("Anchors", Material.LODESTONE);

        private final String title;
        private final Material icon;

        View(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final BestiaryPlugin plugin;
    private View view;

    public BrowserMenu(BestiaryPlugin plugin, View view) {
        super("<dark_gray>Bestiary <gray>| <white>" + view.title, 6);
        this.plugin = plugin;
        this.view = view;
    }

    @Override
    protected List<Object> contents() {
        List<Object> entries = new ArrayList<>();
        switch (view) {
            case SKILLS:
                plugin.content().namedSkillIds()
                        .forEach(id -> entries.add(plugin.content().skill(id)));
                break;
            case DROPS:
                entries.addAll(plugin.content().dropTables().values());
                break;
            case SPAWNERS:
                entries.addAll(plugin.content().spawners().values());
                break;
            case ANCHORS:
                entries.addAll(plugin.anchors().all());
                break;
            case MOBS:
            default:
                entries.addAll(plugin.content().mobs());
                break;
        }
        return entries;
    }

    @Override
    protected GuiButton renderEntry(Object entry) {
        if (entry instanceof MobDefinition) {
            MobDefinition definition = (MobDefinition) entry;
            ItemStack icon = describe(Material.ZOMBIE_HEAD,
                    definition.display().isEmpty()
                            ? "<white>" + definition.id().getKey()
                            : definition.display(),
                    List.of("<gray>id: <white>" + definition.id(),
                            "<gray>type: <white>" + definition.type(),
                            "<gray>health: <white>" + definition.health(),
                            "",
                            "<yellow>Left-click <gray>to spawn at your feet",
                            "<yellow>Right-click <gray>to kill every live one"));
            return GuiButton.of(icon, click -> {
                if (click.isRight()) {
                    plugin.mobs().byDefinition(definition.id())
                            .forEach(instance -> instance.remove(false));
                } else {
                    plugin.mobs().spawn(definition.id(), click.player().getLocation(), 0);
                }
                refresh();
            });
        }

        if (entry instanceof CompiledSkill) {
            CompiledSkill skill = (CompiledSkill) entry;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>from <white>" + skill.source());
            lore.add("<gray>lines: <white>" + skill.lines().size());
            lore.add("");
            lore.add("<yellow>Left-click <gray>to cast it from you");
            lore.add("<yellow>Right-click <gray>to edit its structure");
            ItemStack icon = describe(Material.BOOK, "<white>" + skill.id(), lore);
            return GuiButton.of(icon, click -> {
                if (click.isRight()) {
                    new SkillEditorMenu(plugin, skill.id()).open(click.player());
                    return;
                }
                click.player().closeInventory();
                plugin.executor().cast(skill, click.player(), null,
                        click.player().getLocation(), List.of(), 1.0d, null, null);
            });
        }

        if (entry instanceof DropTable) {
            DropTable table = (DropTable) entry;
            ItemStack icon = describe(Material.CHEST, "<white>" + table.id(),
                    List.of("<gray>mode: <white>" + table.mode(),
                            "<gray>entries: <white>" + table.entries().size(),
                            "",
                            "<yellow>Click <gray>to roll it for yourself"));
            return GuiButton.of(icon, click -> {
                var execution = plugin.executor().newExecution(click.player(), null,
                        click.player().getLocation(), null);
                plugin.drops().preview(table, null, click.player(), execution.contextForConditions(),
                                click.player().getLocation())
                        .forEach(stack -> click.player().getInventory().addItem(stack));
                refresh();
            });
        }

        if (entry instanceof AnchorRecord) {
            AnchorRecord record = (AnchorRecord) entry;
            long remaining = plugin.anchors().cooldownRemaining(record.id());
            ItemStack icon = describe(Material.LODESTONE, "<white>" + record.id(),
                    List.of("<gray>mob: <white>" + record.mob(),
                            "<gray>ready: <white>" + (remaining <= 0 ? "yes"
                                    : dev.bwmp.bestiary.api.config.Durations.humanize(remaining)),
                            "",
                            "<yellow>Left-click <gray>to teleport there",
                            "<yellow>Right-click <gray>to clear its cooldown"));
            return GuiButton.of(icon, click -> {
                if (click.isRight()) {
                    plugin.anchors().reset(record.id());
                    refresh();
                    return;
                }
                if (record.location() != null) {
                    click.player().closeInventory();
                    plugin.scheduler().teleport(click.player(), record.location());
                }
            });
        }

        var spawner = (dev.bwmp.bestiary.spawn.SpawnerDefinition) entry;
        ItemStack icon = describe(Material.SPAWNER, "<white>" + spawner.id(),
                List.of("<gray>mob: <white>" + spawner.mob(),
                        "<gray>max: <white>" + spawner.maxConcurrent(),
                        "",
                        "<yellow>Click <gray>to teleport there"));
        return GuiButton.of(icon, click -> {
            click.player().closeInventory();
            plugin.scheduler().teleport(click.player(), spawner.location());
        });
    }

    @Override
    protected void decorateNavigation() {
        int row = (rows() - 1) * 9;
        int slot = row + 1;
        for (View candidate : View.values()) {
            if (slot >= row + 4) {
                break;
            }
            View target = candidate;
            ItemStack icon = describe(candidate.icon,
                    (candidate == view ? "<green>" : "<gray>") + candidate.title, List.of());
            set(slot++, GuiButton.of(icon, click -> {
                view = target;
                refresh();
            }));
        }
        set(row + 5, GuiButton.of(describe(Material.LODESTONE, "<gray>Anchors", List.of()), click -> {
            view = View.ANCHORS;
            refresh();
        }));
    }

    static ItemStack describe(Material material, String miniMessageName, List<String> miniMessageLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyRenderer.renderMiniMessage(miniMessageName));
            List<String> lore = new ArrayList<>(miniMessageLore.size());
            for (String line : miniMessageLore) {
                lore.add(LegacyRenderer.renderMiniMessage(line));
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
