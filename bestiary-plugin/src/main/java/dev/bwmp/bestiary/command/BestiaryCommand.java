package dev.bwmp.bestiary.command;

import dev.bwmp.bestiary.BestiaryPlugin;
import dev.bwmp.bestiary.api.config.Durations;
import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.mob.MobDefinition;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.gui.BrowserMenu;
import dev.bwmp.bestiary.importer.MythicImporter;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.mob.MobManager;
import dev.bwmp.bestiary.skill.CompiledLine;
import dev.bwmp.bestiary.skill.CompiledSkill;
import dev.bwmp.bestiary.spawn.AnchorRecord;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.keystone.command.CommandArguments;
import dev.bwmp.keystone.command.CommandContext;
import dev.bwmp.keystone.command.RootCommand;
import dev.bwmp.keystone.command.SimpleSubcommand;
import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /bestiary}.
 * <p>
 * {@code cast} and {@code debug} are the ones that make development bearable:
 * {@code cast} runs a skill from the player as caster with a live trace of
 * targeter resolution and condition results, and {@code debug} attaches that
 * trace to a running mob.
 */
public final class BestiaryCommand {

    private final BestiaryPlugin plugin;
    private final MessageService messages;

    public BestiaryCommand(BestiaryPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public RootCommand build() {
        RootCommand root = new RootCommand(messages, "usage");

        root.register(SimpleSubcommand.of("spawn", this::spawn)
                .permission("bestiary.command.spawn")
                .usage("spawn <mob> [level] [x y z] [world]")
                .description("Spawns a mob at your feet, or at a position.")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(mobIds(), args.get(0, "")) : List.of()));

        root.register(SimpleSubcommand.of("kill", this::kill)
                .permission("bestiary.command.kill")
                .usage("kill <mob|all>")
                .description("Removes live mobs.")
                .completer((sender, args) -> {
                    List<String> options = new ArrayList<>(mobIds());
                    options.add("all");
                    return args.size() <= 1 ? RootCommand.matching(options, args.get(0, "")) : List.of();
                }));

        root.register(SimpleSubcommand.of("list", this::list)
                .permission("bestiary.command.list")
                .usage("list [mobs|skills|droptables|anchors|spawners]")
                .description("Lists loaded content."));

        root.register(SimpleSubcommand.of("info", this::info)
                .permission("bestiary.command.info")
                .usage("info <mob|skill>")
                .description("Inspects a definition.")
                .completer((sender, args) -> {
                    List<String> options = new ArrayList<>(mobIds());
                    options.addAll(plugin.content().namedSkillIds());
                    return args.size() <= 1 ? RootCommand.matching(options, args.get(0, "")) : List.of();
                }));

        root.register(SimpleSubcommand.of("cast", this::cast)
                .permission("bestiary.command.cast")
                .requiresPlayer()
                .usage("cast <skill> [target]")
                .description("Runs a skill from you, tracing every step.")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(plugin.content().namedSkillIds(), args.get(0, ""))
                        : List.of()));

        root.register(SimpleSubcommand.of("reload", this::reload)
                .permission("bestiary.command.reload")
                .description("Re-reads config and content."));

