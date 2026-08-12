package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.bestiary.util.Registries;
import dev.bwmp.bestiary.util.ShapeSpec;
import dev.bwmp.bestiary.util.Shapes;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the player sees or hears.
 * <p>
 * Particles are emitted per player rather than through {@code World#spawnParticle}
 * so the view-distance cap in {@code config.yml} actually applies: a 2048-point
 * sphere painted for a player 300 blocks away is bandwidth spent on nothing.
 * <p>
 * Sounds are passed to the {@code String} overload of {@code playSound} and
 * never resolved to the {@code Sound} enum, which stopped being an enum partway
 * through the supported version band.
 */
public final class PresentationMechanics {

    private PresentationMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("particle", Mechanics.type(
                MechanicMeta.builder("particle")
                        .description("Draws particles, optionally in a shape.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "flame", "p", "type")
                        .param("amount", "particles per point", "1", "a", "count")
                        .param("speed", "particle speed", "0", "s")
                        .param("spread", "random offset on each axis", "0", "offset", "o")
                        .param("spread_y", "vertical offset, when it differs", "-1", "oy")
                        .param("y_offset", "raise the whole effect", "0", "height", "h")
                        .param("colour", "RRGGBB, for dust and entity_effect", "", "color", "c")
                        .param("size", "dust size", "1", "scale")
                        .param("material", "block or item for block/item particles", "")
                        .param("shape", "a nested shape block", "")
                        .build(),
                config -> {
                    Particle particle = resolveParticle(config.raw("particle", "flame"));
                    Expression amount = config.number("amount", 1);
                    Expression speed = config.number("speed", 0);
                    Expression spread = config.number("spread", 0);
                    Expression spreadY = config.number("spread_y", -1);
                    Expression yOffset = config.number("y_offset", 0);
                    String colour = config.raw("colour", "");
                    Expression size = config.number("size", 1);
                    Material material = Registries.material(config.raw("material", ""));
                    ShapeSpec shape = config.contains("shape")
                            ? ShapeSpec.of(config.section("shape"))
                            : null;
                    return (context, target) -> {
                        Location centre = target.location().add(0, yOffset.asDouble(context, target), 0);
                        List<Location> points = shape == null
                                ? List.of(centre)
                                : shape.points(context, target, centre);
                        int count = Math.max(1, amount.asInt(context, target));
                        double offset = spread.asDouble(context, target);
                        double offsetY = spreadY.asDouble(context, target);
                        if (offsetY < 0) {
                            offsetY = offset;
                        }
                        Object data = particleData(particle, colour, size.asDouble(context, target), material);
                        for (Location point : points) {
                            emit(engine, point, particle, count, offset, offsetY, offset,
                                    speed.asDouble(context, target), data);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("particle_trail", Mechanics.type(
                MechanicMeta.builder("particle_trail")
                        .description("Draws a line of particles from the origin to the target.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "end_rod", "p", "type")
                        .param("spacing", "distance between particles", "0.4", "step")
                        .param("amount", "particles per point", "1", "a")
                        .build(),
                config -> {
                    Particle particle = resolveParticle(config.raw("particle", "end_rod"));
                    Expression spacing = config.number("spacing", 0.4d);
                    Expression amount = config.number("amount", 1);
                    return (context, target) -> {
                        Location from = context.origin().add(0, 1, 0);
                        Location to = target.isLiving() ? target.eyeLocation() : target.location();
                        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
                            return MechanicResult.FAIL;
                        }
                        for (Location point : Shapes.line(from, to, spacing.asDouble(context, target))) {
                            emit(engine, point, particle, amount.asInt(context, target),
                                    0, 0, 0, 0, null);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("particle_orbital", Mechanics.type(
                MechanicMeta.builder("particle_orbital")
                        .description("Draws a ring that advances each time it runs.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "flame", "p", "type")
                        .param("radius", "ring radius", "1.5", "r")
                        .param("points", "points per ring", "12")
                        .param("height", "vertical offset", "1", "h")
                        .build(),
                config -> {
                    Particle particle = resolveParticle(config.raw("particle", "flame"));
                    Expression radius = config.number("radius", 1.5d);
                    Expression points = config.number("points", 12);
                    Expression height = config.number("height", 1);
                    return (context, target) -> {
                        // Phase comes from wall-clock rather than a counter so
                        // the mechanic stays stateless and every caster's ring
                        // rotates rather than sitting still.
                        double phase = (System.currentTimeMillis() % 3600L) / 3600.0d * Math.PI * 2;
                        Location centre = target.location().add(0, height.asDouble(context, target), 0);
                        int count = Math.max(1, points.asInt(context, target));
                        double distance = radius.asDouble(context, target);
                        for (int index = 0; index < count; index++) {
                            double angle = phase + 2 * Math.PI * index / count;
                            emit(engine, centre.clone().add(Math.cos(angle) * distance, 0,
                                    Math.sin(angle) * distance), particle, 1, 0, 0, 0, 0, null);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("particle_tornado", Mechanics.type(
                MechanicMeta.builder("particle_tornado")
                        .description("A widening helix.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "cloud", "p", "type")
                        .param("radius", "top radius", "3", "r")
                        .param("height", "how tall", "4", "h")
                        .param("points", "total points", "80")
                        .param("turns", "how many revolutions", "4")
                        .build(),
                config -> {
                    Particle particle = resolveParticle(config.raw("particle", "cloud"));
                    Expression radius = config.number("radius", 3);
                    Expression height = config.number("height", 4);
                    Expression points = config.number("points", 80);
                    Expression turns = config.number("turns", 4);
                    return (context, target) -> {
                        Location base = target.location();
                        int count = Math.max(2, points.asInt(context, target));
                        double top = radius.asDouble(context, target);
                        double tall = height.asDouble(context, target);
                        double revolutions = turns.asDouble(context, target);
                        for (int index = 0; index < count; index++) {
                            double progress = index / (double) (count - 1);
                            double angle = 2 * Math.PI * revolutions * progress;
                            double distance = top * progress;
                            emit(engine, base.clone().add(Math.cos(angle) * distance, tall * progress,
                                    Math.sin(angle) * distance), particle, 1, 0, 0, 0, 0, null);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("particle_box", Mechanics.type(
                MechanicMeta.builder("particle_box")
                        .description("Outlines a cube, for telegraphing an area.")
                        .requires(TargetKind.ANY)
                        .param("particle", "particle name", "flame", "p", "type")
                        .param("size", "edge length", "4", "s")
                        .param("spacing", "distance between particles", "0.5")
                        .build(),
                config -> {
                    Particle particle = resolveParticle(config.raw("particle", "flame"));
                    Expression size = config.number("size", 4);
                    Expression spacing = config.number("spacing", 0.5d);
                    return (context, target) -> {
                        for (Location point : Shapes.cube(target.location(), size.asDouble(context, target),
                                spacing.asDouble(context, target))) {
                            emit(engine, point, particle, 1, 0, 0, 0, 0, null);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("sound", Mechanics.type(
                MechanicMeta.builder("sound")
                        .description("Plays a sound at the target for everyone nearby.")
                        .requires(TargetKind.ANY)
                        .required("sound", "namespaced sound key", "s", "name")
                        .param("volume", "how loud", "1.0", "v")
                        .param("pitch", "0.5 to 2.0", "1.0", "p")
                        .param("category", "sound category", "master", "c")
                        .build(),
                config -> {
                    String sound = config.raw("sound", "");
                    Expression volume = config.number("volume", 1.0d);
                    Expression pitch = config.number("pitch", 1.0d);
                    SoundCategory category = config.enumValue(SoundCategory.class, "category",
                            SoundCategory.MASTER);
                    return (context, target) -> {
                        Location location = target.location();
                        World world = location.getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        world.playSound(location, sound, category,
                                volume.asFloat(context, target), pitch.asFloat(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("sound_to_player", Mechanics.type(
                MechanicMeta.builder("sound_to_player")
                        .description("Plays a sound only the target player hears.")
                        .requires(TargetKind.ENTITY)
                        .required("sound", "namespaced sound key", "s", "name")
                        .param("volume", "how loud", "1.0", "v")
                        .param("pitch", "0.5 to 2.0", "1.0", "p")
                        .build(),
                config -> {
                    String sound = config.raw("sound", "");
                    Expression volume = config.number("volume", 1.0d);
                    Expression pitch = config.number("pitch", 1.0d);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        player.playSound(player.getLocation(), sound,
                                volume.asFloat(context, target), pitch.asFloat(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("stop_sound", Mechanics.type(
                MechanicMeta.builder("stop_sound")
                        .description("Stops a sound for the target player.")
                        .requires(TargetKind.ENTITY)
                        .required("sound", "namespaced sound key", "s", "name")
                        .build(),
                config -> {
                    String sound = config.raw("sound", "");
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        player.stopSound(sound);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("explosion", Mechanics.type(
                MechanicMeta.builder("explosion")
                        .description("A real explosion, with damage and optional terrain change.")
                        .requires(TargetKind.ANY)
                        .param("power", "TNT is 4.0", "3.0", "yield", "strength")
                        .param("fire", "leave fires behind", "false")
                        .param("break_blocks", "destroy terrain", "false", "blocks")
                        .build(),
                config -> {
                    Expression power = config.number("power", 3.0d);
                    boolean fire = config.bool("fire", false);
                    boolean breakBlocks = config.bool("break_blocks", false);
                    return (context, target) -> {
                        Location location = target.location();
                        World world = location.getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        world.createExplosion(location, power.asFloat(context, target), fire, breakBlocks,
                                context.caster());
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("fake_explosion", Mechanics.type(
                MechanicMeta.builder("fake_explosion")
                        .description("The look and sound of an explosion with none of the consequences.")
                        .requires(TargetKind.ANY)
                        .param("large", "the big particle", "true")
                        .build(),
                config -> {
                    boolean large = config.bool("large", true);
                    Particle particle = resolveParticle(large ? "explosion_emitter" : "explosion");
                    return (context, target) -> {
                        Location location = target.location();
                        if (location.getWorld() == null) {
                            return MechanicResult.FAIL;
                        }
                        emit(engine, location, particle, 1, 0, 0, 0, 0, null);
                        location.getWorld().playSound(location, "entity.generic.explode",
                                SoundCategory.HOSTILE, 1.0f, 1.0f);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("lightning", Mechanics.type(
                MechanicMeta.builder("lightning")
                        .description("A real lightning strike.")
                        .requires(TargetKind.ANY)
                        .build(),
                config -> (context, target) -> {
                    Location location = target.location();
                    if (location.getWorld() == null) {
                        return MechanicResult.FAIL;
                    }
                    location.getWorld().strikeLightning(location);
                    return MechanicResult.SUCCESS;
                }));

        into.put("fake_lightning", Mechanics.type(
                MechanicMeta.builder("fake_lightning")
                        .description("The flash and the crack, no fire and no damage.")
                        .requires(TargetKind.ANY)
                        .build(),
                config -> (context, target) -> {
                    Location location = target.location();
                    if (location.getWorld() == null) {
                        return MechanicResult.FAIL;
                    }
                    location.getWorld().strikeLightningEffect(location);
                    return MechanicResult.SUCCESS;
                }));

        into.put("firework", Mechanics.type(
                MechanicMeta.builder("firework")
                        .description("Detonates a firework, for a phase transition or a kill flourish.")
                        .requires(TargetKind.ANY)
                        .param("colour", "RRGGBB", "ffaa00", "color", "c")
                        .param("fade", "RRGGBB of the fade colour", "", "fade_colour")
                        .param("shape", "ball, ball_large, star, burst or creeper", "ball")
                        .param("trail", "leave a trail", "true")
                        .param("flicker", "twinkle", "false")
                        .param("instant", "detonate immediately", "true")
                        .build(),
                config -> {
                    Color colour = parseColour(config.raw("colour", "ffaa00"), Color.ORANGE);
                    String fadeRaw = config.raw("fade", "");
                    Color fade = fadeRaw.isEmpty() ? null : parseColour(fadeRaw, Color.WHITE);
                    FireworkEffect.Type shape = parseFireworkShape(config.raw("shape", "ball"));
                    boolean trail = config.bool("trail", true);
                    boolean flicker = config.bool("flicker", false);
                    boolean instant = config.bool("instant", true);
                    return (context, target) -> {
                        Location location = target.location();
                        World world = location.getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        Firework firework = world.spawn(location, Firework.class);
                        FireworkMeta meta = firework.getFireworkMeta();
                        FireworkEffect.Builder effect = FireworkEffect.builder()
                                .withColor(colour).with(shape).trail(trail).flicker(flicker);
                        if (fade != null) {
                            effect.withFade(fade);
                        }
                        meta.addEffect(effect.build());
                        meta.setPower(1);
                        firework.setFireworkMeta(meta);
                        if (instant) {
                            firework.detonate();
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("hologram", Mechanics.type(
                MechanicMeta.builder("hologram")
                        .description("A floating line of text, using a display entity.")
                        .requires(TargetKind.ANY)
                        .required("text", "MiniMessage source", "t", "message", "msg")
                        .param("duration", "how long before it disappears", "3s", "d")
                        .param("y_offset", "height above the target", "1.5", "oy", "height")
                        .param("rise", "blocks it drifts upward over its lifetime", "0.5")
                        .build(),
                config -> {
                    Expression text = config.text("text", "");
                    long duration = config.ticks("duration", 60L);
                    Expression yOffset = config.number("y_offset", 1.5d);
                    Expression rise = config.number("rise", 0.5d);
                    return (context, target) -> {
                        Location location = target.location().add(0, yOffset.asDouble(context, target), 0);
                        World world = location.getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        // Display entities arrive at 1.19.4, which is the floor,
                        // so there is no armour stand fallback to write or test.
                        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
                            entity.setText(Text.render(text.asString(context, target)));
                            entity.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                            entity.setPersistent(false);
                        });
                        double drift = rise.asDouble(context, target);
                        engine.scheduler().atEntityLater(display, () -> {
                            if (display.isValid()) {
                                display.remove();
                            }
                        }, duration);
                        if (drift != 0.0d) {
                            engine.scheduler().atEntityTimer(display, () -> {
                                if (display.isValid()) {
                                    display.teleport(display.getLocation().add(0, drift / (duration / 2.0d), 0));
                                }
                            }, 2L, 2L);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("glow", Mechanics.type(
                MechanicMeta.builder("glow")
                        .description("Outlines the target for a while.")
                        .requires(TargetKind.ENTITY)
                        .param("duration", "how long", "3s", "d")
                        .build(),
                config -> {
                    long duration = config.ticks("duration", 60L);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setGlowing(true);
                        engine.scheduler().atEntityLater(entity, () -> {
                            if (entity.isValid()) {
                                entity.setGlowing(false);
                            }
                        }, duration);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("equip", Mechanics.type(
                MechanicMeta.builder("equip")
                        .description("Puts an item in one of the target's equipment slots.")
                        .requires(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i")
                        .param("slot", "head, chest, legs, feet, hand or off_hand", "hand", "s")
                        .param("drop_chance", "0..1", "0")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    EquipmentSlot slot = config.enumValue(EquipmentSlot.class, "slot", EquipmentSlot.HAND);
                    float dropChance = (float) config.decimal("drop_chance", 0.0d);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null || entity.getEquipment() == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack stack = engine.hooks().sigil().resolveItem(item);
                        if (stack == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.getEquipment().setItem(slot, stack);
                        // setDropChance(EquipmentSlot, float) does not exist at
                        // the 1.19.4 floor, so the per-slot setters are used.
                        // Players have no drop chances at all; setting the item
                        // is still the useful half.
                        try {
                            switch (slot) {
                                case HEAD:
                                    entity.getEquipment().setHelmetDropChance(dropChance);
                                    break;
                                case CHEST:
                                    entity.getEquipment().setChestplateDropChance(dropChance);
                                    break;
                                case LEGS:
                                    entity.getEquipment().setLeggingsDropChance(dropChance);
                                    break;
                                case FEET:
                                    entity.getEquipment().setBootsDropChance(dropChance);
                                    break;
                                case HAND:
                                    entity.getEquipment().setItemInMainHandDropChance(dropChance);
                                    break;
                                case OFF_HAND:
                                    entity.getEquipment().setItemInOffHandDropChance(dropChance);
                                    break;
                                default:
                                    break;
                            }
                        } catch (UnsupportedOperationException ignored) {
                            // Player equipment throws rather than returning.
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("hold_item", Mechanics.type(
                MechanicMeta.builder("hold_item")
                        .description("Swaps the main hand item for a while, then puts it back.")
                        .requires(TargetKind.ENTITY)
                        .required("item", "a Sigil id or a material", "i")
                        .param("duration", "how long", "2s", "d")
                        .build(),
                config -> {
                    String item = config.raw("item", "");
                    long duration = config.ticks("duration", 40L);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null || entity.getEquipment() == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack stack = engine.hooks().sigil().resolveItem(item);
                        if (stack == null) {
                            return MechanicResult.FAIL;
                        }
                        ItemStack previous = entity.getEquipment().getItemInMainHand();
                        entity.getEquipment().setItemInMainHand(stack);
                        engine.scheduler().atEntityLater(entity, () -> {
                            if (entity.isValid() && entity.getEquipment() != null) {
                                entity.getEquipment().setItemInMainHand(previous);
                            }
                        }, duration);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("item_spray", Mechanics.type(
                MechanicMeta.builder("item_spray")
                        .description("Throws short-lived item entities outward, for gore and debris.")
                        .requires(TargetKind.ANY)
                        .required("item", "a material", "i")
                        .param("amount", "how many", "8", "a")
                        .param("strength", "how hard", "0.3", "s")
                        .param("duration", "how long before they vanish", "2s", "d")
                        .build(),
                config -> {
                    Material material = Registries.material(config.raw("item", ""));
                    if (material == null) {
                        throw new IllegalArgumentException("unknown material '" + config.raw("item", "") + "'");
                    }
                    Expression amount = config.number("amount", 8);
                    Expression strength = config.number("strength", 0.3d);
                    long duration = config.ticks("duration", 40L);
                    return (context, target) -> {
                        Location location = target.location().add(0, 1, 0);
                        World world = location.getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        int count = Math.max(1, Math.min(64, amount.asInt(context, target)));
                        double power = strength.asDouble(context, target);
                        for (int index = 0; index < count; index++) {
                            Item dropped = world.dropItem(location, new ItemStack(material));
                            dropped.setPickupDelay(Integer.MAX_VALUE);
                            dropped.setPersistent(false);
                            dropped.setVelocity(new Vector(
                                    (ThreadLocalRandom.current().nextDouble() - 0.5d) * power,
                                    ThreadLocalRandom.current().nextDouble() * power,
                                    (ThreadLocalRandom.current().nextDouble() - 0.5d) * power));
                            engine.scheduler().atEntityLater(dropped, () -> {
                                if (dropped.isValid()) {
                                    dropped.remove();
                                }
                            }, duration);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("play_animation", Mechanics.type(
                MechanicMeta.builder("play_animation")
                        .description("Plays a vanilla entity effect, e.g. hurt or wolf_shake.")
                        .requires(TargetKind.ENTITY)
                        .param("animation", "an EntityEffect name", "hurt", "a", "effect")
                        .build(),
                config -> {
                    org.bukkit.EntityEffect effect = config.enumValue(org.bukkit.EntityEffect.class,
                            "animation", org.bukkit.EntityEffect.HURT);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.playEffect(effect);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("camera_shake", Mechanics.type(
                MechanicMeta.builder("camera_shake")
                        .description("Shoves the player's view around briefly.")
                        .requires(TargetKind.ENTITY)
                        .param("intensity", "degrees per tick", "3", "i", "strength")
                        .param("duration", "how long", "1s", "d")
                        .build(),
                config -> {
                    Expression intensity = config.number("intensity", 3);
                    long duration = config.ticks("duration", 20L);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        double degrees = intensity.asDouble(context, target);
                        // There is no camera-shake packet in the Bukkit API, so
                        // this is small alternating rotations. It is honest
                        // about being an approximation rather than pretending.
                        long[] elapsed = {0L};
                        var task = new Object() {
                            dev.bwmp.bestiary.api.scheduler.BestiaryTask handle;
                        };
                        task.handle = engine.scheduler().atEntityTimer(player, () -> {
                            elapsed[0] += 2L;
                            if (elapsed[0] > duration || !player.isValid()) {
                                if (task.handle != null) {
                                    task.handle.cancel();
                                }
                                return;
                            }
                            float yaw = player.getLocation().getYaw()
                                    + (float) ((ThreadLocalRandom.current().nextDouble() - 0.5d) * degrees * 2);
                            float pitch = player.getLocation().getPitch()
                                    + (float) ((ThreadLocalRandom.current().nextDouble() - 0.5d) * degrees);
                            player.setRotation(yaw, Math.max(-90.0f, Math.min(90.0f, pitch)));
                        }, 2L, 2L);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("blindness", Mechanics.type(
                MechanicMeta.builder("blindness")
                        .description("Shorthand for a blindness effect.")
                        .requires(TargetKind.ENTITY)
                        .param("duration", "how long", "3s", "d")
                        .build(),
                config -> {
                    long duration = config.ticks("duration", 60L);
                    PotionEffectType blindness = Registries.potionEffect("blindness");
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null || blindness == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.addPotionEffect(new PotionEffect(blindness, (int) duration, 0, false, false));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("time_of_day", Mechanics.type(
                MechanicMeta.builder("time_of_day")
                        .description("Sets the world time, or a player's client-side time.")
                        .requires(TargetKind.ANY)
                        .param("time", "ticks, or day/night/noon/midnight", "13000", "t")
                        .param("player_only", "change only the target player's sky", "true", "client")
                        .build(),
                config -> {
                    String written = config.raw("time", "13000");
                    long time = parseTime(written);
                    boolean playerOnly = config.bool("player_only", true);
                    return (context, target) -> {
                        if (playerOnly) {
                            Player player = target.player();
                            if (player == null) {
                                return MechanicResult.FAIL;
                            }
                            player.setPlayerTime(time, false);
                            return MechanicResult.SUCCESS;
                        }
                        World world = target.location().getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        world.setTime(time);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("weather", Mechanics.type(
                MechanicMeta.builder("weather")
                        .description("Sets the weather, or a player's client-side weather.")
                        .requires(TargetKind.ANY)
                        .param("type", "clear, rain or thunder", "rain", "t")
                        .param("duration", "how long", "5m", "d")
                        .param("player_only", "change only the target player's sky", "true", "client")
                        .build(),
                config -> {
                    String type = config.raw("type", "rain").toLowerCase(Locale.ROOT);
                    long duration = config.ticks("duration", 20L * 60 * 5);
                    boolean playerOnly = config.bool("player_only", true);
                    return (context, target) -> {
                        if (playerOnly) {
                            Player player = target.player();
                            if (player == null) {
                                return MechanicResult.FAIL;
                            }
                            player.setPlayerWeather(type.equals("clear")
                                    ? org.bukkit.WeatherType.CLEAR : org.bukkit.WeatherType.DOWNFALL);
                            return MechanicResult.SUCCESS;
                        }
                        World world = target.location().getWorld();
                        if (world == null) {
                            return MechanicResult.FAIL;
                        }
                        world.setStorm(!type.equals("clear"));
                        world.setThundering(type.equals("thunder"));
                        world.setWeatherDuration((int) duration);
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    // --- helpers ----------------------------------------------------------

    static Particle resolveParticle(String name) {
        Particle particle = Registries.particle(name);
        if (particle == null) {
            throw new IllegalArgumentException("unknown particle '" + name + "'");
        }
        return particle;
    }

    /**
     * Emits to each player in range rather than through {@code World}.
     * <p>
     * This is where the view-distance cap actually applies. Painting a
     * 2048-point sphere through the world API sends it to every player tracking
     * the chunk, however far away and however irrelevant.
     */
    static void emit(Engine engine, Location location, Particle particle, int count,
                     double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double viewDistance = engine.settings().particleViewDistance();
        double squared = viewDistance * viewDistance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) > squared) {
                continue;
            }
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, data);
        }
    }

    private static Object particleData(Particle particle, String colour, double size, Material material) {
        Class<?> dataType = particle.getDataType();
        if (dataType == Particle.DustOptions.class) {
            return new Particle.DustOptions(parseColour(colour.isEmpty() ? "ff0000" : colour, Color.RED),
                    (float) Math.max(0.1d, size));
        }
        if (material != null) {
            if (dataType == org.bukkit.block.data.BlockData.class) {
                return material.createBlockData();
            }
            if (dataType == ItemStack.class) {
                return new ItemStack(material);
            }
        }
        return null;
    }

    static Color parseColour(String hex, Color fallback) {
        String text = hex == null ? "" : hex.trim().replace("#", "");
        if (text.length() != 6) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(text, 16));
        } catch (IllegalArgumentException ignored) {
            // NumberFormatException is one of these; Color.fromRGB throws the
            // other for a value outside its range.
            return fallback;
        }
    }

    private static FireworkEffect.Type parseFireworkShape(String written) {
        String text = written.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        switch (text) {
            case "BALL_LARGE":
            case "LARGE":
                return FireworkEffect.Type.BALL_LARGE;
            case "STAR":
                return FireworkEffect.Type.STAR;
            case "BURST":
                return FireworkEffect.Type.BURST;
            case "CREEPER":
                return FireworkEffect.Type.CREEPER;
            default:
                return FireworkEffect.Type.BALL;
        }
    }

    private static long parseTime(String written) {
        switch (written.trim().toLowerCase(Locale.ROOT)) {
            case "day":
                return 1000L;
            case "noon":
                return 6000L;
            case "night":
                return 13000L;
            case "midnight":
                return 18000L;
            default:
                try {
                    return Long.parseLong(written.trim());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("'" + written + "' is not a time");
                }
        }
    }

    static EntityType entityTypeOrThrow(String name) {
        EntityType type = Registries.entityType(name);
        if (type == null) {
            throw new IllegalArgumentException("unknown entity type '" + name + "'");
        }
        return type;
    }
}
