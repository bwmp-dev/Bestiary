package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.Targeter;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * A targeter bound to its type, plus the {@code limit}, {@code sort} and
 * {@code filter} every targeter accepts.
 * <p>
 * Those three are applied here rather than by each implementation, which is
 * what removes most of the reason to write a bespoke targeter at all — and what
 * guarantees the {@code max_targets} cap cannot be forgotten by a third-party
 * targeter.
 */
public final class CompiledTargeter {

    private final String id;
    private final Targeter targeter;
    private final CompiledTargeter source;
    private final int limit;
    private final SortMode sort;
    private final List<CompiledCondition> filter;
    private final int hardCap;
    private final Function<LivingEntity, BestiaryMob> mobLookup;

    public CompiledTargeter(String id, Targeter targeter, CompiledTargeter source, int limit, SortMode sort,
                            List<CompiledCondition> filter, int hardCap,
                            Function<LivingEntity, BestiaryMob> mobLookup) {
        this.id = id;
        this.targeter = targeter;
        this.source = source;
        this.limit = limit;
        this.sort = sort;
        this.filter = List.copyOf(filter);
        this.hardCap = Math.max(1, hardCap);
        this.mobLookup = mobLookup;
    }

    public List<Target> resolve(SkillContext context) {
        List<Target> upstream = source == null ? context.targets() : source.resolve(context);

        List<Target> resolved;
        try {
            resolved = targeter.resolve(context, upstream);
        } catch (RuntimeException exception) {
            throw new SkillFailure("targeter '" + id + "' failed: " + exception, exception);
        }
        if (resolved == null || resolved.isEmpty()) {
            return List.of();
        }

        List<Target> working = new ArrayList<>(resolved);
        working.removeIf(target -> target == null || target.isStale());

        if (!filter.isEmpty()) {
            working.removeIf(target -> !CompiledCondition.allPass(filter, context, target));
        }

        applySort(working, context);

        // The hard cap is applied after limit rather than instead of it, so a
        // limit above the cap is silently the cap rather than a way round it.
        int ceiling = limit > 0 ? Math.min(limit, hardCap) : hardCap;
        if (working.size() > ceiling) {
            working = new ArrayList<>(working.subList(0, ceiling));
        }

        if (context.tracing()) {
            context.trace("  @" + id + " -> " + working.size() + " target(s)");
        }
        return working;
    }

    private void applySort(List<Target> targets, SkillContext context) {
        if (sort == SortMode.NONE || targets.size() < 2) {
            return;
        }
        if (sort == SortMode.RANDOM) {
            Collections.shuffle(targets);
            return;
        }

        Location origin = context.origin();
        switch (sort) {
            case NEAREST:
                targets.sort(Comparator.comparingDouble(target -> distance(origin, target)));
                break;
            case FARTHEST:
                targets.sort(Comparator.comparingDouble((Target target) -> distance(origin, target)).reversed());
                break;
            case LOWEST_HEALTH:
                targets.sort(Comparator.comparingDouble(CompiledTargeter::health));
                break;
            case HIGHEST_HEALTH:
                targets.sort(Comparator.comparingDouble(CompiledTargeter::health).reversed());
                break;
            case THREAT:
                BestiaryMob caster = context.casterLiving() == null ? null : mobLookup.apply(context.casterLiving());
                if (caster == null) {
                    targets.sort(Comparator.comparingDouble(target -> distance(origin, target)));
                } else {
                    targets.sort(Comparator.comparingDouble((Target target) -> {
                        Player player = target.player();
                        return player == null ? 0.0d : caster.threat(player).orElse(0.0d);
                    }).reversed());
                }
                break;
            default:
                break;
        }
    }

    private static double distance(Location origin, Target target) {
        Location location = target.location();
        if (location.getWorld() == null || !location.getWorld().equals(origin.getWorld())) {
            return Double.MAX_VALUE;
        }
        return location.distanceSquared(origin);
    }

    private static double health(Target target) {
        LivingEntity living = target.living();
        return living == null ? Double.MAX_VALUE : living.getHealth();
    }

    public String id() {
        return id;
    }

    public CompiledTargeter source() {
        return source;
    }

    @Override
    public String toString() {
        return "@" + id + (source == null ? "" : " of " + source);
    }
}
