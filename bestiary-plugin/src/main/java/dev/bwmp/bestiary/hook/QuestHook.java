package dev.bwmp.bestiary.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * The {@code quest_progress} mechanic, against AetherCore's quest module.
 * <p>
 * The service is resolved through Bukkit's service manager and the progress
 * method is looked up by shape rather than by name, because AetherCore's
 * published {@code QuestService} interface currently exposes start / complete /
 * reset and no incremental counter. Where no matching method exists the
 * mechanic is a documented no-op with one report line at startup — which is
 * what every integration seam does when the target is absent, and the honest
 * behaviour when it is present but cannot be driven.
 */
public final class QuestHook {

    private Object service;
    private Method progressMethod;
    private Method completeMethod;

    QuestHook() {
        try {
            Class<?> serviceClass = Class.forName("dev.aether.aethercore.modules.quest.QuestService");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceClass);
            this.service = provider == null ? null : provider.getProvider();
            if (service == null) {
                return;
            }
            this.progressMethod = findProgress(serviceClass);
            this.completeMethod = serviceClass.getMethod("forceComplete", Player.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.service = null;
        }
    }

    private static Method findProgress(Class<?> serviceClass) {
        for (String name : new String[]{"progress", "advance", "recordProgress", "increment"}) {
            for (Method method : serviceClass.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters[0] == Player.class && parameters[1] == String.class
                        && (parameters[2] == int.class || parameters[2] == Integer.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    public boolean present() {
        return service != null;
    }

    /** True when incremental progress can actually be driven on this build. */
    public boolean supportsProgress() {
        return service != null && progressMethod != null;
    }

    public boolean progress(Player player, String questId, int amount) {
        if (!supportsProgress()) {
            return false;
        }
        try {
            progressMethod.invoke(service, player, questId, amount);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public boolean complete(Player player, String questId) {
        if (service == null || completeMethod == null) {
            return false;
        }
        try {
            Object result = completeMethod.invoke(service, player, questId);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
