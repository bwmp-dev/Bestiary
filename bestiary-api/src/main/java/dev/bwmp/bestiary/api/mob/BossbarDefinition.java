package dev.bwmp.bestiary.api.mob;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

/**
 * A mob's bossbar. Title is MiniMessage source with placeholders, rendered once
 * per definition revision rather than per tick.
 */
public final class BossbarDefinition {

    public static final BossbarDefinition NONE = new BossbarDefinition(false, "", BarColor.WHITE,
            BarStyle.SOLID, 48.0d, true);

    private final boolean enabled;
    private final String title;
    private final BarColor color;
    private final BarStyle style;
    private final double range;
    private final boolean showHealth;

    public BossbarDefinition(boolean enabled, String title, BarColor color, BarStyle style,
                             double range, boolean showHealth) {
        this.enabled = enabled;
        this.title = title == null ? "" : title;
        this.color = color == null ? BarColor.WHITE : color;
        this.style = style == null ? BarStyle.SOLID : style;
        this.range = range <= 0 ? 48.0d : range;
        this.showHealth = showHealth;
    }

    public boolean enabled() {
        return enabled;
    }

    public String title() {
        return title;
    }

    public BarColor color() {
        return color;
    }

    public BarStyle style() {
        return style;
    }

    /** Players outside this radius do not see the bar. */
    public double range() {
        return range;
    }

    /** False pins the bar full, for a phase indicator rather than a health bar. */
    public boolean showHealth() {
        return showHealth;
    }
}
