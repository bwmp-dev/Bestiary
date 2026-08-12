# Contributing

## Commit messages decide the version

Releases are automated with [release-please](https://github.com/googleapis/release-please). It reads [Conventional Commits](https://www.conventionalcommits.org) from the default branch and keeps a release PR open with the next version and a generated changelog. **Merging that PR is what cuts a release** — nothing ships until you do.

This means the version number is a consequence of how you write commit messages, not something anyone edits by hand.

| Prefix | Effect | Use for |
|---|---|---|
| `fix:` | patch — `1.2.3` → `1.2.4` | bug fixes |
| `feat:` | minor — `1.2.3` → `1.3.0` | new behaviour |
| `feat!:` or a `BREAKING CHANGE:` footer | major, once past 1.0 | anything that breaks `bestiary-api` or a config format |
| `docs:` `chore:` `refactor:` `test:` `ci:` | no release | everything else |

```
feat: add tag ingredients to drop tables

Drop tables can now reference #minecraft:planks. Tags resolve to a
fixed material list at load, so what a table can drop cannot drift.
```

To force a specific version, add a footer to any commit:

```
Release-As: 1.0.0
```

## What counts as breaking

`bestiary-api` is the surface the version promises apply to. Changing it breaks other people's plugins, so treat it as public API — that includes the mechanic, targeter, condition and goal registration entry points, not just the types they take.

Config formats matter too. A key that server owners already have in their `mobs/`, `skills/`, `droptables/` or `spawners/` files cannot be renamed without a migration path, because their old key would simply stop doing anything, silently.

The shorthand grammar is a config format. Changing what `damage{amount=9} @playersInRadius{r=7} ?onGround` parses to breaks every file that used it.

## Building

```
mvn package
```

Java 17, Maven 3.9+. The reactor is four modules:

| Module | Compiled against | Role |
|---|---|---|
| `bestiary-api` | spigot-api 1.19.4 only | public API. **No Keystone types.** |
| `bestiary-plugin` | spigot-api 1.19.4 + keystone-core | engine, parser, config, commands, storage; shades and relocates Keystone |
| `bestiary-ai` | paper-api 1.19.4 | Paper Goal API and Pathfinder layer, loaded reflectively |
| `bestiary-ai-nms` | paper-api 1.19.4, NMS via `MethodHandles` | navigation, move/look control, brains; 1.20.5+ only |

`bestiary-plugin` is parented to `dev.bwmp:keystone-parent`, which is what
supplies the shade plugin and the relocations. Maven resolves a parent before it
reads the project's own `<repositories>`, so `nexus.bwmp.dev` has to be known
from your `settings.xml`, or Keystone installed into your local `.m2`.

## The rules that are not style

**`bestiary-api` must not reference a shaded type.** The shipped jar contains
`dev.bwmp.bestiary.libs.keystone.*`; a third party compiling against the
published API would resolve `dev.bwmp.keystone.*` and get a
`NoClassDefFoundError` naming a class that looks entirely correct. Anything the
API re-exposes is re-declared in that module — see `BestiaryScheduler`.

**`bestiary-plugin` must not name a Paper type.** The same jar runs on Spigot
1.19.4, where `MobGoals` does not exist and a static reference fails at
class-load. The AI tier is reached through `AiController`, which mentions only
Bukkit, and loaded by name behind a `Platform.classExists` probe.

**Nothing uses `BukkitRunnable` or `Bukkit.getScheduler()`.** Every task goes
through the scheduler at the owning entity or region. That claim is what
`folia-supported: true` in `plugin.yml` rests on.

**No enum constant that moved.** `Attribute`, `Particle`, `PotionEffectType` and
`Sound` all changed shape between 1.19.4 and 26.x. Everything goes through
`Attributes` or `Registries`, or is passed as a string.

## Tests

```
mvn test
```

The parser, the shorthand front-end, expressions and the executor guards are
pure JVM and have no server dependency — the executor tests drive a `Proxy` over
Bukkit's `Entity` interface. Those are the components where a silent mistake
costs the most, so they are the ones with tests.

`docs/mechanics.md`, `docs/targeters.md` and `docs/conditions.md` are generated
from the declarations by `DocumentationTest`, which fails if the committed files
have drifted. After changing a parameter:

```
mvn test -Dbestiary.docs.write=true
```

## Adding a mechanic

Add it to the relevant `*Mechanics` class rather than creating a file per
mechanic — the declaration and the behaviour belong adjacent, and the library is
meant to read as a list of what each one does.

Declare every parameter, including its shorthand aliases. The parser reads the
declaration before it builds anything, so a declared parameter is one that can
be misspelled usefully; an undeclared one is a silent default.

Pick the narrowest `TargetKind` that is true. `NONE` means the mechanic runs
once per line rather than once per resolved target, which is what `delay` and
`skill` need and what `damage` must not have.

## Versions in poms are generated

Every version release-please owns is annotated:

```xml
<version>1.0.0</version> <!-- x-release-please-version -->
```

Do not edit those by hand. Equally, **do not add that annotation to a version that is not ours** — `bestiary-plugin/pom.xml` references `keystone-parent`, and bumping that would point Bestiary at a Keystone release that does not exist. Only versions belonging to this repository carry the marker.

## Before opening a PR

```
mvn install    # in Keystone first, then here
mvn test
```

CI builds on every PR. If you touched anything to do with shading, relocation or the AI tiers, say which server versions you tested on — the supported band runs from 1.19.4 to 26.x across Spigot, Paper, Purpur and Folia, and a change can be correct on one and wrong on another.

`/bestiary platform` reports which tier and scheduler a server actually selected, which is usually the fastest way to tell.
