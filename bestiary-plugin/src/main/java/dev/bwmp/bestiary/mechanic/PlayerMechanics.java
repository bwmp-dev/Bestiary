package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.text.Text;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-facing output.
 * <p>
 * Every one of these is scheduled at the <em>player</em>, never at the mob. A
 * global timer reading each player's state is a cross-region read, invisible
 * everywhere except Folia — Sigil found that bug the hard way, so it is
 * designed out here.
 */
public final class PlayerMechanics {

    /** Standalone bars created by {@code bossbar_create}, keyed by id per player. */
    private static final Map<String, BossBar> BARS = new ConcurrentHashMap<>();

    private PlayerMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("message", Mechanics.type(
                MechanicMeta.builder("message")
                        .description("Sends chat text to the target player.")
                        .requires(TargetKind.ENTITY)
                        .required("message", "MiniMessage source", "msg", "m", "text")
                        .build(),
                config -> {
                    Expression message = config.text("message", "");
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String rendered = message.asString(context, target);
                        engine.scheduler().atEntity(player, () ->
                                engine.messages().sendComponent(player,
                                        dev.bwmp.keystone.text.KeystoneText.parse(
                                                dev.bwmp.keystone.text.KeystoneText
                                                        .legacyToMiniMessage(rendered))));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("actionbar", Mechanics.type(
                MechanicMeta.builder("actionbar")
                        .description("Sends text to the target player's action bar.")
                        .requires(TargetKind.ENTITY)
                        .required("message", "MiniMessage source", "msg", "m", "text")
                        .build(),
                config -> {
                    Expression message = config.text("message", "");
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String rendered = Text.render(message.asString(context, target));
                        engine.scheduler().atEntity(player, () ->
                                player.spigot().sendMessage(
                                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(rendered)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("title", Mechanics.type(
                MechanicMeta.builder("title")
                        .description("Shows a title and subtitle to the target player.")
                        .requires(TargetKind.ENTITY)
                        .param("title", "MiniMessage source", "", "t")
                        .param("subtitle", "MiniMessage source", "", "st", "sub")
                        .param("fade_in", "ticks", "10", "fi")
                        .param("stay", "ticks", "40")
                        .param("fade_out", "ticks", "10", "fo")
                        .build(),
                config -> {
                    Expression title = config.text("title", "");
                    Expression subtitle = config.text("subtitle", "");
                    int fadeIn = (int) config.ticks("fade_in", 10L);
                    int stay = (int) config.ticks("stay", 40L);
                    int fadeOut = (int) config.ticks("fade_out", 10L);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String main = Text.render(title.asString(context, target));
                        String sub = Text.render(subtitle.asString(context, target));
                        engine.scheduler().atEntity(player, () ->
                                player.sendTitle(main, sub, fadeIn, stay, fadeOut));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("bossbar_create", Mechanics.type(
                MechanicMeta.builder("bossbar_create")
                        .description("Creates or replaces a named bar and shows it to the target player.")
                        .requires(TargetKind.ENTITY)
                        .required("id", "bar id, unique per player", "name", "n")
                        .param("title", "MiniMessage source", "", "t")
                        .param("colour", "bar colour", "white", "color", "c")
                        .param("style", "solid, segmented_6, segmented_10, segmented_12, segmented_20", "solid")
                        .param("progress", "0..1", "1.0", "p")
                        .param("duration", "remove after this long; 0 keeps it", "0", "d")
                        .build(),
                config -> {
                    String id = config.raw("id", "");
                    Expression title = config.text("title", "");
                    BarColor colour = config.enumValue(BarColor.class, "colour", BarColor.WHITE);
                    BarStyle style = config.enumValue(BarStyle.class, "style", BarStyle.SOLID);
                    Expression progress = config.number("progress", 1.0d);
                    long duration = config.ticks("duration", 0L);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        String key = barKey(player.getUniqueId(), id);
                        BossBar existing = BARS.remove(key);
                        if (existing != null) {
                            existing.removeAll();
                        }
                        BossBar bar = engine.bossbars().createStandalone(
                                title.asString(context, target), colour, style);
                        bar.setProgress(clamp(progress.asDouble(context, target)));
                        bar.addPlayer(player);
                        BARS.put(key, bar);
                        if (duration > 0) {
                            engine.scheduler().atEntityLater(player, () -> {
                                BossBar expired = BARS.remove(key);
                                if (expired != null) {
                                    expired.removeAll();
                                }
                            }, duration);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("bossbar_update", Mechanics.type(
                MechanicMeta.builder("bossbar_update")
                        .description("Changes an existing named bar.")
                        .requires(TargetKind.ENTITY)
                        .required("id", "bar id", "name", "n")
                        .param("title", "MiniMessage source; empty leaves it", "", "t")
                        .param("progress", "0..1; negative leaves it", "-1", "p")
                        .build(),
                config -> {
                    String id = config.raw("id", "");
                    Expression title = config.text("title", "");
                    Expression progress = config.number("progress", -1);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        BossBar bar = BARS.get(barKey(player.getUniqueId(), id));
                        if (bar == null) {
                            return MechanicResult.FAIL;
                        }
                        String rendered = title.asString(context, target);
                        if (!rendered.isEmpty()) {
                            bar.setTitle(Text.render(rendered));
                        }
                        double value = progress.asDouble(context, target);
                        if (value >= 0) {
                            bar.setProgress(clamp(value));
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("bossbar_remove", Mechanics.type(
                MechanicMeta.builder("bossbar_remove")
                        .description("Removes a named bar from the target player.")
                        .requires(TargetKind.ENTITY)
                        .required("id", "bar id", "name", "n")
                        .build(),
                config -> {
                    String id = config.raw("id", "");
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        BossBar bar = BARS.remove(barKey(player.getUniqueId(), id));
                        if (bar == null) {
                            return MechanicResult.FAIL;
                        }
                        bar.removeAll();
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("toast", Mechanics.type(
                MechanicMeta.builder("toast")
                        .description("Shows an advancement-style popup. Approximated with a title "
                                + "where the server offers no advancement API.")
                        .requires(TargetKind.ENTITY)
                        .required("message", "MiniMessage source", "msg", "m", "text")
                        .param("icon", "material shown beside it, where supported", "diamond")
                        .build(),
                config -> {
                    Expression message = config.text("message", "");
                    Material icon = dev.bwmp.bestiary.util.Registries.material(config.raw("icon", "diamond"));
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        // Granting a real toast means registering an
                        // advancement, which is a datapack concern and would
                        // leave a permanent entry in every player's file. The
                        // approximation is named in the docs rather than
                        // pretending to be the real thing.
                        String rendered = Text.render(message.asString(context, target));
                        engine.scheduler().atEntity(player, () ->
                                player.sendTitle(" ", rendered, 5, 40, 10));
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static String barKey(UUID player, String id) {
        return player + "|" + id.toLowerCase(Locale.ROOT);
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /** Called on quit so a disconnected player's bars do not leak. */
    public static void forget(UUID player) {
        BARS.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(player.toString())) {
                return false;
            }
            entry.getValue().removeAll();
            return true;
        });
    }

    public static void shutdown() {
        BARS.values().forEach(BossBar::removeAll);
        BARS.clear();
    }
}
