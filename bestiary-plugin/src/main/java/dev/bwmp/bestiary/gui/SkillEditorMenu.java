package dev.bwmp.bestiary.gui;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.SkillDefinition;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.config.SkillWriter;
import dev.bwmp.keystone.gui.GuiButton;
import dev.bwmp.keystone.gui.PaginatedMenu;
import dev.bwmp.keystone.text.KeystoneText;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structural editing of one skill.
 * <p>
 * Operates on the {@link SkillNode} graph and re-serialises to canonical
 * structured YAML, never on the file's text. Parameter values are typed in
 * chat rather than in an anvil, because an anvil rename is capped at 50
 * characters and half the useful values here are expressions.
 */
public final class SkillEditorMenu extends PaginatedMenu<SkillNode> {

    private final BestiaryPlugin plugin;
    private final String skillId;
    private final List<SkillNode> lines = new ArrayList<>();
    private final SkillDefinition original;
    private boolean dirty;

    public SkillEditorMenu(BestiaryPlugin plugin, String skillId) {
        super("<dark_gray>Edit <white>" + skillId, 6);
        this.plugin = plugin;
        this.skillId = skillId;
        this.original = plugin.content().skillDefinition(skillId);
        if (original != null) {
            lines.addAll(original.lines());
        }
    }

    @Override
    protected List<SkillNode> contents() {
        return lines;
    }

    @Override
    protected GuiButton renderEntry(SkillNode node) {
        int index = lines.indexOf(node);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + node.toShorthand());
        for (Map.Entry<String, Object> entry : node.args().asMap().entrySet()) {
            lore.add("<dark_gray> - <gray>" + entry.getKey() + ": <white>" + render(entry.getValue()));
        }
        lore.add("");
        lore.add("<yellow>Left-click <gray>to edit a parameter");
        lore.add("<yellow>Right-click <gray>to delete this line");
        lore.add("<yellow>Shift-left <gray>to move it up");
        lore.add("<yellow>Shift-right <gray>to duplicate it");

        return GuiButton.of(BrowserMenu.describe(Material.PAPER,
                "<white>" + (index + 1) + ". " + node.type(), lore), click -> {
            if (click.isShift() && click.isLeft()) {
                if (index > 0) {
                    lines.add(index - 1, lines.remove(index));
                    dirty = true;
                }
            } else if (click.isShift()) {
                lines.add(index + 1, node);
                dirty = true;
            } else if (click.isRight()) {
                lines.remove(index);
                dirty = true;
            } else {
                new ParameterMenu(plugin, this, node).open(click.player());
                return;
            }
            refresh();
        });
    }

    @Override
    protected void decorateNavigation() {
        int row = (rows() - 1) * 9;
        set(row + 2, GuiButton.of(BrowserMenu.describe(Material.LIME_DYE,
                dirty ? "<green>Save changes" : "<gray>No changes",
                List.of("<gray>Writes canonical structured YAML.",
                        "<gray>Hand-written shorthand in this file",
                        "<gray>is replaced by the structured form.")), click -> {
            if (!dirty) {
                return;
            }
            save(click.player());
        }));
        set(row + 6, GuiButton.of(BrowserMenu.describe(Material.BARRIER, "<red>Discard", List.of()),
                click -> new BrowserMenu(plugin, BrowserMenu.View.SKILLS).open(click.player())));
    }

    void replace(SkillNode previous, SkillNode replacement) {
        int index = lines.indexOf(previous);
        if (index >= 0) {
            lines.set(index, replacement);
            dirty = true;
        }
    }

    void reopen(Player player) {
        open(player);
    }

    private void save(Player player) {
        if (original == null) {
            player.sendMessage("Bestiary: the original definition is no longer loaded; nothing saved.");
            return;
        }
        SkillDefinition updated = new SkillDefinition(skillId, original.cooldownTicks(),
                original.conditions(), lines, original.source(), original.revision() + 1);
        File file = new File(plugin.getDataFolder(), original.source());
        try {
            SkillWriter.save(file, skillId, updated);
            dirty = false;
            plugin.reloadBestiary();
            plugin.messages().sendComponent(player,
                    KeystoneText.parse("<green>Saved <white>" + skillId + "<green> and reloaded."));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save " + skillId + ": " + exception);
            plugin.messages().sendComponent(player,
                    KeystoneText.parse("<red>Could not save: <white>" + exception.getMessage()));
        }
    }

    private static String render(Object value) {
        if (value instanceof Args) {
            return ((Args) value).toShorthand();
        }
        return String.valueOf(value);
    }
}
