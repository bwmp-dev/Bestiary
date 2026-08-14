package dev.bwmp.bestiary.condition;

import dev.bwmp.bestiary.api.skill.Condition;
import dev.bwmp.bestiary.api.skill.ConditionMeta;
import dev.bwmp.bestiary.api.skill.ConditionType;
import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.function.Function;

public final class Conditions {

    @FunctionalInterface
    public interface Body {
        boolean test(SkillContext context, Target target);
    }

    private Conditions() {
    }

    public static ConditionType type(ConditionMeta meta, Function<MechanicConfig, Body> factory) {
        return new ConditionType() {
            @Override
            public ConditionMeta meta() {
                return meta;
            }

            @Override
            public Condition create(MechanicConfig config) {
                Body body = factory.apply(config);
                return new Condition() {
                    @Override
                    public ConditionMeta meta() {
                        return meta;
                    }

                    @Override
                    public boolean test(SkillContext context, Target target) {
                        return body.test(context, target);
                    }
                };
            }
        };
    }
}
