package dev.bwmp.bestiary.hook;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

/**
 * WorldGuard regions and GriefPrevention claims, both entirely reflective.
 * <p>
 * Both are optional and both change their internals between major versions, so
 * a compile-time dependency would be a standing liability for two boolean
 * questions. Anything that fails to resolve disables that half with one report
 * line.
 */
public final class RegionHook {

    private Object regionContainer;
    private Method createQuery;
    private Method getApplicableRegions;
    private Method adaptLocation;

    private Object griefPreventionDataStore;
    private Method getClaimAt;

    RegionHook() {
        setUpWorldGuard();
        setUpGriefPrevention();
    }

    private void setUpWorldGuard() {
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            this.regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            this.createQuery = regionContainer.getClass().getMethod("createQuery");
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            this.adaptLocation = adapter.getMethod("adapt", Location.class);
            Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            this.getApplicableRegions = createQuery.getReturnType().getMethod("getApplicableRegions", weLocation);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.regionContainer = null;
        }
    }

    private void setUpGriefPrevention() {
        try {
            Class<?> griefPrevention = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object instance = griefPrevention.getField("instance").get(null);
            if (instance == null) {
                return;
            }
            this.griefPreventionDataStore = griefPrevention.getField("dataStore").get(instance);
            Class<?> claim = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            this.getClaimAt = griefPreventionDataStore.getClass()
                    .getMethod("getClaimAt", Location.class, boolean.class, claim);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            this.griefPreventionDataStore = null;
        }
    }

    public boolean worldGuardPresent() {
        return regionContainer != null;
    }

    public boolean griefPreventionPresent() {
        return griefPreventionDataStore != null;
    }

    /** Empty when WorldGuard is absent, which callers treat as "no regions". */
    public Set<String> regionsAt(Location location) {
        if (regionContainer == null || location == null) {
            return Set.of();
        }
        try {
            Object query = createQuery.invoke(regionContainer);
            Object adapted = adaptLocation.invoke(null, location);
            Object regions = getApplicableRegions.invoke(query, adapted);
            Object regionSet = regions.getClass().getMethod("getRegions").invoke(regions);
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Object region : (Iterable<?>) regionSet) {
                Object id = region.getClass().getMethod("getId").invoke(region);
                names.add(String.valueOf(id).toLowerCase(Locale.ROOT));
            }
            return names;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Set.of();
        }
    }

    public boolean inRegion(Location location, String regionId) {
        return regionsAt(location).contains(regionId.toLowerCase(Locale.ROOT));
    }

    public boolean inClaim(Location location) {
        if (griefPreventionDataStore == null || location == null) {
            return false;
        }
        try {
            return getClaimAt.invoke(griefPreventionDataStore, location, true, null) != null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
