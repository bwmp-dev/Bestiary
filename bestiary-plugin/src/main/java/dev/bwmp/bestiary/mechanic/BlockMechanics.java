package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.util.Registries;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terrain.
 * <p>
 * {@code block_mask} is the interesting one: it changes what a player sees
 * without changing the world, so an arena can appear to fill with lava for the
 * duration of a phase and leave nothing behind if the server crashes mid-fight.
 * Every real terrain change is reversible on a timer for the same reason.
 */
public final class BlockMechanics {

    /** Blocks currently masked per player, so an unmask restores exactly what was hidden. */
    private static final Map<UUID, Set<Location>> MASKED = new ConcurrentHashMap<>();

    private BlockMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("set_block", Mechanics.type(
                MechanicMeta.builder("set_block")
                        .description("Replaces the block at the target, optionally reverting later.")
                        .requires(TargetKind.LOCATION)
                        .required("material", "block material", "m", "block", "type")
                        .param("duration", "revert after this long; 0 is permanent", "0", "d")
                        .param("physics", "apply block physics", "false")
                        .build(),
                config -> {
                    Material material = requireMaterial(config.raw("material", ""));
                    long duration = config.ticks("duration", 0L);
                    boolean physics = config.bool("physics", false);
                    return (context, target) -> {
                        Location location = target.location();
                        if (location.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        Block block = location.getBlock();
                        BlockData previous = block.getBlockData();
                        block.setType(material, physics);
                        if (duration > 0) {
                            engine.scheduler().atLocation(location, () ->
                                    engine.scheduler().runLater(() -> block.setBlockData(previous, physics),
                                            duration));
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("place_block", Mechanics.type(
                MechanicMeta.builder("place_block")
                        .description("Places a block only where there is currently air.")
                        .requires(TargetKind.LOCATION)
                        .required("material", "block material", "m", "block", "type")
                        .param("duration", "revert after this long; 0 is permanent", "0", "d")
                        .build(),
                config -> {
                    Material material = requireMaterial(config.raw("material", ""));
                    long duration = config.ticks("duration", 0L);
                    return (context, target) -> {
                        Block block = target.location().getBlock();
                        if (!block.getType().isAir()) {
                            return MechanicResult.FAIL;
                        }
                        block.setType(material, false);
                        if (duration > 0) {
                            engine.scheduler().runLater(() -> {
                                if (block.getType() == material) {
                                    block.setType(Material.AIR, false);
                                }
                            }, duration);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("break_block", Mechanics.type(
                MechanicMeta.builder("break_block")
                        .description("Breaks the block at the target.")
                        .requires(TargetKind.LOCATION)
                        .param("drop", "drop the usual items", "false")
                        .param("radius", "break a sphere of this radius instead of one block", "0", "r")
                        .param("ignore", "comma-separated materials to leave alone", "bedrock,barrier")
                        .build(),
                config -> {
                    boolean drop = config.bool("drop", false);
                    Expression radius = config.number("radius", 0);
                    Set<Material> ignore = materials(config.stringList("ignore"));
                    return (context, target) -> {
                        Location centre = target.location();
                        if (centre.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        double range = radius.asDouble(context, target);
                        List<Block> blocks = new ArrayList<>();
                        if (range <= 0) {
                            blocks.add(centre.getBlock());
                        } else {
                            for (Location point : Shapes.box(centre, range * 2, 1.0d)) {
                                if (point.distanceSquared(centre) <= range * range) {
                                    blocks.add(point.getBlock());
                                }
                            }
                        }
                        boolean any = false;
                        for (Block block : blocks) {
                            if (block.getType().isAir() || ignore.contains(block.getType())) {
                                continue;
                            }
                            if (drop) {
                                block.breakNaturally();
                            } else {
                                block.setType(Material.AIR, false);
                            }
                            any = true;
                        }
                        return Mechanics.result(any);
                    };
                }));

        into.put("block_mask", Mechanics.type(
                MechanicMeta.builder("block_mask")
                        .description("Shows a different block to nearby players without changing the world.")
                        .requires(TargetKind.LOCATION)
                        .required("material", "the block to show", "m", "block", "type")
                        .param("radius", "mask a sphere of this radius", "0", "r")
                        .param("duration", "revert after this long", "5s", "d")
                        .param("view_distance", "who sees it", "48", "vd")
                        .build(),
                config -> {
                    Material material = requireMaterial(config.raw("material", ""));
                    Expression radius = config.number("radius", 0);
                    long duration = config.ticks("duration", 100L);
                    Expression viewDistance = config.number("view_distance", 48);
                    return (context, target) -> {
                        Location centre = target.location();
                        if (centre.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        List<Location> points = collect(centre, radius.asDouble(context, target));
                        double range = viewDistance.asDouble(context, target);
                        BlockData data = material.createBlockData();
                        for (Player player : centre.getWorld().getPlayers()) {
                            if (player.getLocation().distanceSquared(centre) > range * range) {
                                continue;
                            }
                            Set<Location> masked = MASKED.computeIfAbsent(player.getUniqueId(),
                                    id -> ConcurrentHashMap.newKeySet());
                            for (Location point : points) {
                                player.sendBlockChange(point, data);
                                masked.add(point);
                            }
                        }
                        if (duration > 0) {
                            engine.scheduler().runLater(() -> unmask(centre, points), duration);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("block_unmask", Mechanics.type(
                MechanicMeta.builder("block_unmask")
                        .description("Restores the real blocks a mask was hiding.")
                        .requires(TargetKind.LOCATION)
                        .param("radius", "unmask a sphere of this radius", "0", "r")
                        .build(),
                config -> {
                    Expression radius = config.number("radius", 0);
                    return (context, target) -> {
                        Location centre = target.location();
                        if (centre.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        unmask(centre, collect(centre, radius.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("block_wave", Mechanics.type(
                MechanicMeta.builder("block_wave")
                        .description("Throws falling blocks upward along a shape, for a ground-rupture look.")
                        .requires(TargetKind.ANY)
                        .param("material", "block to throw; empty uses whatever is underfoot", "", "m", "block")
                        .param("shape", "a nested shape block", "")
                        .param("velocity", "upward speed", "0.4", "v")
                        .param("duration", "how long the blocks last", "3s", "d")
                        .build(),
                config -> {
                    String materialName = config.raw("material", "");
                    Material material = materialName.isEmpty() ? null : requireMaterial(materialName);
                    dev.bwmp.bestiary.util.ShapeSpec shape =
                            dev.bwmp.bestiary.util.ShapeSpec.of(config.section("shape"));
                    Expression velocity = config.number("velocity", 0.4d);
                    long duration = config.ticks("duration", 60L);
                    return (context, target) -> {
                        Location centre = target.location();
                        if (centre.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        boolean any = false;
                        for (Location point : shape.points(context, target, centre)) {
                            if (!context.charge(1)) {
                                break;
                            }
                            Block ground = groundUnder(point);
                            if (ground == null) {
                                continue;
                            }
                            BlockData data = material != null
                                    ? material.createBlockData()
                                    : ground.getBlockData();
                            FallingBlock falling = point.getWorld().spawnFallingBlock(
                                    ground.getLocation().add(0.5d, 1.0d, 0.5d), data);
                            falling.setDropItem(false);
                            falling.setHurtEntities(false);
                            falling.setPersistent(false);
                            falling.setVelocity(new org.bukkit.util.Vector(0,
                                    velocity.asDouble(context, target), 0));
                            engine.scheduler().atEntityLater(falling, () -> {
                                if (falling.isValid()) {
                                    falling.remove();
                                }
                            }, duration);
                            any = true;
                        }
                        return Mechanics.result(any);
                    };
                }));

        into.put("block_physics", Mechanics.type(
                MechanicMeta.builder("block_physics")
                        .description("Turns the block at the target into a falling block.")
                        .requires(TargetKind.LOCATION)
                        .param("velocity", "initial upward speed", "0.2", "v")
                        .build(),
                config -> {
                    Expression velocity = config.number("velocity", 0.2d);
                    return (context, target) -> {
                        Location location = target.location();
                        Block block = location.getBlock();
                        if (block.getType().isAir() || location.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        BlockData data = block.getBlockData();
                        block.setType(Material.AIR, false);
                        FallingBlock falling = location.getWorld().spawnFallingBlock(
                                block.getLocation().add(0.5d, 0, 0.5d), data);
                        falling.setDropItem(false);
                        falling.setVelocity(new org.bukkit.util.Vector(0,
                                velocity.asDouble(context, target), 0));
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static void unmask(Location centre, List<Location> points) {
        if (centre.getWorld() == null) {
            return;
        }
        for (Player player : centre.getWorld().getPlayers()) {
            Set<Location> masked = MASKED.get(player.getUniqueId());
            if (masked == null) {
                continue;
            }
            for (Location point : points) {
                if (masked.remove(point)) {
                    player.sendBlockChange(point, point.getBlock().getBlockData());
                }
            }
            if (masked.isEmpty()) {
                MASKED.remove(player.getUniqueId());
            }
        }
    }

    private static List<Location> collect(Location centre, double radius) {
        if (radius <= 0) {
            return List.of(centre.getBlock().getLocation());
        }
        List<Location> points = new ArrayList<>();
        int range = (int) Math.ceil(radius);
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    if (x * x + y * y + z * z > radius * radius) {
                        continue;
                    }
                    points.add(centre.clone().add(x, y, z).getBlock().getLocation());
                }
            }
        }
        return points;
    }

    private static Block groundUnder(Location point) {
        if (point.getWorld() == null) {
            return null;
        }
        for (int drop = 0; drop < 6; drop++) {
            Block block = point.clone().subtract(0, drop, 0).getBlock();
            if (!block.getType().isAir() && block.getType().isSolid()) {
                return block;
            }
        }
        return null;
    }

    private static Material requireMaterial(String name) {
        Material material = Registries.material(name);
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("'" + name + "' is not a block material");
        }
        return material;
    }

    private static Set<Material> materials(List<String> names) {
        Set<Material> set = new HashSet<>();
        for (String name : names) {
            Material material = Registries.material(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }

    /** Called on quit so a disconnected player's mask set does not leak. */
    public static void forget(UUID player) {
        MASKED.remove(player);
    }
}
