package dev.bwmp.bestiary.text;

import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.LegacyRenderer;

/**
 * MiniMessage in, platform-appropriate text out.
 * <p>
 * Everything Bestiary displays is MiniMessage source in config and a legacy
 * string by the time it reaches the server, for the reason Keystone's
 * {@code LegacyRenderer} spells out: a relocated {@code Component} is not the
 * type the server's API expects, so it could not be handed to
 * {@code Entity#customName(Component)} even where that method exists.
 * <p>
 * Rendering happens once per definition revision, not per tick.
 */
public final class Text {

    private Text() {
    }

    /** Admin-authored config text, full tag set. */
    public static String render(String miniMessage) {
        return miniMessage == null || miniMessage.isEmpty()
                ? ""
                : LegacyRenderer.renderMiniMessage(KeystoneText.legacyToMiniMessage(miniMessage));
    }

    /** Formatting stripped, for logs, placeholders and numeric contexts. */
    public static String plain(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }
        return KeystoneText.plain(KeystoneText.parse(KeystoneText.legacyToMiniMessage(miniMessage)));
    }

    /** Strips legacy section codes from a name the server handed back. */
    public static String stripLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(legacy.length());
        for (int index = 0; index < legacy.length(); index++) {
            char character = legacy.charAt(index);
            if (character == '§' && index + 1 < legacy.length()) {
                index++;
                continue;
            }
            builder.append(character);
        }
        return builder.toString();
    }
}
