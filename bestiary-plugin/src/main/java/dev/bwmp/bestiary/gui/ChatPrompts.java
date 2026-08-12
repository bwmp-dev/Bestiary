package dev.bwmp.bestiary.gui;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.keystone.text.KeystoneText;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One-shot "type it in chat" prompts for the GUI.
 * <p>
 * An anvil rename caps at 50 characters and strips formatting, and half the
 * values worth editing here are expressions or MiniMessage. Chat has neither
 * limit.
 * <p>
 * The chat event is asynchronous, so the answer is handed back on the player's
 * own scheduler rather than acted on where it arrives.
 */
public final class ChatPrompts implements Listener {

    private final Engine engine;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompts(Engine engine) {
        this.engine = engine;
    }

    public void ask(Player player, String question, Consumer<String> answer) {
        pending.put(player.getUniqueId(), answer);
        engine.messages().sendComponent(player, KeystoneText.parse(
                "<gold>Bestiary <dark_gray>| <white>" + KeystoneText.escape(question)));
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> answer = pending.remove(event.getPlayer().getUniqueId());
        if (answer == null) {
            return;
        }
        event.setCancelled(true);
        String typed = event.getMessage().trim();
        if (typed.equalsIgnoreCase("cancel")) {
            engine.messages().sendComponent(event.getPlayer(),
                    KeystoneText.parse("<gray>Cancelled."));
            return;
        }
        engine.scheduler().atEntity(event.getPlayer(), () -> answer.accept(typed));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
