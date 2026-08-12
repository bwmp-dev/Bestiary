package dev.bwmp.bestiary.skill;

import dev.bwmp.bestiary.api.scheduler.BestiaryScheduler;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.api.skill.Mechanic;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.TargetKind;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four guards, exercised without a server.
 * <p>
 * The caster is a {@link Proxy} over Bukkit's {@code Entity} interface, which
 * is enough for the executor: it only ever asks whether the caster is valid,
 * where it is, and what its id is. That keeps these tests pure JVM, which is
 * the point of writing the guards before the executor.
 */
class SkillExecutorGuardsTest {

    private SkillExecutor executor;
    private Entity caster;
    private Map<String, CompiledSkill> skills;
    private AtomicInteger runs;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("bestiary-test");
        logger.setLevel(Level.OFF);
        executor = new SkillExecutor(noopScheduler(), logger);
        skills = new HashMap<>();
        executor.bind(id -> skills.get(id), entity -> null);
        caster = fakeEntity();
        runs = new AtomicInteger();
    }

    @Test
    void theMechanicBudgetStopsARunawayTree() {
        executor.limits(new ExecutionLimits(32, 50, 64, 1000.0d));

        // `loop` calls itself, so without the budget this never returns.
        skills.put("loop", skill("loop", List.of(
                counting(), callSkill("loop"))));

        executor.cast("loop", caster, null, origin(), List.of(), 1.0d, null);

        assertTrue(runs.get() > 0, "nothing ran at all");
        assertTrue(runs.get() <= 50, "budget of 50 exceeded: " + runs.get());
    }

    @Test
    void theDepthGuardStopsUnboundedRecursion() {
        executor.limits(new ExecutionLimits(5, 4000, 64, 1000.0d));

        skills.put("deep", skill("deep", List.of(counting(), callSkill("deep"))));
        executor.cast("deep", caster, null, origin(), List.of(), 1.0d, null);

        // One counting mechanic per frame, and frames stop at the depth limit.
        assertEquals(5, runs.get());
    }

    @Test
    void haltStopsTheEnclosingSkillOnly() {
        skills.put("inner", skill("inner", List.of(counting(), halting(), counting())));
        skills.put("outer", skill("outer", List.of(callSkill("inner"), counting())));

        executor.cast("outer", caster, null, origin(), List.of(), 1.0d, null);

        // inner: one counting then HALT. outer: one counting after the child.
        assertEquals(2, runs.get());
    }

    @Test
    void aSkillOnCooldownDoesNotRunTwice() {
        skills.put("cooling", new CompiledSkill("cooling", 100L, List.of(),
                List.of(counting()), "test", false));

        executor.cast("cooling", caster, null, origin(), List.of(), 1.0d, null);
        executor.cast("cooling", caster, null, origin(), List.of(), 1.0d, null);

        assertEquals(1, runs.get(), "the cooldown did not hold");
        assertTrue(executor.onCooldown(caster, "cooling"));
        assertTrue(executor.cooldownRemainingMillis(caster, "cooling") > 0);
    }

    @Test
    void clearingCooldownsLetsItRunAgain() {
        skills.put("cooling", new CompiledSkill("cooling", 100L, List.of(),
                List.of(counting()), "test", false));

        executor.cast("cooling", caster, null, origin(), List.of(), 1.0d, null);
        executor.clearCooldowns(caster);
        assertFalse(executor.onCooldown(caster, "cooling"));

        executor.cast("cooling", caster, null, origin(), List.of(), 1.0d, null);
        assertEquals(2, runs.get());
        // The second cast claims the cooldown again, which is the point of it.
        assertTrue(executor.onCooldown(caster, "cooling"));
    }

    @Test
    void aMechanicThatThrowsDoesNotStopTheSkill() {
        skills.put("fragile", skill("fragile", List.of(throwing(), counting())));
        executor.cast("fragile", caster, null, origin(), List.of(), 1.0d, null);
        assertEquals(1, runs.get());
    }

    @Test
    void callingAnUnknownSkillIsSurvivable() {
        skills.put("caller", skill("caller", List.of(callSkill("absent"), counting())));
        executor.cast("caller", caster, null, origin(), List.of(), 1.0d, null);
        assertEquals(1, runs.get());
    }

    // --- fixtures ---------------------------------------------------------

    private CompiledSkill skill(String id, List<CompiledLine> lines) {
        return new CompiledSkill(id, 0L, List.of(), lines, "test", false);
    }

    private CompiledLine counting() {
        return line("count", (context, target) -> {
            runs.incrementAndGet();
            return MechanicResult.SUCCESS;
        });
    }

    private CompiledLine halting() {
        return line("halt", (context, target) -> MechanicResult.HALT);
    }

    private CompiledLine throwing() {
        return line("boom", (context, target) -> {
            throw new IllegalStateException("deliberate");
        });
    }

    private CompiledLine callSkill(String id) {
        return line("skill", (context, target) -> context.runSkill(id, List.of(), 1.0d));
    }

    private CompiledLine line(String id, Body body) {
        MechanicMeta meta = MechanicMeta.builder(id).requires(TargetKind.NONE).build();
        Mechanic mechanic = new Mechanic() {
            @Override
            public MechanicMeta meta() {
                return meta;
            }

            @Override
            public MechanicResult execute(SkillContext context, Target target) {
                return body.run(context, target);
            }
        };
        return new CompiledLine(id, mechanic, null, List.of(), null, "test:" + id);
    }

    @FunctionalInterface
    private interface Body {
        MechanicResult run(SkillContext context, Target target);
    }

    private static Location origin() {
        return new Location(null, 0, 64, 0);
    }

    private static Entity fakeEntity() {
        UUID id = UUID.randomUUID();
        return (Entity) Proxy.newProxyInstance(
                SkillExecutorGuardsTest.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isValid":
                            return true;
                        case "getUniqueId":
                            return id;
                        case "getLocation":
                            return origin();
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return id.hashCode();
                        case "toString":
                            return "FakeEntity";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }

    private static BestiaryScheduler noopScheduler() {
        BestiaryTask task = new BestiaryTask() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        return new BestiaryScheduler() {
            @Override
            public BestiaryTask run(Runnable runnable) {
                runnable.run();
                return task;
            }

            @Override
            public BestiaryTask runLater(Runnable runnable, long delayTicks) {
                return task;
            }

            @Override
            public BestiaryTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
                return task;
            }

            @Override
            public BestiaryTask atEntity(Entity entity, Runnable runnable) {
                runnable.run();
                return task;
            }

            @Override
            public BestiaryTask atEntityTimer(Entity entity, Runnable runnable, long delay, long period) {
                return task;
            }

            @Override
            public BestiaryTask atEntityLater(Entity entity, Runnable runnable, long delayTicks) {
                // Deliberately does not run: a suspended tree must not resume
                // inside the same test tick, or the budget test would loop.
                return task;
            }

            @Override
            public BestiaryTask atLocation(Location location, Runnable runnable) {
                runnable.run();
                return task;
            }

            @Override
            public BestiaryTask async(Runnable runnable) {
                return task;
            }

            @Override
            public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
                return CompletableFuture.completedFuture(false);
            }

            @Override
            public boolean ownsRegion(Location location) {
                return true;
            }

            @Override
            public boolean isFolia() {
                return false;
            }
        };
    }
}
