package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import org.bukkit.EntityEffect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public final class DamageMechanics {

    private DamageMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("damage", Mechanics.type(
                MechanicMeta.builder("damage")
                        .description("Deals damage, optionally bypassing armour and the immunity window.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "damage dealt", "1", "a", "d")
                        .param("ignore_armor", "subtract health directly rather than going through armour",
                                "false", "ia")
                        .param("ignore_immunity", "clear the no-damage window first", "false", "ii")
                        .param("prevent_knockback", "damage without the usual shove", "false", "pkb")
                        .param("scale_with_power", "multiply by the skill's power", "true")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 1);
                    boolean ignoreArmor = config.bool("ignore_armor", false);
                    boolean ignoreImmunity = config.bool("ignore_immunity", false);
                    boolean preventKnockback = config.bool("prevent_knockback", false);
                    boolean scale = config.bool("scale_with_power", true);
                    return (context, target) -> {
                        LivingEntity victim = target.living();
                        if (victim == null || victim.isDead()) {
                            return MechanicResult.FAIL;
                        }
                        double value = amount.asDouble(context, target) * (scale ? context.power() : 1.0d);
                        if (value <= 0) {
                            return MechanicResult.FAIL;
                        }
                        return Mechanics.result(applyDamage(context.caster(), victim, value,
                                ignoreArmor, ignoreImmunity, preventKnockback));
                    };
                }));

        into.put("percent_damage", Mechanics.type(
                MechanicMeta.builder("percent_damage")
                        .description("Damage as a percentage of the target's maximum or current health.")
                        .requires(TargetKind.ENTITY)
                        .param("percent", "0..100", "10", "p", "amount")
                        .param("of", "max or current", "max")
                        .param("ignore_armor", "subtract health directly", "true", "ia")
                        .build(),
                config -> {
                    Expression percent = config.number("percent", 10);
                    boolean ofMax = !config.raw("of", "max").equalsIgnoreCase("current");
                    boolean ignoreArmor = config.bool("ignore_armor", true);
                    return (context, target) -> {
                        LivingEntity victim = target.living();
                        if (victim == null || victim.isDead()) {
                            return MechanicResult.FAIL;
                        }
                        double basis = ofMax ? maxHealth(victim) : victim.getHealth();
                        double value = basis * percent.asDouble(context, target) / 100.0d;
                        return Mechanics.result(applyDamage(context.caster(), victim, value,
                                ignoreArmor, false, false));
                    };
                }));

        into.put("true_damage", Mechanics.type(
                MechanicMeta.builder("true_damage")
                        .description("Health removed directly. Ignores armour, resistance and immunity.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "health removed", "1", "a")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 1);
                    return (context, target) -> {
                        LivingEntity victim = target.living();
                        if (victim == null || victim.isDead()) {
                            return MechanicResult.FAIL;
                        }
                        return Mechanics.result(applyDamage(context.caster(), victim,
                                amount.asDouble(context, target), true, true, true));
                    };
                }));

        into.put("heal", Mechanics.type(
                MechanicMeta.builder("heal")
                        .description("Restores health, capped at the target's maximum.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "health restored", "1", "a")
                        .param("overheal", "allow exceeding the maximum by raising it", "false")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 1);
                    boolean overheal = config.bool("overheal", false);
                    return (context, target) -> {
                        LivingEntity healed = target.living();
                        if (healed == null || healed.isDead()) {
                            return MechanicResult.FAIL;
                        }
                        double value = amount.asDouble(context, target) * context.power();
                        double maximum = maxHealth(healed);
                        double result = healed.getHealth() + value;
                        if (!overheal) {
                            result = Math.min(result, maximum);
                        } else if (result > maximum) {
                            setMaxHealth(healed, result);
                        }
                        healed.setHealth(Math.max(0.0d, result));

                        // Healing done near a threat-tracking mob generates
                        // threat, which is what makes a healer targetable.
                        if (context.caster() instanceof Player) {
                            engine.mobs().instances().forEach(instance -> {
                                if (instance.threatTable() != null
                                        && instance.entity().getWorld().equals(healed.getWorld())
                                        && instance.entity().getLocation().distanceSquared(
                                        healed.getLocation()) < 32 * 32) {
                                    instance.threatTable().addHealing((Player) context.caster(), value);
                                }
                            });
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("heal_percent", Mechanics.type(
                MechanicMeta.builder("heal_percent")
                        .description("Restores a percentage of maximum health.")
                        .requires(TargetKind.ENTITY)
                        .param("percent", "0..100", "10", "p", "amount")
                        .build(),
                config -> {
                    Expression percent = config.number("percent", 10);
                    return (context, target) -> {
                        LivingEntity healed = target.living();
                        if (healed == null || healed.isDead()) {
                            return MechanicResult.FAIL;
                        }
                        double maximum = maxHealth(healed);
                        healed.setHealth(Math.min(maximum,
                                healed.getHealth() + maximum * percent.asDouble(context, target) / 100.0d));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_health", Mechanics.type(
                MechanicMeta.builder("set_health")
                        .description("Sets health outright.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "new health", "20", "a", "hp")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 20);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setHealth(Math.max(0.0d,
                                Math.min(maxHealth(entity), amount.asDouble(context, target))));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_max_health", Mechanics.type(
                MechanicMeta.builder("set_max_health")
                        .description("Sets the maximum health attribute.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "new maximum", "20", "a")
                        .param("heal", "restore to full afterwards", "false")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 20);
                    boolean heal = config.bool("heal", false);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        double value = Math.max(1.0d, amount.asDouble(context, target));
                        setMaxHealth(entity, value);
                        if (heal) {
                            entity.setHealth(value);
                        } else if (entity.getHealth() > value) {
                            entity.setHealth(value);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("consume_health", Mechanics.type(
                MechanicMeta.builder("consume_health")
                        .description("Spends the caster's own health. Fails rather than killing the caster.")
                        .requires(TargetKind.NONE)
                        .param("amount", "health spent", "1", "a")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 1);
                    return (context, target) -> {
                        LivingEntity caster = context.casterLiving();
                        if (caster == null) {
                            return MechanicResult.FAIL;
                        }
                        double value = amount.asDouble(context, target);
                        if (caster.getHealth() - value <= 0.0d) {
                            return MechanicResult.FAIL;
                        }
                        caster.setHealth(caster.getHealth() - value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("feed", Mechanics.type(
                MechanicMeta.builder("feed")
                        .description("Restores hunger and saturation on a player.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "hunger points", "4", "a")
                        .param("saturation", "saturation added", "2", "sat")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 4);
                    Expression saturation = config.number("saturation", 2);
                    return (context, target) -> {
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        player.setFoodLevel(Math.max(0, Math.min(20,
                                player.getFoodLevel() + amount.asInt(context, target))));
                        player.setSaturation((float) Math.max(0,
                                player.getSaturation() + saturation.asDouble(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("damage_armor", Mechanics.type(
                MechanicMeta.builder("damage_armor")
                        .description("Wears down worn armour without dealing health damage.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "durability removed per piece", "10", "a")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 10);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        EntityEquipment equipment = entity == null ? null : entity.getEquipment();
                        if (equipment == null) {
                            return MechanicResult.FAIL;
                        }
                        int value = amount.asInt(context, target);
                        boolean any = false;
                        ItemStack[] armour = equipment.getArmorContents();
                        for (int index = 0; index < armour.length; index++) {
                            if (damageItem(armour[index], value)) {
                                any = true;
                            }
                        }
                        equipment.setArmorContents(armour);
                        return Mechanics.result(any);
                    };
                }));

        into.put("ignite", Mechanics.type(
                MechanicMeta.builder("ignite")
                        .description("Sets the target on fire.")
                        .requires(TargetKind.ENTITY)
                        .param("duration", "how long", "3s", "ticks", "d")
                        .build(),
                config -> {
                    long ticks = config.ticks("duration", 60L);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setFireTicks((int) Math.max(entity.getFireTicks(), ticks));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("extinguish", Mechanics.type(
                MechanicMeta.builder("extinguish")
                        .description("Puts the target out.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    Entity entity = target.entity();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    entity.setFireTicks(0);
                    return MechanicResult.SUCCESS;
                }));

        into.put("kill", Mechanics.type(
                MechanicMeta.builder("kill")
                        .description("Kills the target outright, running its death handling.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    LivingEntity entity = target.living();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    entity.setHealth(0.0d);
                    return MechanicResult.SUCCESS;
                }));

        into.put("suicide", Mechanics.type(
                MechanicMeta.builder("suicide")
                        .description("Kills the caster. The last line of a self-destruct skill.")
                        .requires(TargetKind.NONE)
                        .build(),
                config -> (context, target) -> {
                    LivingEntity caster = context.casterLiving();
                    if (caster == null) {
                        return MechanicResult.FAIL;
                    }
                    caster.setHealth(0.0d);
                    return MechanicResult.HALT;
                }));
    }

    private static boolean applyDamage(Entity source, LivingEntity victim, double amount,
                                       boolean ignoreArmor, boolean ignoreImmunity, boolean preventKnockback) {
        if (amount <= 0.0d) {
            return false;
        }
        if (ignoreImmunity) {
            victim.setNoDamageTicks(0);
        }

        if (!ignoreArmor) {
            org.bukkit.util.Vector velocity = preventKnockback ? victim.getVelocity() : null;
            if (source instanceof LivingEntity) {
                victim.damage(amount, (LivingEntity) source);
            } else {
                victim.damage(amount);
            }
            if (velocity != null) {
                victim.setVelocity(velocity);
            }
            return true;
        }

        // Armour-ignoring damage subtracts health directly, because there is no
        // Bukkit way to say "this much, past armour". The hurt animation is
        // played explicitly so it still reads as damage to the player.
        double remaining = victim.getHealth() - amount;
        victim.playEffect(EntityEffect.HURT);
        if (remaining <= 0.0d) {
            victim.setHealth(0.0d);
        } else {
            victim.setHealth(remaining);
            if (source instanceof LivingEntity && victim instanceof org.bukkit.entity.Mob) {
                ((org.bukkit.entity.Mob) victim).setTarget((LivingEntity) source);
            }
        }
        return true;
    }

    private static boolean damageItem(ItemStack stack, int amount) {
        if (stack == null || stack.getType().getMaxDurability() <= 0) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable)) {
            return false;
        }
        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
        int updated = damageable.getDamage() + amount;
        if (updated >= stack.getType().getMaxDurability()) {
            stack.setAmount(0);
            return true;
        }
        damageable.setDamage(updated);
        stack.setItemMeta(meta);
        return true;
    }

    public static double maxHealth(LivingEntity entity) {
        org.bukkit.attribute.Attribute attribute =
                dev.bwmp.bestiary.expression.Attributes.byLegacyName("GENERIC_MAX_HEALTH");
        org.bukkit.attribute.AttributeInstance instance =
                attribute == null ? null : entity.getAttribute(attribute);
        return instance == null ? entity.getHealth() : instance.getValue();
    }

    static void setMaxHealth(LivingEntity entity, double value) {
        org.bukkit.attribute.Attribute attribute =
                dev.bwmp.bestiary.expression.Attributes.byLegacyName("GENERIC_MAX_HEALTH");
        org.bukkit.attribute.AttributeInstance instance =
                attribute == null ? null : entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
