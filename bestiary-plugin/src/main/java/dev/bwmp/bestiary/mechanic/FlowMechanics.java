package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.skill.Execution;
import dev.bwmp.bestiary.skill.SkillContextImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Flow control.
 * <p>
 * Every one of these reaches the executor through {@link SkillContext} rather
 * than by calling anything itself, which is what keeps the four guards seeing
 * the whole tree. {@code delay} in particular does not
 * schedule anything of its own: it tells the execution to pause, and the
 * executor reschedules the remainder at the caster — so a delayed skill on
 * Folia resumes on the correct region thread, and one whose caster died during
 * the pause is dropped rather than resumed against a stale entity.
 */
public final class FlowMechanics {

    private FlowMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("skill", Mechanics.type(
                MechanicMeta.builder("skill")
                        .description("Runs another skill against the current targets.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("skill", "the skill id", "s", "name")
                        .param("power", "multiplies the child's power", "1.0", "p")
                        .param("per_target", "run once per target instead of once with all of them",
                                "false", "pt")
                        .build(),
                config -> {
                    String skillId = config.raw("skill", "");
                    Expression power = config.number("power", 1.0d);
                    boolean perTarget = config.bool("per_target", false);
                    if (skillId.isEmpty()) {
                        throw new IllegalArgumentException("required parameter 'skill' is missing");
                    }
                    return (context, target) -> {
                        List<dev.bwmp.bestiary.api.skill.Target> targets = perTarget
                                ? List.of(target)
                                : context.targets();
                        return context.runSkill(skillId, targets, power.asDouble(context, target));
                    };
                }));

        // A NONE-targeting mechanic still runs once per resolved target, so a
        // `skill` line under a targeter that found eight players would fire
        // eight times. These two guard against that by only acting on the first.
        into.put("delay", Mechanics.type(
                MechanicMeta.builder("delay")
                        .description("Pauses the rest of the skill tree.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .param("ticks", "how long", "20", "t", "duration", "d")
                        .build(),
                config -> {
                    long ticks = config.ticks("ticks", 20L);
                    return (context, target) -> {
                        Execution execution = execution(context);
                        if (execution == null) {
                            return MechanicResult.FAIL;
                        }
                        execution.delay(ticks);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("repeat", Mechanics.type(
                MechanicMeta.builder("repeat")
                        .description("Runs a skill several times, optionally with a gap between them.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .param("times", "how many", "3", "amount", "count", "t")
                        .param("interval", "ticks between runs", "0", "i", "delay")
                        .required("skill", "the skill to repeat; an inline skills: block also works", "s")
                        .build(),
                config -> {
                    Expression times = config.number("times", 3);
                    long interval = config.ticks("interval", 0L);
                    String skillId = config.raw("skill", "");
                    if (skillId.isEmpty()) {
                        throw new IllegalArgumentException(
                                "'repeat' needs either skill=<id> or a nested skills: block");
                    }
                    return (context, target) -> {
                        int count = Math.max(0, times.asInt(context, target));
                        if (count == 0) {
                            return MechanicResult.PASS;
                        }

                        if (interval <= 0) {
                            // Pushed in reverse so the first repetition ends up
                            // on top of the stack and therefore runs first.
                            for (int index = count - 1; index >= 0; index--) {
                                if (!context.charge(1)) {
                                    return MechanicResult.FAIL;
                                }
                                context.runSkill(skillId, context.targets(), 1.0d);
                            }
                            return MechanicResult.SUCCESS;
                        }

                        // With a gap, each repetition is a separate cast at the
                        // caster: spacing them inside one execution would mean
                        // holding the whole tree open for the full duration,
                        // and the guards would charge the wait as work.
                        var skill = engine.content().skill(skillId);
                        if (skill == null) {
                            return MechanicResult.FAIL;
                        }
                        var caster = context.caster();
                        var trigger = context.trigger();
                        var origin = context.origin();
                        var targets = List.copyOf(context.targets());
                        double power = context.power();
                        for (int index = 0; index < count; index++) {
                            long delay = interval * index;
                            if (delay == 0) {
                                context.runSkill(skillId, targets, 1.0d);
                                continue;
                            }
                            engine.scheduler().atEntityLater(caster, () -> {
                                if (caster.isValid()) {
                                    engine.executor().cast(skill, caster, trigger, origin,
                                            targets, power, null, null);
                                }
                            }, delay);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("random_skill", Mechanics.type(
                MechanicMeta.builder("random_skill")
                        .description("Runs one of several skills, chosen uniformly.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("skills", "comma-separated skill ids", "s", "list")
                        .build(),
                config -> {
                    List<String> skills = config.stringList("skills");
                    if (skills.isEmpty()) {
                        throw new IllegalArgumentException("'random_skill' needs skills=<a,b,c>");
                    }
                    return (context, target) -> {
                        String chosen = skills.get(ThreadLocalRandom.current().nextInt(skills.size()));
                        return context.runSkill(chosen, context.targets(), 1.0d);
                    };
                }));

        into.put("weighted_skill", Mechanics.type(
                MechanicMeta.builder("weighted_skill")
                        .description("Runs one of several skills, chosen by weight.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("skills", "comma-separated id:weight pairs", "s", "list")
                        .build(),
                config -> {
                    List<String> entries = config.stringList("skills");
                    if (entries.isEmpty()) {
                        throw new IllegalArgumentException(
                                "'weighted_skill' needs skills=<a:3,b:1>");
                    }
                    List<String> ids = new ArrayList<>();
                    List<Double> weights = new ArrayList<>();
                    for (String entry : entries) {
                        int colon = entry.lastIndexOf(':');
                        if (colon <= 0) {
                            ids.add(entry);
                            weights.add(1.0d);
                            continue;
                        }
                        ids.add(entry.substring(0, colon));
                        try {
                            weights.add(Math.max(0.0d, Double.parseDouble(entry.substring(colon + 1))));
                        } catch (NumberFormatException exception) {
                            throw new IllegalArgumentException("'" + entry + "' is not id:weight");
                        }
                    }
                    return (context, target) -> {
                        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
                        if (total <= 0.0d) {
                            return MechanicResult.FAIL;
                        }
                        double roll = ThreadLocalRandom.current().nextDouble(total);
                        for (int index = 0; index < ids.size(); index++) {
                            roll -= weights.get(index);
                            if (roll <= 0.0d) {
                                return context.runSkill(ids.get(index), context.targets(), 1.0d);
                            }
                        }
                        return context.runSkill(ids.get(ids.size() - 1), context.targets(), 1.0d);
                    };
                }));

        into.put("cancel_event", Mechanics.type(
                MechanicMeta.builder("cancel_event")
                        .description("Cancels the Bukkit event that triggered this skill, if there is one.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .build(),
                config -> (context, target) -> {
                    if (context.event() == null) {
                        return MechanicResult.FAIL;
                    }
                    context.cancelEvent();
                    return MechanicResult.SUCCESS;
                }));

        into.put("cancel_skill", Mechanics.type(
                MechanicMeta.builder("cancel_skill")
                        .description("Drops every remaining line at every depth.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .build(),
                config -> (context, target) -> {
                    Execution execution = execution(context);
                    if (execution != null) {
                        execution.cancelAll();
                    }
                    return MechanicResult.HALT;
                }));

        into.put("stop", Mechanics.type(
                MechanicMeta.builder("stop")
                        .description("Stops the remaining lines of the enclosing skill only.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .build(),
                config -> (context, target) -> MechanicResult.HALT));

        into.put("signal", Mechanics.type(
                MechanicMeta.builder("signal")
                        .description("Sends a named signal, firing ~onSignal bindings on the targets.")
                        .requires(TargetKind.ENTITY)
                        .required("name", "the signal name", "s", "signal")
                        .build(),
                config -> {
                    Expression name = config.text("name", "");
                    return (context, target) -> {
                        if (target.entity() == null) {
                            return MechanicResult.FAIL;
                        }
                        var instance = engine.mobs().instance(target.entity());
                        if (instance == null) {
                            return MechanicResult.FAIL;
                        }
                        instance.signal(name.asString(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("sync", Mechanics.type(
                MechanicMeta.builder("sync")
                        .description("Ensures the rest of the tree runs on the caster's own thread. "
                                + "A no-op unless the tree is currently off it.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .build(),
                config -> (context, target) -> {
                    Execution execution = execution(context);
                    if (execution != null) {
                        // One tick's pause is what puts the continuation back on
                        // the entity's scheduler, which is the whole effect.
                        execution.delay(1L);
                    }
                    return MechanicResult.SUCCESS;
                }));

        into.put("async", Mechanics.type(
                MechanicMeta.builder("async")
                        .description("Runs a skill off the server threads. It must not touch world state.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("skill", "the skill to run off-thread", "s")
                        .build(),
                config -> {
                    String skillId = config.raw("skill", "");
                    if (skillId.isEmpty()) {
                        throw new IllegalArgumentException("'async' needs skill=<id>");
                    }
                    return (context, target) -> {
                        var skill = engine.content().skill(skillId);
                        if (skill == null) {
                            return MechanicResult.FAIL;
                        }
                        var caster = context.caster();
                        var trigger = context.trigger();
                        var origin = context.origin();
                        var targets = List.copyOf(context.targets());
                        engine.scheduler().async(() ->
                                engine.executor().cast(skill, caster, trigger, origin, targets,
                                        context.power(), null, null));
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static Execution execution(SkillContext context) {
        return context instanceof SkillContextImpl ? ((SkillContextImpl) context).execution() : null;
    }
}
