package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The handful of engine services placeholders need, narrowed to an interface so
 * the resolvers stay unit-testable and so PlaceholderAPI stays optional.
 */
public interface PlaceholderServices {

    /** Null when the entity is not a Bestiary mob. */
    BestiaryMob mobOf(Entity entity);

    Object globalVariable(String name);

    /** Null when PlaceholderAPI is absent or the placeholder is unknown. */
    String placeholderApi(Player player, String placeholder);

    /** Plain text, MiniMessage stripped, for placeholders used in numeric contexts. */
    String plainText(String miniMessage);
}
