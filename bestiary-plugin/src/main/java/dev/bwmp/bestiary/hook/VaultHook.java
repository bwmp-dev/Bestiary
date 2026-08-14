package dev.bwmp.bestiary.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

public final class VaultHook {

    private Object economy;
    private Method depositMethod;
    private Method withdrawMethod;

    VaultHook() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(economyClass);
            if (provider == null) {
                return;
            }
            this.economy = provider.getProvider();
            this.depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            this.withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.economy = null;
        }
    }

    public boolean present() {
        return economy != null;
    }

    /** No-op without Vault; the caller reports that once rather than per drop. */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || amount == 0.0d) {
            return false;
        }
        try {
            if (amount > 0) {
                depositMethod.invoke(economy, player, amount);
            } else {
                withdrawMethod.invoke(economy, player, -amount);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
