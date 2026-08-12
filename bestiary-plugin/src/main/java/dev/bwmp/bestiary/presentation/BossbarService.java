package dev.bwmp.bestiary.presentation;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.mob.BossbarDefinition;
import dev.bwmp.bestiary.api.scheduler.BestiaryTask;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-mob bossbars, visible within a configured range.
 * <p>
 * Multiple bosses stack per player without fighting each other for the bar
 * because each mob owns its own {@link BossBar} and only adds and removes
 * players — nothing ever reassigns a shared bar, which is how two bosses end up
 * flickering over one another.
 * <p>
 * The title is rendered from MiniMessage once per definition revision and only
 * re-rendered when it contains a placeholder, so a static title costs one
 * parse for the mob's entire life rather than one per tick.
 */
public final class BossbarService {

    private static final class Entry {
        private final BossBar bar;
        private final BossbarDefinition definition;
        private final boolean dynamicTitle;
        private final Set<UUID> viewers = new HashSet<>();
        private BestiaryTask task;

        private Entry(BossBar bar, BossbarDefinition definition, boolean dynamicTitle) {
            this.bar = bar;
            this.definition = definition;
            this.dynamicTitle = dynamicTitle;
        }
    }

    private final Engine engine;
    private final Map<UUID, Entry> bars = new ConcurrentHashMap<>();

    public BossbarService(Engine engine) {
        this.engine = engine;
    }

    public void attach(MobInstance instance) {
        BossbarDefinition definition = instance.definition().bossbar();
        if (!definition.enabled()) {
            return;
        }
        boolean dynamic = definition.title().indexOf('<') >= 0;
        BossBar bar = Bukkit.createBossBar(Text.render(titleFor(instance)),
                definition.color(), definition.style());
        bar.setProgress(1.0d);
        Entry entry = new Entry(bar, definition, dynamic);
        bars.put(instance.uniqueId(), entry);

        entry.task = engine.scheduler().atEntityTimer(instance.entity(),
                () -> update(instance), 20L, 10L);
    }

    public void detach(MobInstance instance) {
        Entry entry = bars.remove(instance.uniqueId());
        if (entry == null) {
            return;
        }
        if (entry.task != null) {
            entry.task.cancel();
        }
        entry.bar.removeAll();
    }

    public void refresh(MobInstance instance) {
        Entry entry = bars.get(instance.uniqueId());
        if (entry != null) {
            entry.bar.setTitle(Text.render(titleFor(instance)));
        }
    }

    private void update(MobInstance instance) {
        Entry entry = bars.get(instance.uniqueId());
        if (entry == null) {
            return;
        }
        LivingEntity entity = instance.entity();
        if (entity == null || !entity.isValid() || instance.removed()) {
            detach(instance);
            return;
        }

        if (entry.definition.showHealth()) {
            double max = engine.mobs().maxHealth(entity);
            entry.bar.setProgress(max <= 0 ? 1.0d
                    : Math.max(0.0d, Math.min(1.0d, entity.getHealth() / max)));
        }
        if (entry.dynamicTitle) {
            entry.bar.setTitle(Text.render(titleFor(instance)));
        }

        double range = entry.definition.range();
        Set<UUID> current = new HashSet<>();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) > range * range) {
                continue;
            }
            current.add(player.getUniqueId());
            if (entry.viewers.add(player.getUniqueId())) {
                entry.bar.addPlayer(player);
            }
        }
        entry.viewers.removeIf(id -> {
            if (current.contains(id)) {
                return false;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                entry.bar.removePlayer(player);
            }
            return true;
        });
    }

    /** The phase's title when it declares one, the mob's otherwise. */
    private String titleFor(MobInstance instance) {
        String phaseTitle = "";
        List<dev.bwmp.bestiary.mob.CompiledMob.Phase> phases = instance.compiled().phases();
        if (!phases.isEmpty()) {
            int index = Math.min(instance.phaseIndexValue(), phases.size() - 1);
            phaseTitle = phases.get(index).definition().bossbarTitle();
        }
        String source = phaseTitle.isEmpty() ? instance.definition().bossbar().title() : phaseTitle;
        return engine.expressions().compileText(source, "bossbar:" + instance.definition().id())
                .asString(engine.mobs().contextFor(instance),
                        dev.bwmp.bestiary.api.skill.Target.of(instance.entity()));
    }

    public void shutdown() {
        for (Entry entry : bars.values()) {
            if (entry.task != null) {
                entry.task.cancel();
            }
            entry.bar.removeAll();
        }
        bars.clear();
    }

    /** For {@code bossbar_create} on an arbitrary player-facing bar. */
    public BossBar createStandalone(String miniMessageTitle, org.bukkit.boss.BarColor color,
                                    org.bukkit.boss.BarStyle style) {
        return Bukkit.createBossBar(Text.render(miniMessageTitle), color, style);
    }
}
