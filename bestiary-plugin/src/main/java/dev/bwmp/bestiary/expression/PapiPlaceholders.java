package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.skill.SkillContext;
import dev.bwmp.bestiary.api.skill.Target;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * The {@code papi} namespace, so a condition can gate on another plugin's state
 * — a Jobs level, a rank — without Bestiary hooking that plugin directly.
 * <p>
 * Resolved against the most relevant player in scope: the target when it is a
 * player, then the trigger, then the caster. That ordering is what makes
 * {@code <papi.vault_eco_balance>} inside a {@code damage} line mean the
 * victim's balance rather than the boss's.
 */
public final class PapiPlaceholders implements PlaceholderResolver {

    private final PlaceholderServices services;

    public PapiPlaceholders(PlaceholderServices services) {
        this.services = services;
    }

    @Override
    public Set<String> namespaces() {
        return Set.of("papi");
    }

    @Override
    public String resolve(String key, SkillContext context, Target target) {
        if (!key.regionMatches(true, 0, "papi.", 0, 5)) {
            return null;
        }
        String placeholder = key.substring(5);
        if (placeholder.isEmpty()) {
            return null;
        }
        return services.placeholderApi(playerInScope(context, target), placeholder);
    }

    private Player playerInScope(SkillContext context, Target target) {
        if (target != null && target.player() != null) {
            return target.player();
        }
        if (context == null) {
            return null;
        }
        if (context.triggerPlayer() != null) {
            return context.triggerPlayer();
        }
        return context.caster() instanceof Player ? (Player) context.caster() : null;
    }
}
