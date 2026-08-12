# API

`bestiary-api` is published separately from the plugin and depends on nothing
but the Bukkit API. That is deliberate and it is load-bearing: the shipped
Bestiary jar relocates Keystone and Adventure into `dev.bwmp.bestiary.libs`, so
anything the API exposed from those packages would compile against one name and
resolve to another at runtime — a `NoClassDefFoundError` naming a class that
looks entirely correct. Everything Bestiary re-exposes is re-declared in the API
module instead; see `BestiaryScheduler`.

```xml
<dependency>
    <groupId>dev.bwmp</groupId>
    <artifactId>bestiary-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Getting the API

Published through Bukkit's service manager, because it is the one registry that
genuinely spans plugin boundaries and it unregisters cleanly.

```java
BestiaryAPI bestiary = BestiaryAPI.get().orElseThrow();
```

Add `Bestiary` to your `softdepend` (or `depend`) so it has enabled first.

## Identifying a mob

`resolve` is the only supported way to ask what an entity is. It reads the
`bestiary:id` persistent-data key, adopting the mob if the chunk-load adopter
has not reached it yet — so it is correct regardless of event ordering.

```java
bestiary.resolve(event.getEntity()).ifPresent(mob -> {
    getLogger().info(mob.definition().id() + " at level " + mob.level());
});
```

`BestiaryMob` exposes the level, the current phase, mob-scoped variables, the
owning anchor, threat and damage-share for a player, and `cast`, `signal` and
`remove`.

## Spawning

```java
bestiary.spawn(new NamespacedKey("aether", "valkyrie_champion"), location, 3);
```

## Registering a mechanic

A mechanic is a factory plus a body. The factory runs once per config line at
load; the body runs once per resolved target. Everything the body needs from
config is read in the factory, so parsing never happens on a hot path.

```java
public final class HealingWindType implements MechanicType {

    private static final MechanicMeta META = MechanicMeta.builder("healing_wind")
            .description("Heals everything it touches for a share of its maximum health.")
            .requires(TargetKind.ENTITY)
            .param("percent", "share of maximum health", "5", "p", "amount")
            .build();

    @Override
    public MechanicMeta meta() {
        return META;
    }

    @Override
    public Mechanic create(MechanicConfig config) {
        Expression percent = config.number("percent", 5);
        return new Mechanic() {
            @Override
            public MechanicMeta meta() {
                return META;
            }

            @Override
            public MechanicResult execute(SkillContext context, Target target) {
                LivingEntity entity = target.living();
                if (entity == null) {
                    return MechanicResult.FAIL;
                }
                // Evaluated per target: the same line can heal for
                // <caster.level> * 2 and mean something different each time.
                double amount = entity.getMaxHealth() * percent.asDouble(context, target) / 100.0;
                entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + amount));
                return MechanicResult.SUCCESS;
            }
        };
    }
}
```

```java
bestiary.registerMechanicType(this, new NamespacedKey(this, "healing_wind"), new HealingWindType());
```

The id's namespace must match your plugin — that is what stops two plugins
quietly overriding each other's content — and the registration is dropped
automatically when your plugin disables, so a reload cannot leave handlers
pointing at a dead classloader.

Config can now use it in either form:

```yaml
- healing_wind{percent=8} @playersInRadius{r=6}
- type: myplugin:healing_wind
  percent: 8
  targeter: { type: players_in_radius, radius: 6 }
```

Targeters and conditions follow the same shape through `registerTargeterType`
and `registerConditionType`.

## Parameters and aliases

`MechanicMeta` declares the parameters and their shorthand aliases. The alias
table lives with the mechanic, never in the parser, which is what lets `p=`
mean `percent` on your mechanic and something else on someone else's. The
parser reads the declaration before it builds anything, so a misspelled key is a
load-time warning naming the parameters that do exist.

Keys are normalized by lowercasing and stripping underscores, so
`ignore_armor`, `ignoreArmor` and `IgnoreArmor` are one key.

## Expressions

Every numeric and string parameter is an `Expression`, not a value. Constants
are just expressions that ignore their input, so a mechanic never branches on
whether its parameter happened to be literal.

Resolution order is pinned: placeholders are substituted first, then — in
numeric contexts only — the result is parsed as an infix expression
(`+ - * / %`, unary minus, parentheses, doubles throughout). String contexts get
substitution only.

## Registering an AI goal

Goals compile against `bestiary-api` and never against a Paper class, so a goal
still loads on a server where the Goal API is absent — it simply never runs.

```java
bestiary.registerGoalType(this, new NamespacedKey(this, "spin"), (context, args) -> new AiGoal() {
    @Override
    public boolean shouldActivate() {
        return context.target() != null;
    }

    @Override
    public void tick() {
        Location location = context.mob().getLocation();
        context.mob().setRotation(location.getYaw() + 20, location.getPitch());
    }

    @Override
    public Set<GoalCategory> categories() {
        return Set.of(GoalCategory.LOOK);
    }
});
```

## Scheduling

Use `bestiary.scheduler()`. `BukkitRunnable` must not be used in mechanic or
goal code: on Folia there is no single main thread, and work must be submitted
to the thread that owns the entity or location it touches.

| Method | Runs on |
|---|---|
| `run`, `runLater`, `runTimer` | wherever global work belongs |
| `atEntity`, `atEntityTimer`, `atEntityLater` | the thread owning that entity, following it between regions |
| `atLocation` | the thread owning that region |
| `async` | off the server threads; must not touch world state |
| `teleport` | safely on both backends |

## Events

| Event | When |
|---|---|
| `BestiaryMobSpawnEvent` | after a mob is spawned and fully configured; cancelling removes it |
| `BestiaryMobDeathEvent` | on death, before drops are rolled, with every damage contributor |
| `BestiaryPhaseChangeEvent` | when a mob advances a phase |

## Statistics

`killCount`, `totalKillCount` and `anchorCooldownMillis` are served from the
in-memory view maintained alongside the storage writes, never from a query. They
are safe to call on the main thread as often as you like — that is the same
guarantee the `%bestiary_*%` placeholders rely on.
