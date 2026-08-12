package dev.bwmp.bestiary.mechanic;

import dev.bwmp.bestiary.Engine;
import dev.bwmp.bestiary.api.skill.Expression;
import dev.bwmp.bestiary.api.skill.MechanicMeta;
import dev.bwmp.bestiary.api.skill.MechanicResult;
import dev.bwmp.bestiary.api.skill.MechanicType;
import dev.bwmp.bestiary.api.skill.TargetKind;
import dev.bwmp.bestiary.api.skill.VariableScope;
import dev.bwmp.bestiary.aura.AuraSpec;
import dev.bwmp.bestiary.expression.Arithmetic;
import dev.bwmp.bestiary.mob.MobInstance;
import dev.bwmp.bestiary.text.Text;
import dev.bwmp.bestiary.util.Registries;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/** Status effects, auras, variables, threat and the small per-entity switches. */
public final class StateMechanics {

    private StateMechanics() {
    }

    public static void register(Map<String, MechanicType> into, Engine engine) {

        into.put("potion", Mechanics.type(
                MechanicMeta.builder("potion")
                        .description("Applies a potion effect.")
                        .requires(TargetKind.ENTITY)
                        .required("type", "effect name, e.g. slowness", "t", "effect", "p")
                        .param("duration", "how long", "5s", "d", "ticks")
                        .param("level", "amplifier, 1 is level I", "1", "l", "amplifier", "a")
                        .param("ambient", "the faded particle style", "false")
                        .param("particles", "show particles", "true")
                        .param("icon", "show the effect icon", "true")
                        .build(),
                config -> {
                    String typeName = config.raw("type", "");
                    PotionEffectType type = Registries.potionEffect(typeName);
                    if (type == null) {
                        throw new IllegalArgumentException("unknown potion effect '" + typeName + "'");
                    }
                    long duration = config.ticks("duration", 100L);
                    Expression level = config.number("level", 1);
                    boolean ambient = config.bool("ambient", false);
                    boolean particles = config.bool("particles", true);
                    boolean icon = config.bool("icon", true);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        int amplifier = Math.max(0, level.asInt(context, target) - 1);
                        entity.addPotionEffect(new PotionEffect(type, (int) duration, amplifier,
                                ambient, particles, icon));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("remove_potion", Mechanics.type(
                MechanicMeta.builder("remove_potion")
                        .description("Removes one potion effect.")
                        .requires(TargetKind.ENTITY)
                        .required("type", "effect name", "t", "effect")
                        .build(),
                config -> {
                    PotionEffectType type = Registries.potionEffect(config.raw("type", ""));
                    if (type == null) {
                        throw new IllegalArgumentException(
                                "unknown potion effect '" + config.raw("type", "") + "'");
                    }
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.removePotionEffect(type);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("clear_potions", Mechanics.type(
                MechanicMeta.builder("clear_potions")
                        .description("Removes every potion effect.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    LivingEntity entity = target.living();
                    if (entity == null) {
                        return MechanicResult.FAIL;
                    }
                    entity.getActivePotionEffects().forEach(effect ->
                            entity.removePotionEffect(effect.getType()));
                    return MechanicResult.SUCCESS;
                }));

        into.put("aura", Mechanics.type(
                MechanicMeta.builder("aura")
                        .description("Attaches a named, timed, stackable effect with its own skills.")
                        .requires(TargetKind.ENTITY)
                        .required("name", "aura name, unique per holder", "n", "id")
                        .param("duration", "total lifetime", "10s", "d")
                        .param("interval", "ticks between on_tick runs", "20", "i", "period")
                        .param("max_stacks", "stack ceiling", "1", "stacks", "ms")
                        .param("on_start", "skill run when it is first applied", "")
                        .param("on_tick", "skill run every interval, once per stack", "")
                        .param("on_end", "skill run when it expires or is removed", "")
                        .param("on_stack", "skill run when a stack is added", "")
                        .param("cancel_on_damage", "drop the aura when its holder is hurt", "false")
                        .param("cancel_on_giver_death", "drop the aura when the caster dies", "false")
                        .param("refresh_on_stack", "reset the timer when a stack is added", "true")
                        .build(),
                config -> {
                    AuraSpec spec = new AuraSpec(
                            config.raw("name", "aura"),
                            config.ticks("duration", 200L),
                            config.ticks("interval", 20L),
                            config.integer("max_stacks", 1),
                            config.raw("on_start", ""),
                            config.raw("on_tick", ""),
                            config.raw("on_end", ""),
                            config.raw("on_stack", ""),
                            config.bool("cancel_on_damage", false),
                            config.bool("cancel_on_giver_death", false),
                            config.bool("refresh_on_stack", true));
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        engine.auras().apply(entity, context.caster(), spec);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("remove_aura", Mechanics.type(
                MechanicMeta.builder("remove_aura")
                        .description("Removes a named aura, running its on_end skill.")
                        .requires(TargetKind.ENTITY)
                        .required("name", "aura name", "n", "id")
                        .build(),
                config -> {
                    String name = config.raw("name", "");
                    return (context, target) -> {
                        if (target.entity() == null) {
                            return MechanicResult.FAIL;
                        }
                        engine.auras().remove(target.entity(), name);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("immunity", Mechanics.type(
                MechanicMeta.builder("immunity")
                        .description("Opens a named immunity window, e.g. table=knockback.")
                        .requires(TargetKind.ENTITY)
                        .required("table", "immunity table name", "t", "name")
                        .param("duration", "how long", "3s", "d")
                        .build(),
                config -> {
                    String table = config.raw("table", "");
                    long duration = config.ticks("duration", 60L);
                    return (context, target) -> {
                        if (target.entity() == null) {
                            return MechanicResult.FAIL;
                        }
                        engine.immunity().grant(target.entity(), table, duration);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_stance", Mechanics.type(
                MechanicMeta.builder("set_stance")
                        .description("Sets the mob's stance variable, which conditions can read.")
                        .requires(TargetKind.ENTITY)
                        .required("stance", "the new stance", "s", "value")
                        .build(),
                config -> {
                    Expression stance = config.text("stance", "");
                    return (context, target) -> {
                        MobInstance instance = instanceOf(engine, target.entity());
                        if (instance == null) {
                            return MechanicResult.FAIL;
                        }
                        instance.variables().put("stance", stance.asString(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_ai", Mechanics.type(
                MechanicMeta.builder("set_ai")
                        .description("Turns the target's vanilla AI on or off.")
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        if (!(target.entity() instanceof Mob)) {
                            return MechanicResult.FAIL;
                        }
                        ((Mob) target.entity()).setAware(value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_faction", Mechanics.type(
                MechanicMeta.builder("set_faction")
                        .description("Changes the mob's faction for the rest of its life.")
                        .requires(TargetKind.ENTITY)
                        .required("faction", "the new faction", "f", "value")
                        .build(),
                config -> {
                    Expression faction = config.text("faction", "");
                    return (context, target) -> {
                        MobInstance instance = instanceOf(engine, target.entity());
                        if (instance == null) {
                            return MechanicResult.FAIL;
                        }
                        instance.variables().put("faction", faction.asString(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_level", Mechanics.type(
                MechanicMeta.builder("set_level")
                        .description("Changes the mob's level. Attributes are not re-rolled.")
                        .requires(TargetKind.ENTITY)
                        .required("level", "the new level", "l", "amount")
                        .build(),
                config -> {
                    Expression level = config.number("level", 1);
                    return (context, target) -> {
                        MobInstance instance = instanceOf(engine, target.entity());
                        if (instance == null) {
                            return MechanicResult.FAIL;
                        }
                        instance.variables().put("level", level.asInt(context, target));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_name", Mechanics.type(
                MechanicMeta.builder("set_name")
                        .description("Sets the target's display name from MiniMessage source.")
                        .requires(TargetKind.ENTITY)
                        .required("name", "MiniMessage text", "n", "display")
                        .param("visible", "always show it", "true")
                        .build(),
                config -> {
                    Expression name = config.text("name", "");
                    boolean visible = config.bool("visible", true);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setCustomName(Text.render(name.asString(context, target)));
                        entity.setCustomNameVisible(visible);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_no_damage_ticks", Mechanics.type(
                MechanicMeta.builder("set_no_damage_ticks")
                        .description("Sets the target's damage immunity window.")
                        .requires(TargetKind.ENTITY)
                        .param("ticks", "how long", "0", "amount", "t")
                        .build(),
                config -> {
                    Expression ticks = config.number("ticks", 0);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setNoDamageTicks(Math.max(0, ticks.asInt(context, target)));
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_invulnerable", booleanSwitch("set_invulnerable",
                "Toggles invulnerability.", (entity, value) -> entity.setInvulnerable(value)));

        into.put("set_gliding", Mechanics.type(
                MechanicMeta.builder("set_gliding")
                        .description("Toggles elytra gliding.")
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setGliding(value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_collidable", Mechanics.type(
                MechanicMeta.builder("set_collidable")
                        .description("Toggles entity collision.")
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        LivingEntity entity = target.living();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        entity.setCollidable(value);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_variable", Mechanics.type(
                MechanicMeta.builder("set_variable")
                        .description("Writes a variable in one of the four scopes.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("name", "variable name", "n", "var", "key")
                        .param("value", "the value; expressions are evaluated", "0", "v", "amount")
                        .param("scope", "skill, mob, target or global", "skill", "s")
                        .build(),
                config -> {
                    String name = config.raw("name", "");
                    Expression value = config.text("value", "0");
                    VariableScope scope = VariableScope.parse(config.raw("scope", "skill"), VariableScope.SKILL);
                    return (context, target) -> {
                        String text = value.asString(context, target);
                        Object stored = Arithmetic.parses(text) ? (Object) Arithmetic.evaluate(text) : text;
                        context.setVariable(scope, name, stored);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("variable_math", Mechanics.type(
                MechanicMeta.builder("variable_math")
                        .description("Applies an operation to a numeric variable.")
                        .requires(TargetKind.NONE)
                        .threadSafe()
                        .required("name", "variable name", "n", "var", "key")
                        .param("operation", "add, subtract, multiply, divide, set, min, max", "add", "op")
                        .param("value", "the operand", "1", "v", "amount")
                        .param("scope", "skill, mob, target or global", "skill", "s")
                        .build(),
                config -> {
                    String name = config.raw("name", "");
                    String operation = config.raw("operation", "add").toLowerCase(Locale.ROOT);
                    Expression value = config.number("value", 1);
                    VariableScope scope = VariableScope.parse(config.raw("scope", "skill"), VariableScope.SKILL);
                    return (context, target) -> {
                        double current = asNumber(context.variable(scope, name));
                        double operand = value.asDouble(context, target);
                        double result;
                        switch (operation) {
                            case "subtract":
                            case "sub":
                                result = current - operand;
                                break;
                            case "multiply":
                            case "mul":
                                result = current * operand;
                                break;
                            case "divide":
                            case "div":
                                result = operand == 0.0d ? current : current / operand;
                                break;
                            case "set":
                                result = operand;
                                break;
                            case "min":
                                result = Math.min(current, operand);
                                break;
                            case "max":
                                result = Math.max(current, operand);
                                break;
                            case "add":
                            default:
                                result = current + operand;
                                break;
                        }
                        context.setVariable(scope, name, result);
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("set_target", Mechanics.type(
                MechanicMeta.builder("set_target")
                        .description("Makes the caster attack the resolved target.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    if (!(context.caster() instanceof Mob) || target.living() == null) {
                        return MechanicResult.FAIL;
                    }
                    ((Mob) context.caster()).setTarget(target.living());
                    return MechanicResult.SUCCESS;
                }));

        into.put("taunt", Mechanics.type(
                MechanicMeta.builder("taunt")
                        .description("Forces the target player to the top of the caster's threat table.")
                        .requires(TargetKind.ENTITY)
                        .build(),
                config -> (context, target) -> {
                    MobInstance caster = instanceOf(engine, context.caster());
                    Player player = target.player();
                    if (caster == null || caster.threatTable() == null || player == null) {
                        return MechanicResult.FAIL;
                    }
                    caster.threatTable().taunt(player);
                    return MechanicResult.SUCCESS;
                }));

        into.put("modify_threat", Mechanics.type(
                MechanicMeta.builder("modify_threat")
                        .description("Adds to or multiplies a player's threat on the caster.")
                        .requires(TargetKind.ENTITY)
                        .param("amount", "threat added, or the factor when mode=multiply", "10", "a")
                        .param("mode", "add or multiply", "add", "m")
                        .build(),
                config -> {
                    Expression amount = config.number("amount", 10);
                    boolean multiply = config.raw("mode", "add").equalsIgnoreCase("multiply");
                    return (context, target) -> {
                        MobInstance caster = instanceOf(engine, context.caster());
                        Player player = target.player();
                        if (caster == null || caster.threatTable() == null || player == null) {
                            return MechanicResult.FAIL;
                        }
                        double value = amount.asDouble(context, target);
                        if (multiply) {
                            caster.threatTable().multiply(player, value);
                        } else {
                            caster.threatTable().add(player, value);
                        }
                        return MechanicResult.SUCCESS;
                    };
                }));

        into.put("clear_threat", Mechanics.type(
                MechanicMeta.builder("clear_threat")
                        .description("Wipes the caster's threat table, or one player from it.")
                        .requires(TargetKind.ANY)
                        .param("all", "clear everyone rather than the target", "false")
                        .build(),
                config -> {
                    boolean all = config.bool("all", false);
                    return (context, target) -> {
                        MobInstance caster = instanceOf(engine, context.caster());
                        if (caster == null || caster.threatTable() == null) {
                            return MechanicResult.FAIL;
                        }
                        if (all) {
                            caster.threatTable().clearAll();
                            return MechanicResult.SUCCESS;
                        }
                        Player player = target.player();
                        if (player == null) {
                            return MechanicResult.FAIL;
                        }
                        caster.threatTable().clear(player);
                        return MechanicResult.SUCCESS;
                    };
                }));
    }

    private static MechanicType booleanSwitch(String id, String description,
                                              java.util.function.BiConsumer<Entity, Boolean> setter) {
        return Mechanics.type(
                MechanicMeta.builder(id)
                        .description(description)
                        .requires(TargetKind.ENTITY)
                        .param("value", "true or false", "true", "enabled", "v")
                        .build(),
                config -> {
                    boolean value = config.bool("value", true);
                    return (context, target) -> {
                        Entity entity = target.entity();
                        if (entity == null) {
                            return MechanicResult.FAIL;
                        }
                        setter.accept(entity, value);
                        return MechanicResult.SUCCESS;
                    };
                });
    }

    public static MobInstance instanceOf(Engine engine, Entity entity) {
        return entity == null ? null : engine.mobs().instance(entity);
    }

    public static double asNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }
}
