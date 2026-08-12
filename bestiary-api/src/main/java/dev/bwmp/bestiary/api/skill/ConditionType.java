package dev.bwmp.bestiary.api.skill;

/** A factory that builds a {@link Condition} from parsed parameters. */
public interface ConditionType {

    ConditionMeta meta();

    Condition create(MechanicConfig config);
}
