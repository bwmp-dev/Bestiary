package dev.bwmp.bestiary.api.skill;

/** A factory that builds a {@link Targeter} from parsed parameters. */
public interface TargeterType {

    TargeterMeta meta();

    Targeter create(MechanicConfig config);
}
