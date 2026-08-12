package dev.bwmp.bestiary.drop;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.skill.CompiledCondition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rolls drop tables and hands out what they produce. */
public final class DropService {

    private static final int MAX_TABLE_DEPTH = 8;

    private final Engine engine;

    public DropService(Engine engine) {
        this.engine = engine;
    }

    /**
     * @param contributors everyone who dealt damage, most first; the killer is
     *                     included even when they contributed nothing, because
     *                     the last hit is still a claim
     */
    public void rollForDeath(MobInstance instance, Player killer, List<Player> contributors, Location at) {
        DropTable table = engine.content().dropTable(instance.definition().dropTable());
        if (table == null) {
            if (!instance.definition().dropTable().isEmpty()) {
                engine.logger().warning("Mob " + instance.definition().id()
                        + " names unknown drop table '" + instance.definition().dropTable() + "'");
            }
            return;
        }

        SkillContext context = engine.mobs().contextFor(instance);
        if (table.distribution() == DropTable.Distribution.SHARED) {
            List<ItemStack> items = new ArrayList<>();
            roll(table, instance, killer, context, at, items, 0);
            items.forEach(stack -> at.getWorld().dropItemNaturally(at, stack));
            return;
        }

        List<Player> candidates = new ArrayList<>(contributors);
        if (killer != null && !candidates.contains(killer)) {
            candidates.add(killer);
        }
        for (Player player : candidates) {
            if (!qualifies(table, instance, player, context)) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            roll(table, instance, player, context, at, items, 0);
            give(player, items, at);
        }
    }

    private boolean qualifies(DropTable table, MobInstance instance, Player player, SkillContext context) {
        if (table.minimumDamageShare() > 0.0d
                && instance.ledger().shareOf(player) < table.minimumDamageShare()) {
            return false;
        }
        return CompiledCondition.allPass(table.conditions(), context, Target.of(player));
    }

    /** Runs a table for one recipient, appending items rather than giving them. */
    public void roll(DropTable table, MobInstance instance, Player recipient, SkillContext context,
                     Location at, List<ItemStack> items, int depth) {
        if (depth > MAX_TABLE_DEPTH) {
            engine.logger().warning("Drop table '" + table.id() + "' nests more than "
                    + MAX_TABLE_DEPTH + " deep; stopping.");
            return;
        }

        List<DropEntry> eligible = new ArrayList<>();
        for (DropEntry entry : table.entries()) {
            Target target = recipient == null ? null : Target.of(recipient);
            if (!entry.conditions().isEmpty() && !CompiledCondition.allPass(entry.conditions(), context, target)) {
                continue;
            }
            eligible.add(entry);
        }
        if (eligible.isEmpty()) {
            return;
        }

        switch (table.mode()) {
            case ONE_OF:
                DropEntry chosen = pickWeighted(eligible);
                if (chosen != null) {
                    award(chosen, table, instance, recipient, context, at, items, depth);
                }
                break;
            case N_OF:
                List<DropEntry> pool = new ArrayList<>(eligible);
                for (int index = 0; index < table.count() && !pool.isEmpty(); index++) {
                    DropEntry entry = pickWeighted(pool);
                    pool.remove(entry);
                    award(entry, table, instance, recipient, context, at, items, depth);
                }
                break;
            case ALL:
            default:
                for (DropEntry entry : eligible) {
                    if (ThreadLocalRandom.current().nextDouble() > entry.chance()) {
                        continue;
                    }
                    award(entry, table, instance, recipient, context, at, items, depth);
                }
                break;
        }
    }

    private DropEntry pickWeighted(List<DropEntry> entries) {
        double total = 0.0d;
        for (DropEntry entry : entries) {
            total += entry.weight();
        }
        if (total <= 0.0d) {
            return entries.get(0);
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (DropEntry entry : entries) {
            roll -= entry.weight();
            if (roll <= 0.0d) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private void award(DropEntry entry, DropTable table, MobInstance instance, Player recipient,
                       SkillContext context, Location at, List<ItemStack> items, int depth) {
        Target target = recipient == null ? null : Target.of(recipient);
        int amount = Math.max(0, entry.amount().asInt(context, target));

        switch (entry.kind()) {
            case ITEM: {
                if (amount <= 0) {
                    return;
                }
                ItemStack stack = engine.hooks().sigil().resolveItem(entry.id(), amount);
                if (stack == null) {
                    engine.logger().warning("Drop table '" + table.id() + "' names unresolvable item '"
                            + entry.id() + "'");
                    return;
                }
                items.add(stack);
                break;
            }
            case TABLE: {
                DropTable nested = engine.content().dropTable(entry.id());
                if (nested == null) {
                    engine.logger().warning("Drop table '" + table.id() + "' references unknown table '"
                            + entry.id() + "'");
                    return;
                }
                roll(nested, instance, recipient, context, at, items, depth + 1);
                break;
            }
            case EXP:
                if (recipient != null) {
                    recipient.giveExp(amount);
                } else if (at.getWorld() != null) {
                    at.getWorld().spawn(at, org.bukkit.entity.ExperienceOrb.class,
                            orb -> orb.setExperience(amount));
                }
                break;
            case CURRENCY:
                if (recipient != null && !engine.hooks().vault().deposit(recipient, amount)) {
                    engine.logger().fine("Currency drop skipped: Vault is not present.");
                }
                break;
            case COMMAND:
                runCommand(entry.id(), recipient);
                break;
            case QUEST:
                if (recipient != null && !engine.hooks().quests().progress(recipient, entry.id(), Math.max(1, amount))) {
                    engine.logger().fine("Quest progress skipped for '" + entry.id() + "'.");
                }
                break;
            default:
                break;
        }
    }

    private void runCommand(String command, Player recipient) {
        String resolved = recipient == null ? command : command.replace("%player%", recipient.getName());
        engine.scheduler().run(() ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved));
    }

    private void give(Player player, List<ItemStack> items, Location fallback) {
        for (ItemStack stack : items) {
            player.getInventory().addItem(stack).values()
                    .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        }
    }

    /** For {@code /bestiary droptable test} and the {@code drop_table} mechanic. */
    public List<ItemStack> preview(DropTable table, MobInstance instance, Player recipient,
                                   SkillContext context, Location at) {
        List<ItemStack> items = new ArrayList<>();
        roll(table, instance, recipient, context, at, items, 0);
        return items;
    }
}
