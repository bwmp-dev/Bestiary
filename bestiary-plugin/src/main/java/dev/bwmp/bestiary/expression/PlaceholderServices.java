package dev.bwmp.bestiary.expression;

import dev.bwmp.bestiary.api.mob.BestiaryMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface PlaceholderServices {

    /** Null when the entity is not a Bestiary mob. */
    BestiaryMob mobOf(Entity entity);

    Object globalVariable(String name);

    /** Null when PlaceholderAPI is absent or the placeholder is unknown. */
    String placeholderApi(Player player, String placeholder);

    String plainText(String miniMessage);
}
