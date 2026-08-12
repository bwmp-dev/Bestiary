package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.api.skill.Mechanic;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.function.Function;

/**
 * Turns a declaration and a body into a {@link dev.bwmp.bestiary.api.skill.MechanicType}.
 * <p>
 * The built-in library is large, and written as one class per mechanic it would
 * be several hundred files of boilerplate around a two-line body. This keeps
 * the declaration and the behaviour adjacent, which is what makes the library
 * readable as a list of what each mechanic does.
 */
public final class Mechanics {

    /** The body of a mechanic: what happens, per resolved target. */
    @FunctionalInterface
    public interface Body {
        MechanicResult run(SkillContext context, Target target);
    }

    private Mechanics() {
    }

    public static dev.bwmp.bestiary.api.skill.MechanicType type(
            MechanicMeta meta, Function<MechanicConfig, Body> factory) {
        return new dev.bwmp.bestiary.api.skill.MechanicType() {
            @Override
            public MechanicMeta meta() {
                return meta;
            }

            @Override
            public Mechanic create(MechanicConfig config) {
                Body body = factory.apply(config);
                return new Mechanic() {
                    @Override
                    public MechanicMeta meta() {
                        return meta;
                    }

                    @Override
                    public MechanicResult execute(SkillContext context, Target target) {
                        return body.run(context, target);
                    }
                };
            }
        };
    }

    /** {@code SUCCESS} when something happened, {@code FAIL} when it could not. */
    public static MechanicResult result(boolean fired) {
        return fired ? MechanicResult.SUCCESS : MechanicResult.FAIL;
    }
}
