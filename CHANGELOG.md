## Changelog

Notable changes to Bestiary. Versions follow [semantic versioning](https://semver.org);
`bestiary-api` is the surface those promises apply to.

## Unreleased

First release.

### Mobs and skills

- Two content types — a **mob** (what a creature is) and a **skill** (what
  something does) — composed from four primitives: mechanics, targeters,
  conditions and triggers.
- 140 mechanics, 39 targeters and 52 conditions, documented in `docs/`.
- A skill is a tree rather than a list: a mechanic may itself be a skill call,
  so skills compose to arbitrary depth. Inline blocks become real skills under a
  synthetic id, so every call — nested or not — takes the same executor path.
- Every mob is a vanilla base type with overrides. Registering new entity types
  is a registry-level NMS commitment that breaks on every Minecraft release and
  buys almost nothing the Goal API and display entities cannot fake.

### Definitions

- Structured YAML and a shorthand form parse to the identical tree through the
  same parser, so there is one validator and one set of error messages. Mixing
  both in one file is fine. `DesugaringTest` is what holds that claim up.
- Every numeric and string parameter accepts placeholders and arithmetic,
  resolved at execution time, across the `caster`, `target`, `trigger`,
  `origin`, `skill.var`, `mob.var`, `global.var`, `random` and `math`
  namespaces, plus `papi` where PlaceholderAPI is installed.
- A bracketed token in a namespace nobody registered is left exactly as
  written, so MiniMessage survives.
- Parse failures are per definition, not per file: one broken skill does not
  take out the other forty beside it. Each failure names the file, the YAML
  path, the offending value and what was expected.
- A condition that needs an entity but sits in a slot that only ever holds a
  location is a load-time error rather than a boss that quietly targets
  nothing.

### Spawning

- Structure anchors: a marker placed by a world generator is adopted on chunk
  load, its position recorded and the entity deleted. From then on the mob
  spawns on player proximity with a persisted respawn cooldown, so nothing
  spawns in the thousands of structures nobody has visited.
- Position and existence are stored separately, which is what makes any removal
  Bestiary did not intend — peaceful difficulty, a fall into the void, another
  plugin's `remove()` — self-healing on the next visit.
- Identity comes from a scoreboard tag where the generator can write one, and
  from an entity-type map in `config.yml` where it cannot.

### Custom AI

- Custom goals go through Paper's supported goal-selector API, so they cost
  zero NMS and work across the entire supported band — including stripping a
  Ravager's vanilla goals entirely and replacing them.
- `navigation`, `move_control`, `look_control` and brain manipulation need a
  Mojang-mapped runtime and activate on Paper 1.20.5+. If any of it fails to
  resolve, the whole tier disables itself with one report line rather than
  half-working.
- **Custom AI requires Paper.** On Spigot, mobs keep their vanilla base AI plus
  scripted movement mechanics. This is reported at startup rather than left to
  be discovered.

### Performance

- Four guards, because skills call skills: recursion depth 32, 4000 mechanics
  per execution, 64 targets per targeter, and 5 ms of wall clock per tick after
  which the execution is suspended and resumed.
- Exceeding any one aborts that execution and logs once per skill per minute
  with the skill id and the node path. Silently truncating would be worse: a
  boss that half-fires is a bug report nobody can reproduce.
- Skill trees are walked over an explicit frame stack rather than by recursion,
  which is what makes suspend-and-resume possible at all — a Java call stack
  cannot be paused and resumed next tick.
- Polled triggers are resolved at compile time to the GCD of the declared
  intervals, so a mob with one `~onTimer:160` costs one task every 160 ticks
  rather than a per-tick task that checks a counter.

### Commands

- `/bestiary spawn · kill · list · info · cast · reload · anchor · spawner ·
  droptable · import · debug · menu · platform`
- `cast` runs a skill from you with a live trace of targeter resolution and
  condition results; `debug` attaches that trace to a running mob.

### API and integrations

- `bestiary-api` carries no Keystone types, so a third party compiles against
  the same class names the shipped jar resolves at runtime.
- Mechanic, targeter, condition and AI goal types are all registrable from
  another plugin, and unregister cleanly when it disables.
- Optional integrations, all probed and all reported once at startup: Sigil,
  PlaceholderAPI (consumed *and* provided as `%bestiary_*%`), Vault, WorldGuard,
  GriefPrevention, ModelEngine, Citizens, mcMMO, Jobs and AetherCore.

### Migrating from MythicMobs

- `/bestiary import <file|dir>` converts mobs, skills and drop tables to native
  YAML. It is a one-shot converter, not runtime compatibility.
- Anything unmappable is written into the output file in place and named in the
  summary — a boss that converts to 90% of itself and never says so is a very
  expensive debugging session.

### Built on Keystone

- Messages, config handling, the command tree, registries and scheduling come
  from [Keystone](https://github.com/bwmp-dev/Keystone).
- Keystone and Adventure are both shaded and relocated into the plugin. CI
  asserts the relocation held, because a regression there compiles perfectly
  and only fails at runtime.
- Nothing uses `BukkitRunnable` or `Bukkit.getScheduler()`; every task goes
  through the scheduler at the owning entity or region. That is what
  `folia-supported: true` rests on.
