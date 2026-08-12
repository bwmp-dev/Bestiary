package dev.bwmp.bestiary.gui;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.api.config.Args;
import dev.bwmp.bestiary.api.config.SkillNode;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.ParameterSpec;
import dev.bwmp.keystone.gui.GuiButton;
import dev.bwmp.keystone.gui.PaginatedMenu;
import dev.bwmp.keystone.text.KeystoneText;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The parameters of one mechanic line.
 * <p>
 * Lists every parameter the mechanic declares, whether or not the line sets it,
 * so a skill can be completed without reading the docs — which is most of the
 * reason the alias table lives with the mechanic rather than in the parser.
 */
final class ParameterMenu extends PaginatedMenu<String> {

    private final BestiaryPlugin plugin;
    private final SkillEditorMenu parent;
    private SkillNode node;

    ParameterMenu(BestiaryPlugin plugin, SkillEditorMenu parent, SkillNode node) {
        super("<dark_gray>Parameters <gray>| <white>" + node.type(), 5);
        this.plugin = plugin;
        this.parent = parent;
        this.node = node;
    }

    @Override
    protected List<String> contents() {
        Set<String> names = new LinkedHashSet<>();
        MechanicType type = plugin.registries().mechanicIndex().find(node.type()).orElse(null);
        if (type != null) {
            names.addAll(type.meta().parameterNames());
        }
        names.addAll(node.args().keys());
        return new ArrayList<>(names);
    }

    @Override
    protected GuiButton renderEntry(String name) {
        Object value = node.args().get(name);
        boolean set = value != null;
        List<String> lore = new ArrayList<>();
        lore.add(set ? "<gray>current: <white>" + value : "<dark_gray>not set");
        describe(name).ifPresent(description -> lore.add("<dark_gray>" + description));
        lore.add("");
        lore.add("<yellow>Left-click <gray>to type a new value in chat");
        if (set) {
            lore.add("<yellow>Right-click <gray>to clear it");
        }

        return GuiButton.of(BrowserMenu.describe(set ? Material.WRITABLE_BOOK : Material.PAPER,
                (set ? "<white>" : "<gray>") + name, lore), click -> {
            if (click.isRight() && set) {
                node = new SkillNode(node.type(), node.args().without(name), node.targeter(),
                        node.conditions(), node.trigger(), node.children(), node.source());
                parent.replace(nodeBefore(), node);
                refresh();
                return;
            }
            click.player().closeInventory();
            plugin.prompts().ask(click.player(),
                    "Type a new value for '" + name + "', or 'cancel'.", typed -> {
                        SkillNode updated = new SkillNode(node.type(),
                                node.args().with(name, typed), node.targeter(), node.conditions(),
                                node.trigger(), node.children(), node.source());
                        parent.replace(node, updated);
                        node = updated;
                        plugin.scheduler().atEntity(click.player(), () -> parent.reopen(click.player()));
                    });
        });
    }

    /** The node as the parent currently holds it, before this menu's last edit. */
    private SkillNode nodeBefore() {
        return node;
    }

    @Override
    protected void decorateNavigation() {
        int row = (rows() - 1) * 9;
        set(row + 4, GuiButton.of(BrowserMenu.describe(Material.ARROW, "<gray>Back", List.of()),
                click -> parent.reopen(click.player())));
    }

    private java.util.Optional<String> describe(String name) {
        MechanicType type = plugin.registries().mechanicIndex().find(node.type()).orElse(null);
        if (type == null) {
            return java.util.Optional.empty();
        }
        for (ParameterSpec parameter : type.meta().parameters()) {
            if (parameter.name().equals(ParameterSpec.normalize(name))) {
                return parameter.description().isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(parameter.description());
            }
        }
        return java.util.Optional.empty();
    }

    static Args unused(Args args) {
        return args;
    }

    static String escape(String value) {
        return KeystoneText.escape(value);
    }
}
