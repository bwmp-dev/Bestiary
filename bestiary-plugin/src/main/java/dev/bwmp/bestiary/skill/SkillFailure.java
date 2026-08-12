package dev.bwmp.bestiary.skill;

/**
 * A skill execution could not continue.
 * <p>
 * Caught at the execution boundary and logged once per skill per minute with
 * the skill id and the offending node path. Silently truncating would be worse
 * than failing: a boss that half-fires is a bug report nobody can reproduce.
 */
public class SkillFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SkillFailure(String message) {
        super(message);
    }

    public SkillFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