        root.register(SimpleSubcommand.of("anchor", this::anchor)
                .permission("bestiary.command.anchor")
                .usage("anchor <create|remove|list|reset> [args]")
                .description("Manages structure anchors.")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(List.of("create", "remove", "list", "reset"), args.get(0, ""))
                        : args.size() == 2 && args.lower(0).equals("create")
                        ? RootCommand.matching(mobIds(), args.get(1, ""))
                        : List.of()));

        root.register(SimpleSubcommand.of("spawner", this::spawner)
                .permission("bestiary.command.spawner")
                .usage("spawner <create|remove|list> [args]")
                .description("Manages placed spawners.")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(List.of("create", "remove", "list"), args.get(0, ""))
                        : List.of()));

        root.register(SimpleSubcommand.of("droptable", this::dropTable)
                .permission("bestiary.command.droptable")
                .usage("droptable test <table>")
                .description("Rolls a drop table without killing anything.")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(List.of("test"), args.get(0, ""))
                        : RootCommand.matching(plugin.content().dropTables().keySet(), args.get(1, ""))));

        root.register(SimpleSubcommand.of("import", this::importMythic)
                .permission("bestiary.command.import")
                .usage("import <file|dir>")
                .description("Converts MythicMobs configs to native Bestiary YAML."));

        root.register(SimpleSubcommand.of("debug", this::debug)
                .permission("bestiary.command.debug")
                .requiresPlayer()
                .usage("debug <mob|off>")
                .description("Attaches a live skill trace to the nearest matching mob."));

        root.register(SimpleSubcommand.of("menu", this::menu)
                .permission("bestiary.command.menu")
                .requiresPlayer()
                .description("Opens the content browser."));

        root.register(SimpleSubcommand.of("platform", this::platform)
                .permission("bestiary.command.platform")
                .description("Shows platform diagnostics."));

        root.defaultTo(SimpleSubcommand.of("help", context -> help(context, root))
                .description("Shows this list."));
        return root;
    }

    // --- subcommands ------------------------------------------------------

    private void spawn(CommandContext context) {
        CommandSender sender = context.sender();
        Player player = context.player().orElse(null);
        CommandArguments args = context.args();

        NamespacedKey id = MobManager.parseKey(args.get(0, ""));
        if (id == null || plugin.content().compiledMob(id) == null) {
            reply(sender, "<red>No such mob: <white>" + args.get(0, ""));
            return;
        }
        // Per-mob spawn gating, so a moderator can be trusted with some of the
        // bestiary and not all of it.
        if (player != null
                && !player.hasPermission("bestiary.mob." + id.getNamespace() + "." + id.getKey())
                && !player.hasPermission("bestiary.admin")) {
            messages.send(player, "no-permission");
            return;
        }

        int level = args.integer(1).orElse(0);
        Location where = player == null ? null : player.getLocation();
        if (args.size() >= 5) {
            World world = player == null ? null : player.getWorld();
            if (args.size() >= 6) {
                world = Bukkit.getWorld(args.get(5, ""));
            }
            if (world == null) {
                world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            }
            if (world == null) {
                reply(sender, "<red>No such world.");
                return;
            }
            try {
                where = new Location(world,
                        Double.parseDouble(args.get(2, "0")),
                        Double.parseDouble(args.get(3, "0")),
                        Double.parseDouble(args.get(4, "0")));
            } catch (NumberFormatException exception) {
                reply(sender, "<red>Coordinates must be numbers.");
                return;
            }
        }
        if (where == null) {
            reply(sender, "<red>From the console, give coordinates: "
                    + "<white>/bestiary spawn <mob> [level] <x> <y> <z> [world]");
            return;
        }

        Location destination = where;
        // Spawning is region work, so on Folia it belongs to the thread that
        // owns the destination rather than to whoever typed the command.
        plugin.scheduler().atLocation(destination, () -> {
            BestiaryMob spawned = plugin.mobs().spawn(id, destination, level).orElse(null);
            reply(sender, spawned == null
                    ? "<red>Could not spawn <white>" + id
                    : "<green>Spawned <white>" + id + "<green> at level <white>" + spawned.level()
                    + "<green> in <white>" + destination.getWorld().getName());
        });
    }

    private void kill(CommandContext context) {
        String written = context.args().get(0, "");
        if (written.isEmpty()) {
            reply(context.sender(), "<red>Usage: /bestiary kill <mob|all>");
            return;
        }
        List<MobInstance> targets;
        if (written.equalsIgnoreCase("all")) {
            targets = new ArrayList<>(plugin.mobs().instances());
        } else {
            NamespacedKey id = MobManager.parseKey(written);
            targets = id == null ? List.of() : plugin.mobs().byDefinition(id);
        }
        targets.forEach(instance -> instance.remove(false));
        reply(context.sender(), "<green>Removed <white>" + targets.size() + "<green> mob(s).");
    }

    private void list(CommandContext context) {
        String what = context.args().get(0, "mobs").toLowerCase(Locale.ROOT);
        CommandSender sender = context.sender();
        switch (what) {
            case "skills":
                reply(sender, "<gold>Skills <gray>(" + plugin.content().namedSkillIds().size() + ")<white> "
                        + String.join(", ", plugin.content().namedSkillIds()));
                break;
            case "droptables":
                reply(sender, "<gold>Drop tables <white>"
                        + String.join(", ", plugin.content().dropTables().keySet()));
                break;
            case "anchors": {
                List<String> lines = new ArrayList<>();
                for (AnchorRecord anchor : plugin.anchors().all()) {
                    lines.add(anchor.id() + " -> " + anchor.mob());
                }
                reply(sender, "<gold>Anchors <gray>(" + lines.size() + ")");
                lines.forEach(line -> reply(sender, "<gray> - <white>" + line));
                break;
            }
            case "spawners":
                reply(sender, "<gold>Spawners <white>"
                        + String.join(", ", plugin.content().spawners().keySet()));
                break;
            case "mobs":
            default:
                reply(sender, "<gold>Mobs <gray>(" + mobIds().size() + ")<white> "
                        + String.join(", ", mobIds()));
                break;
        }
    }

    private void info(CommandContext context) {
        String written = context.args().get(0, "");
        CommandSender sender = context.sender();

        NamespacedKey id = MobManager.parseKey(written);
        MobDefinition definition = id == null ? null
                : plugin.content().mob(id).orElse(null);
        if (definition != null) {
            reply(sender, "<gold>" + definition.id() + " <gray>(" + definition.type() + ")");
            reply(sender, "<gray>display: <white>" + Text.plain(definition.display()));
            reply(sender, "<gray>health: <white>" + definition.health()
                    + " <gray>damage: <white>" + definition.damage()
                    + " <gray>armour: <white>" + definition.armor());
            reply(sender, "<gray>faction: <white>" + (definition.faction().isEmpty()
                    ? "-" : definition.faction())
                    + " <gray>drops: <white>" + (definition.dropTable().isEmpty()
                    ? "-" : definition.dropTable()));
            reply(sender, "<gray>skill lines: <white>" + definition.skills().size()
                    + " <gray>phases: <white>" + definition.phases().size()
                    + " <gray>goals: <white>" + definition.ai().goals().size());
            reply(sender, "<gray>revision: <white>" + definition.revision()
                    + " <gray>from <white>" + definition.source());
            return;
        }

        CompiledSkill skill = plugin.content().skill(written);
        if (skill == null) {
            reply(sender, "<red>No mob or skill called <white>" + written);
            return;
        }
        reply(sender, "<gold>" + skill.id() + " <gray>from <white>" + skill.source());
        if (skill.cooldownTicks() > 0) {
            reply(sender, "<gray>cooldown: <white>" + Durations.render(skill.cooldownTicks()));
        }
        for (CompiledLine line : skill.lines()) {
            reply(sender, "<gray> - <white>" + line);
        }
    }

    private void cast(CommandContext context) {
        Player player = context.requirePlayer();
        String skillId = context.args().get(0, "");
        CompiledSkill skill = plugin.content().skill(skillId);
        if (skill == null) {
            reply(player, "<red>No such skill: <white>" + skillId);
            return;
        }

        List<Target> targets = new ArrayList<>();
        Player explicit = context.args().player(1).orElse(null);
        if (explicit != null) {
            targets.add(Target.of(explicit));
        }

        reply(player, "<gold>casting <white>" + skill.id());
        plugin.executor().cast(skill, player, explicit, player.getLocation(), targets, 1.0d, null,
                line -> reply(player, "<dark_gray>|<gray> " + line));
    }

    private void reload(CommandContext context) {
        plugin.reloadBestiary();
        reply(context.sender(), "<green>Bestiary reloaded. See the console for the load report.");
    }

    private void anchor(CommandContext context) {
        CommandArguments args = context.args();
        String action = args.lower(0);
        CommandSender sender = context.sender();

        switch (action) {
            case "create": {
                Player player = context.player().orElse(null);
                if (player == null) {
                    reply(sender, "<red>Run this in-game: the anchor is created at your feet.");
                    return;
                }
                NamespacedKey id = MobManager.parseKey(args.get(1, ""));
                if (id == null || plugin.content().compiledMob(id) == null) {
                    reply(sender, "<red>No such mob: <white>" + args.get(1, ""));
                    return;
                }
                AnchorRecord record = plugin.anchors().create(player.getLocation(), id,
                        args.integer(2).orElse(1));
                reply(sender, "<green>Anchor <white>" + record.id() + "<green> created for <white>" + id);
                break;
            }
            case "remove":
                reply(sender, plugin.anchors().remove(args.get(1, ""))
                        ? "<green>Anchor removed."
                        : "<red>No anchor with that id.");
                break;
            case "reset":
                reply(sender, plugin.anchors().reset(args.get(1, ""))
                        ? "<green>Anchor cooldown cleared."
                        : "<red>No anchor with that id.");
                break;
            case "list":
            default: {
                var anchors = plugin.anchors().all();
                reply(sender, "<gold>Anchors <gray>(" + anchors.size() + ")");
                for (AnchorRecord record : anchors) {
                    long remaining = plugin.anchors().cooldownRemaining(record.id());
                    reply(sender, "<gray> - <white>" + record.id() + " <gray>-> <white>" + record.mob()
                            + (remaining > 0 ? " <gray>(ready in " + Durations.humanize(remaining) + ")"
                            : " <green>(ready)"));
                }
                break;
            }
        }
    }

    private void spawner(CommandContext context) {
        CommandArguments args = context.args();
        CommandSender sender = context.sender();
        switch (args.lower(0)) {
            case "create": {
                Player player = context.player().orElse(null);
                if (player == null) {
                    reply(sender, "<red>Run this in-game.");
                    return;
                }
                NamespacedKey mob = MobManager.parseKey(args.get(1, ""));
                if (mob == null || plugin.content().compiledMob(mob) == null) {
                    reply(sender, "<red>No such mob: <white>" + args.get(1, ""));
                    return;
                }
                String id = args.get(2, "spawner_" + System.currentTimeMillis());
                var definition = new dev.bwmp.bestiary.spawn.SpawnerDefinition(
                        id, mob, player.getLocation(), 1, 3, 20L * 30, 32, 4, 1, true, List.of());
                plugin.spawns().persist(definition);
                reply(sender, "<green>Spawner <white>" + id + "<green> saved. "
                        + "<gray>Add it to a spawners/ file to have it load on boot.");
                break;
            }
            case "remove":
                plugin.spawns().forget(args.get(1, ""));
                reply(sender, "<green>Spawner removed from storage.");
                break;
            case "list":
            default: {
                var rows = plugin.spawns().storedSpawners();
                reply(sender, "<gold>Stored spawners <gray>(" + rows.size() + ")");
                for (var row : rows) {
                    row.describe().forEach(line -> reply(sender, "<gray> <white>" + line));
                }
                break;
            }
        }
    }

    private void dropTable(CommandContext context) {
        CommandSender sender = context.sender();
        if (!context.args().lower(0).equals("test")) {
            reply(sender, "<red>Usage: /bestiary droptable test <table>");
            return;
        }
        DropTable table = plugin.content().dropTable(context.args().get(1, ""));
        if (table == null) {
            reply(sender, "<red>No such drop table.");
            return;
        }
        Player player = context.player().orElse(null);
        // From the console there is nobody to roll for and nowhere obvious to
        // stand, so the first world's spawn is the origin and the roll is a
        // dry run: entries needing a recipient are simply not awarded.
        Location where = player != null ? player.getLocation()
                : Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        if (where == null) {
            reply(sender, "<red>No world is loaded.");
            return;
        }
        var execution = plugin.executor().newExecution(player, null, where, null);
        List<ItemStack> rolled = plugin.drops().preview(table, null, player,
                execution.contextForConditions(), where);
        reply(sender, "<gold>" + table.id() + " <gray>rolled <white>" + rolled.size() + "<gray> item(s):");
        for (ItemStack stack : rolled) {
            reply(sender, "<gray> - <white>" + stack.getAmount() + "x " + stack.getType());
        }
    }

    private void importMythic(CommandContext context) {
        CommandSender sender = context.sender();
        String path = context.args().joinFrom(0);
        if (path.isBlank()) {
            reply(sender, "<red>Usage: /bestiary import <file|dir>");
            return;
        }
        File source = new File(path);
        if (!source.isAbsolute()) {
            source = new File(plugin.getServer().getWorldContainer(), path);
        }
        if (!source.exists()) {
            reply(sender, "<red>Nothing at <white>" + source.getAbsolutePath());
            return;
        }
        MythicImporter.Result result = new MythicImporter(plugin).importFrom(source);
        reply(sender, "<green>Imported <white>" + result.converted()
                + "<green> definition(s), <white>" + result.skipped() + "<green> skipped.");
        // A silent partial import is worse than a loud one: a boss that
        // converts to 90% of itself and never says so is a very expensive
        // debugging session.
        for (String warning : result.warnings()) {
            reply(sender, "<yellow> ! <white>" + warning);
        }
        reply(sender, "<gray>Written to <white>" + result.output());
    }

    private void debug(CommandContext context) {
        Player player = context.requirePlayer();
        String written = context.args().get(0, "");
        if (written.equalsIgnoreCase("off")) {
            plugin.debug().detach(player);
            reply(player, "<green>Debug trace detached.");
            return;
        }

        MobInstance nearest = null;
        double best = 64 * 64;
        for (MobInstance instance : plugin.mobs().instances()) {
            if (!instance.entity().getWorld().equals(player.getWorld())) {
                continue;
            }
            if (!written.isEmpty()
                    && !instance.definition().id().toString().equalsIgnoreCase(written)
                    && !instance.definition().id().getKey().equalsIgnoreCase(written)) {
                continue;
            }
            double distance = instance.entity().getLocation().distanceSquared(player.getLocation());
            if (distance < best) {
                best = distance;
                nearest = instance;
            }
        }
        if (nearest == null) {
            reply(player, "<red>No matching Bestiary mob within 64 blocks.");
            return;
        }
        plugin.debug().attach(player, nearest);
        reply(player, "<green>Tracing <white>" + nearest.definition().id()
                + "<green>. Use <white>/bestiary debug off<green> to stop.");
    }

    private void menu(CommandContext context) {
        new BrowserMenu(plugin, BrowserMenu.View.MOBS).open(context.requirePlayer());
    }

    private void platform(CommandContext context) {
        CommandSender sender = context.sender();
        plugin.keystone().platform().describe().forEach(line -> reply(sender, "<gray>" + line));
        reply(sender, "<gray>Scheduler: <white>"
                + (plugin.scheduler().isFolia() ? "Folia region scheduler" : "Bukkit"));
        reply(sender, "<gray>AI tier:   <white>" + (plugin.ai().available()
                ? "Paper Goal API" + (plugin.ai().nmsAvailable() ? " + NMS" : "") : "none (Spigot)"));
        reply(sender, "<gray>Content:   <white>" + mobIds().size() + " mobs, "
                + plugin.content().namedSkillIds().size() + " skills, "
                + plugin.content().dropTables().size() + " drop tables");
        reply(sender, "<gray>Live mobs: <white>" + plugin.mobs().activeMobs().size());
    }

    private void help(CommandContext context, RootCommand root) {
        CommandSender sender = context.sender();
        reply(sender, "<gold>Bestiary <gray>commands");
        root.subcommands().forEach(subcommand -> {
            if (!subcommand.permission().isBlank() && !sender.hasPermission(subcommand.permission())) {
                return;
            }
            reply(sender, "<gray>/bestiary <white>" + subcommand.usage()
                    + (subcommand.description().isEmpty() ? "" : " <dark_gray>- <gray>"
                    + subcommand.description()));
        });
    }

    // --- helpers ----------------------------------------------------------

    private List<String> mobIds() {
        List<String> ids = new ArrayList<>();
        plugin.content().compiledMobs().keySet().forEach(key -> ids.add(key.toString()));
        ids.sort(String::compareTo);
        return ids;
    }

    private void reply(CommandSender sender, String miniMessage) {
        messages.sendComponent(sender, KeystoneText.parse(miniMessage));
    }

    /** The nearest Bestiary mob to a sender, for commands that take no id. */
    static MobInstance nearest(BestiaryPlugin plugin, Entity origin, double range) {
        MobInstance nearest = null;
        double best = range * range;
        for (MobInstance instance : plugin.mobs().instances()) {
            if (!instance.entity().getWorld().equals(origin.getWorld())) {
                continue;
            }
            double distance = instance.entity().getLocation().distanceSquared(origin.getLocation());
            if (distance < best) {
                best = distance;
                nearest = instance;
            }
        }
        return nearest;
    }
}
