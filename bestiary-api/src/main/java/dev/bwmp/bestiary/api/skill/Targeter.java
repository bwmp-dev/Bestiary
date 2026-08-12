package dev.bwmp.bestiary.api.skill;

import java.util.List;

/**
 * To whom, or where.
 * <p>
 * Results are never cached: a cached target list is a correctness bug, because
 * the world moved between the tick that resolved it and the tick that used it.
 * <p>
 * Every targeter is hard-capped by config ({@code max_targets}, default 64) and
 * supports {@code limit}, {@code sort} and {@code filter} generically, which is
 * applied by the engine rather than by each implementation.
 */
public interface Targeter {

    TargeterMeta meta();

    /**
     * @param source the targets this targeter composes over, from
     *               {@code @a of @b}; the context's own targets when there is
     *               no {@code of} clause
     */
    List<Target> resolve(SkillContext context, List<Target> source);
}
