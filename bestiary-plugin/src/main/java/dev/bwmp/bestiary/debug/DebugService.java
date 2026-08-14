package dev.bwmp.bestiary.debug;

import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * {@code /bestiary debug}: a live trace of targeter resolution and condition
 * results, attached to a running mob.
 * <p>
 * Also the timing mode — per-skill wall clock, so the
 * expensive thing is identifiable rather than guessed at.
 */
public final class DebugService {

    private static final class Watch {
        private final UUID watcher;
        private final Map<String, Long> nanosBySkill = new ConcurrentHashMap<>();
        private final Map<String, Integer> countsBySkill = new ConcurrentHashMap<>();

        private Watch(UUID watcher) {
            this.watcher = watcher;
        }
    }

    private final MessageService messages;
    private final Map<UUID, Watch> byMob = new ConcurrentHashMap<>();

    public DebugService(MessageService messages) {
        this.messages = messages;
    }

    public void attach(Player watcher, MobInstance instance) {
        byMob.put(instance.uniqueId(), new Watch(watcher.getUniqueId()));
    }

    public void detach(Player watcher) {
        byMob.entrySet().removeIf(entry -> entry.getValue().watcher.equals(watcher.getUniqueId()));
    }

    public boolean watching(MobInstance instance) {
        return byMob.containsKey(instance.uniqueId());
    }

    /** Null when nobody is watching, which is the fast path and the usual one. */
    public Consumer<String> tracerFor(MobInstance instance) {
        Watch watch = byMob.get(instance.uniqueId());
        if (watch == null) {
            return null;
        }
        Player watcher = Bukkit.getPlayer(watch.watcher);
        if (watcher == null || !watcher.isOnline()) {
            byMob.remove(instance.uniqueId());
            return null;
        }
        return line -> messages.sendComponent(watcher,
                KeystoneText.parse("<dark_gray>[<gold>" + instance.definition().id().getKey()
                        + "<dark_gray>]<gray> " + KeystoneText.escape(line)));
    }

    /** Records one skill's wall clock against the mob being watched. */
    public void record(MobInstance instance, String skillId, long nanos) {
        Watch watch = byMob.get(instance.uniqueId());
        if (watch == null) {
            return;
        }
        watch.nanosBySkill.merge(skillId, nanos, Long::sum);
        watch.countsBySkill.merge(skillId, 1, Integer::sum);
    }

    public java.util.List<String> timings(MobInstance instance) {
        Watch watch = byMob.get(instance.uniqueId());
        if (watch == null) {
            return java.util.List.of();
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        watch.nanosBySkill.forEach((skill, nanos) -> {
            int count = watch.countsBySkill.getOrDefault(skill, 1);
            lines.add(String.format(java.util.Locale.ROOT, "%s: %d run(s), %.3f ms total, %.3f ms mean",
                    skill, count, nanos / 1.0e6d, nanos / 1.0e6d / count));
        });
        lines.sort(String::compareTo);
        return lines;
    }

    public void clear() {
        byMob.clear();
    }
}
