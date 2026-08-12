package dev.bwmp.bestiary.targeter;

import dev.bwmp.bestiary.api.skill.MechanicConfig;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import dev.bwmp.bestiary.api.skill.Targeter;
import dev.bwmp.bestiary.api.skill.TargeterMeta;
import dev.bwmp.bestiary.api.skill.TargeterType;

import java.util.List;
import java.util.function.Function;

/** The {@link dev.bwmp.bestiary.mechanic.Mechanics} pattern, for targeters. */
public final class Targeters {

    @FunctionalInterface
    public interface Body {
        List<Target> resolve(SkillContext context, List<Target> source);
    }

    private Targeters() {
    }

    public static TargeterType type(TargeterMeta meta, Function<MechanicConfig, Body> factory) {
        return new TargeterType() {
            @Override
            public TargeterMeta meta() {
                return meta;
            }

            @Override
            public Targeter create(MechanicConfig config) {
                Body body = factory.apply(config);
                return new Targeter() {
                    @Override
                    public TargeterMeta meta() {
                        return meta;
                    }

                    @Override
                    public List<Target> resolve(SkillContext context, List<Target> source) {
                        return body.resolve(context, source);
                    }
                };
            }
        };
    }
}
