# Bestiary

A custom-mob and skill engine for Minecraft servers. Two content types — a
**mob** (what a creature is) and a **skill** (what something does) — composed
from four primitives:

| Primitive | Answers | Example |
|---|---|---|
| **Mechanic** | *what happens* | deal 12 damage |
| **Targeter** | *to whom, or where* | the nearest 3 players within 8 blocks |
| **Condition** | *only if* | caster below 50% health |
| **Trigger** | *when* | every 60 ticks while in combat |

A skill is a tree, not a list: a mechanic may itself be a skill call, so skills
compose to arbitrary depth.

**Minecraft 1.19.4 – 26.x, on Spigot, Paper, Paper forks and Folia.** One jar.

## Two ways to write the same thing

```yaml
valkyrie_shockwave:
  cooldown: 8s
  skills:
    - type: damage
      amount: 9
      ignore_armor: true
      targeter: { type: players_in_radius, radius: 7 }
      conditions:
        - { type: on_ground }
```

```yaml
valkyrie_shockwave:
  cooldown: 8s
  skills:
    - damage{amount=9;ignoreArmor=true} @playersInRadius{r=7} ?onGround
```

Both parse to the identical tree, through the same parser, so there is one
validator and one set of error messages. Mixing forms in one file is fine.
Structured YAML is the canonical form, which is what makes the in-game editor
possible without round-tripping strings.

## Expressions

Every numeric and string parameter accepts placeholders and arithmetic,
resolved at execution time:

```yaml
- damage{amount=<caster.level> * 2.5 + <random.1to4>} @target
- message{msg="<target.name> is at <target.hp.percent>%"} @nearestPlayer{r=10}
```

Namespaces: `caster.*`, `target.*`, `trigger.*`, `origin.*`, `skill.var.*`,
`mob.var.*`, `global.var.*`, `random.*`, `math.*`, and `papi.*` when
PlaceholderAPI is present. A bracketed token in a namespace nobody registered —
`<gradient:#a:#b>` — is left exactly as written, so MiniMessage survives.

## Content layout

```
plugins/Bestiary/
  config.yml
  messages.yml
  skills/          # arbitrarily nested; merged into one namespace
  mobs/<ns>/       # the first directory is the id's namespace
  droptables/
  spawners/
```

`mobs/aether/valkyrie_champion.yml` defines `aether:valkyrie_champion`.

Parse failures are per definition, not per file: one broken skill does not take
out the other forty beside it. Each failure names the file, the YAML path, the
offending value and what was expected.

## Structure anchors

A world generator places a marker where a boss belongs. Bestiary adopts it on
chunk load, records the position, deletes the entity, and from then on spawns
the boss on player proximity with a persisted respawn cooldown. Nothing spawns
in the thousands of structures nobody has visited, and any removal Bestiary did
not intend is self-healing on the next visit.

Identity comes from a scoreboard tag where the generator can write one, and from
an entity-type map in `config.yml` where it cannot.

## Custom AI

Paper ships a supported goal-selector API, so custom goals cost **zero NMS** and
work across the entire supported band — including stripping a Ravager's vanilla
goals entirely and replacing them.

```yaml
ai:
  goals:
    - clear: [ MOVE, TARGET ]
    - bestiary:melee_attack{speed=1.0}
    - bestiary:hover_strafe{distance=6;height=3}
    - bestiary:avoid_void
  navigation: flying     # 1.20.5+; reported and ignored below that
```

`navigation`, `move_control`, `look_control` and brain manipulation need a
Mojang-mapped runtime and activate on Paper 1.20.5+. If any of it fails to
resolve the whole tier disables itself with one report line rather than
half-working.

**Custom AI requires Paper.** On Spigot, mobs keep their vanilla base AI plus
scripted movement mechanics — a real limitation, reported at startup.

## Performance

Four guards, because skills call skills:

| Guard | Default |
|---|---|
| Recursion depth | 32 |
| Mechanics per execution | 4000 |
| Targets per targeter | 64 |
| Wall clock per tick | 5 ms, then suspended and resumed |

Exceeding any of them aborts that execution and logs once per skill per minute
with the skill id and the node path. Silently truncating would be worse: a boss
that half-fires is a bug report nobody can reproduce.

Ceiling: **50 concurrent mobs with active skill trees under 2 ms/tick total.**

## Commands

`/bestiary spawn · kill · list · info · cast · reload · anchor · spawner ·
droptable · import · debug · menu · platform`

`cast` runs a skill from you with a live trace of targeter resolution and
condition results; `debug` attaches that trace to a running mob.

## Integrations

All optional, all probed, all reported once at startup: Sigil, PlaceholderAPI
(consumed *and* provided as `%bestiary_*%`), Vault, WorldGuard, GriefPrevention,
ModelEngine, Citizens, mcMMO, Jobs, AetherCore.

## Migrating from MythicMobs

`/bestiary import <file|dir>` converts mobs, skills and drop tables to native
YAML. It is a one-shot converter, not runtime compatibility. Anything unmappable
is written into the output file in place and named in the summary — a boss that
converts to 90% of itself and never says so is a very expensive debugging
session.

## Reference

- [docs/mechanics.md](docs/mechanics.md) — 140 mechanics
- [docs/targeters.md](docs/targeters.md) — 39 targeters
- [docs/conditions.md](docs/conditions.md) — 52 conditions
- [docs/api.md](docs/api.md) — registering your own

The first three are generated from the declarations, so they cannot drift from
what the engine accepts.

## Building

```
mvn package
```

Java 17, Maven. `bestiary-plugin` is parented to `dev.bwmp:keystone-parent`,
which supplies the shade and relocation configuration; Maven resolves a parent
before it reads the project's own repositories, so `nexus.bwmp.dev` has to be
known from `settings.xml` or Keystone installed locally.

## Licence

LGPL-3.0. See [COPYING](COPYING).
