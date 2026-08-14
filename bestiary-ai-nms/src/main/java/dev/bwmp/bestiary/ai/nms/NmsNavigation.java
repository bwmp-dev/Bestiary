package dev.bwmp.bestiary.ai.nms;

import org.bukkit.entity.LivingEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * The four capabilities the Goal API genuinely cannot reach: navigation
 * swapping, {@code MoveControl} and {@code LookControl} replacement, and brain
 * access.
 * <p>
 * Active on 1.20.5+ only, where Paper's runtime is Mojang-mapped and member
 * names are stable. There is no compile-time server dependency: no
 * Mojang-mapped server artifact is reachable from plain Maven, so every member
 * is reached through a {@link MethodHandle} resolved once at load against
 * Mojang-mapped names.
 * <p>
 * <b>If any handle fails to resolve the whole tier disables itself.</b> A
 * half-working NMS layer — flying navigation that silently did not apply while
 * the move control did — is far harder to diagnose than one that says it is off.
 */
public final class NmsNavigation {

    private static final String NAV_PACKAGE = "net.minecraft.world.entity.ai.navigation.";
    private static final String CONTROL_PACKAGE = "net.minecraft.world.entity.ai.control.";

    private final boolean available;

    private MethodHandle getHandle;
    private MethodHandle getLevel;
    private MethodHandle setNavigation;
    private MethodHandle setMoveControl;
    private MethodHandle setLookControl;
    private MethodHandle getBrain;

    private Constructor<?> groundNavigation;
    private Constructor<?> flyingNavigation;
    private Constructor<?> amphibiousNavigation;
    private Constructor<?> climbingNavigation;

    public NmsNavigation() {
        boolean resolved = false;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Class<?> craftEntity = Class.forName(craftClass("entity.CraftEntity"));
            Method handleMethod = craftEntity.getMethod("getHandle");
            handleMethod.setAccessible(true);
            getHandle = lookup.unreflect(handleMethod);

            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            Method levelMethod = nmsEntity.getMethod("level");
            levelMethod.setAccessible(true);
            getLevel = lookup.unreflect(levelMethod);

            Class<?> nmsMob = Class.forName("net.minecraft.world.entity.Mob");
            setNavigation = setter(lookup, nmsMob, "navigation");
            setMoveControl = setter(lookup, nmsMob, "moveControl");
            setLookControl = setter(lookup, nmsMob, "lookControl");

            Class<?> nmsLiving = Class.forName("net.minecraft.world.entity.LivingEntity");
            Method brainMethod = nmsLiving.getMethod("getBrain");
            brainMethod.setAccessible(true);
            getBrain = lookup.unreflect(brainMethod);

            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            groundNavigation = navigationConstructor("GroundPathNavigation", nmsMob, level);
            flyingNavigation = navigationConstructor("FlyingPathNavigation", nmsMob, level);
            amphibiousNavigation = navigationConstructor("WaterBoundPathNavigation", nmsMob, level);
            climbingNavigation = navigationConstructor("WallClimberNavigation", nmsMob, level);

            resolved = groundNavigation != null && flyingNavigation != null
                    && amphibiousNavigation != null && climbingNavigation != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            resolved = false;
        }
        this.available = resolved;
    }

    /** Called by the bridge before anything else is attempted. */
    public boolean available() {
        return available;
    }

    /**
     * Swaps a mob's pathfinder.
     * <p>
     * The most valuable capability here: on a floating-island world, giving a
     * ground mob flying pathfinding is the difference between an arena boss
     * that works and one that walks off the edge.
     */
    public boolean applyNavigation(LivingEntity entity, String kind) {
        if (!available) {
            return false;
        }
        Constructor<?> constructor;
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "flying":
                constructor = flyingNavigation;
                break;
            case "amphibious":
                constructor = amphibiousNavigation;
                break;
            case "climbing":
                constructor = climbingNavigation;
                break;
            case "ground":
                constructor = groundNavigation;
                break;
            default:
                return false;
        }
        try {
            Object handle = getHandle.invoke(entity);
            Object level = getLevel.invoke(handle);
            Object navigation = constructor.newInstance(handle, level);
            setNavigation.invoke(handle, navigation);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Replaces the movement controller; a goal's tick fights it rather than replacing it. */
    public boolean applyMoveControl(LivingEntity entity, String className) {
        return applyControl(entity, className, setMoveControl);
    }

    public boolean applyLookControl(LivingEntity entity, String className) {
        return applyControl(entity, className, setLookControl);
    }

    private boolean applyControl(LivingEntity entity, String className, MethodHandle setter) {
        if (!available || className == null || className.isBlank()) {
            return false;
        }
        try {
            Class<?> type = Class.forName(className.indexOf('.') >= 0
                    ? className
                    : CONTROL_PACKAGE + className);
            Object handle = getHandle.invoke(entity);
            Constructor<?> constructor = type.getConstructor(
                    Class.forName("net.minecraft.world.entity.Mob"));
            constructor.setAccessible(true);
            setter.invoke(handle, constructor.newInstance(handle));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * The brain of a behaviour-based mob — Warden, Piglin, Villager, Allay.
     * Those use {@code Brain} rather than the goal selector, so {@code MobGoals}
     * sees nothing to remove.
     *
     * @return the brain, or null
     */
    public Object brainOf(LivingEntity entity) {
        if (!available) {
            return null;
        }
        try {
            return getBrain.invoke(getHandle.invoke(entity));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean clearBrain(LivingEntity entity) {
        Object brain = brainOf(entity);
        if (brain == null) {
            return false;
        }
        try {
            Method clearMemories = brain.getClass().getMethod("clearMemories");
            clearMemories.setAccessible(true);
            clearMemories.invoke(brain);
            Method stopAll = brain.getClass().getMethod("stopAll",
                    Class.forName("net.minecraft.server.level.ServerLevel"),
                    Class.forName("net.minecraft.world.entity.LivingEntity"));
            stopAll.setAccessible(true);
            Object handle = getHandle.invoke(entity);
            stopAll.invoke(brain, getLevel.invoke(handle), handle);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static MethodHandle setter(MethodHandles.Lookup lookup, Class<?> owner, String fieldName)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return lookup.unreflectSetter(field);
    }

    private static Constructor<?> navigationConstructor(String simpleName, Class<?> mob, Class<?> level) {
        try {
            Constructor<?> constructor = Class.forName(NAV_PACKAGE + simpleName)
                    .getConstructor(mob, level);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * The CraftBukkit package moved out of a versioned name partway through the
     * supported band, so both shapes are tried rather than assuming either.
     */
    private static String craftClass(String suffix) throws ClassNotFoundException {
        String base = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
        try {
            return Class.forName(base + "." + suffix).getName();
        } catch (ClassNotFoundException ignored) {
            return Class.forName("org.bukkit.craftbukkit." + suffix).getName();
        }
    }
}
