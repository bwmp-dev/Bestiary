package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;

import java.util.Locale;
import java.util.Set;

/** The {@code mob} and {@code global} variable namespaces. */
public final class VariablePlaceholders implements PlaceholderResolver {

    private final PlaceholderServices services;

    public VariablePlaceholders(PlaceholderServices services) {
        this.services = services;
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("mob", "global");
    }

    @Override
    public String resolve(String key, SkillContext context, Target target) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("global.var.")) {
            Object value = services.globalVariable(lower.substring("global.var.".length()));
            return value == null ? null : String.valueOf(value);
        }
        if (lower.startsWith("mob.var.")) {
            if (context == null) {
                return null;
            }
            BestiaryMob mob = services.mobOf(context.caster());
            if (mob == null) {
                return null;
            }
            Object value = mob.variables().get(lower.substring("mob.var.".length()));
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
