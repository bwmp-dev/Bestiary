package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.drop.DropTable;
import dev.bwmp.bestiary.mob.MobInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Rewards and the integration seams.
 * <p>
 * {@code drop_table}, {@code currency} and {@code quest_progress} resolve
 * through the integration hooks and are no-ops with a report line when the
 * target plugin is absent — which is the whole contract of an integration
 * seam. They still
 * exist in the mechanic list on a server without those plugins, so a config
 * written against a fuller server loads rather than failing.
 */
public final class ProgressionMechanics {

    private ProgressionMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("give_item", Mechanics.type(
                MechanicMeta.builder("give_item")
                        .description("Puts an item in the target player's inventory.")
                        .requires(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i", "id")
                        .param("amount", "how many", "1", "a")
                        .param("drop_if_full", "drop the overflow rather than losing it", "true")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    Expression amount = config.number("amount", 1);
                    boolean dropIfFull = config.bool("drop_if_full", true);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack stack = engine.hooks().sigil()
                                .resolveItem(item, Math.max(1, amount.asInt(context, target)));
                        if (stack == null) {
                            return MechanicResult.FAIL;
                        }
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                        if (dropIfFull) {
                            overflow.values().forEach(left ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), left));
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("drop_item", Mechanics.type(
                MechanicMeta.builder("drop_item")
                        .description("Drops an item on the ground at the target.")
                        .requires(TargetKind.ANY)
                        .required("item", "a Sigil id or a material", "i", "id")
                        .param("amount", "how many", "1", "a")
                        .param("naturally", "scatter it as a normal drop", "true")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    Expression amount = config.number("amount", 1);
                    boolean naturally = config.bool("naturally", true);
                    return (context, target) -> {
                        Location location = target.location();
                        if (location.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack stack = engine.hooks().sigil()
                                .resolveItem(item, Math.max(1, amount.asInt(context, target)));
                        if (stack == null) {
                            return MechanicResult.FAIL;
                        }
                        if (naturally) {
                            location.getWorld().dropItemNaturally(location, stack);
                        } else {
                            location.getWorld().dropItem(location, stack);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("drop_table", Mechanics.type(
                MechanicMeta.builder("drop_table")
                        .description("Rolls a drop table and gives the result to the target player.")
                        .requires(TargetKind.ANY)
                        .required("table", "the drop table id", "t", "id")
                        .build(),
                config -> {
                    String tableId = config.raw("table", "");
                    return (context, target) -> {
                        DropTable table = engine.content().dropTable(tableId);
                        if (table == null) {
                            return MechanicResult.FAIL;
                        }
                        MobInstance instance = StateMechanics.instanceOf(engine, context.caster());
                        Player player = target.player();
                        List<ItemStack> items = engine.drops()
                                .preview(table, instance, player, context, target.location());
                        Location where = target.location();
                        for (ItemStack stack : items) {
                            if (player != null) {
                                player.getInventory().addItem(stack).values().forEach(left ->
                                        player.getWorld().dropItemNaturally(player.getLocation(), left));
                            } else if (where.getWorld() != null) {
                                where.getWorld().dropItemNaturally(where, stack);
                            }
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("give_exp", Mechanics.type(
                MechanicMeta.builder("give_exp")
                        .description("Gives experience to a player, or drops orbs at a location.")
                        .requires(TargetKind.ANY)
                        .param("amount", "how much", "10", "a", "exp")
                        .param("levels", "give levels rather than points", "false")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 10);
                    boolean levels = config.bool("levels", false);
                    return (context, target) -> {
                        int value = amount.asInt(context, target);
                        Player player = target.player();
                        if (player != null) {
                            if (levels) {
                                player.giveExpLevels(value);
                            } else {
                                player.giveExp(value);
                            }
                            return MechanicResult.SUCCESS;
                        }
                        Location where = target.location();
                        if (where.getWorld() == null || levels) {
                            return MechanicResult.FAIL;
                        }
                        where.getWorld().spawn(where, ExperienceOrb.class,
                                orb -> orb.setExperience(Math.max(1, value)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("currency", Mechanics.type(
                MechanicMeta.builder("currency")
                        .description("Adds to the target player's balance through Vault. "
                                + "A no-op when Vault is absent.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "how much; negative withdraws", "100", "a")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 100);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        return Mechanics.result(engine.hooks().vault()
                                .deposit(player, amount.asDouble(context, target)));
                    };
                }));

        into.put("run_command", Mechanics.type(
                MechanicMeta.builder("run_command")
                        .description("Runs a command as the target player.")
                        .requires(TargetKind.ENTITY)
                        .required("command", "the command, without the leading slash", "cmd", "c")
                        .build(),
                config -> {
                    Expression command = config.text("command", "");
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String line = resolve(command.asString(context, target), player);
                        engine.scheduler().atEntity(player, () -> player.performCommand(line));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("console_command", Mechanics.type(
                MechanicMeta.builder("console_command")
                        .description("Runs a command from the console.")
                        .requires(TargetKind.ANY)
                        .required("command", "the command, without the leading slash", "cmd", "c")
                        .build(),
                config -> {
                    Expression command = config.text("command", "");
                    return (context, target) -> {
                        String line = resolve(command.asString(context, target), target.player());
                        // Commands are dispatched on the global region: a
                        // command handler may touch anything, so the entity's
                        // thread is the wrong place for it on Folia.
                        engine.scheduler().run(() ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("quest_progress", Mechanics.type(
                MechanicMeta.builder("quest_progress")
                        .description("Advances a quest for the target player through AetherCore. "
                                + "A no-op with a startup report line when the hook is unavailable.")
                        .requires(TargetKind.ENTITY)
                        .required("quest", "the quest id", "q", "id")
                        .param("amount", "how much progress", "1", "a")
                        .param("complete", "force the quest complete rather than advancing it", "false")
                        .build(),
                config -> {
                    Expression quest = config.text("quest", "");
                    Expression amount = config.number("amount", 1);
                    boolean complete = config.bool("complete", false);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String id = quest.asString(context, target);
                        return Mechanics.result(complete
                                ? engine.hooks().quests().complete(player, id)
                                : engine.hooks().quests().progress(player, id,
                                Math.max(1, amount.asInt(context, target))));
                    };
                }));
    }

    private static String resolve(String command, Player player) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        return player == null ? line : line.replace("%player%", player.getName());
    }
}
