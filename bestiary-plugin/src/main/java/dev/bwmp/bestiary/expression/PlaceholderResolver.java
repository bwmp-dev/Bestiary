package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

/** Resolves one namespace of {@code <...>} placeholders. */
public interface PlaceholderResolver {

    /**
     * The namespaces this resolver answers for, e.g. {@code caster}.
     * <p>
     * Declared rather than probed, because that is what lets the engine leave
     * {@code <gradient:#a:#b>} alone: a bracketed token whose first segment
     * belongs to nobody is not a Bestiary placeholder at all, and blanking it
     * would mangle every MiniMessage tag in the file.
     */
    java.util.Set<String> namespaces();

    /**
     * @param key the full placeholder text without the angle brackets
     * @return null when this resolver owns the namespace but not the key,
     *         which is the case that earns a warning
     */
    String resolve(String key, SkillContext context, Target target);
}
