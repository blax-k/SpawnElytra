# Spawn Elytra for Fabric

A server-side Fabric port of the Spawn Elytra Paper plugin (v1.6) with the same behaviour, the
same `config.yml`, the same language files and the same commands and permissions.
The platform-neutral 1.6 logic (zones, geometry, migration, tiers, statistics, editor drafts and
menu screens) is the shared `common/` core that the Paper plugin uses too. Vanilla clients
can join; nothing has to be installed on the client. The mod also runs in single player (integrated
server).

## Supported versions

One jar per range. Every range was boot-tested on its lowest and highest Minecraft version.

| Jar | Minecraft | Java |
|-----|-----------|------|
| `spawnelytra-fabric-1.6+1.21-1.21.1.jar` | 1.21 – 1.21.1 | 21+ |
| `spawnelytra-fabric-1.6+1.21.2-1.21.3.jar` | 1.21.2 – 1.21.3 | 21+ |
| `spawnelytra-fabric-1.6+1.21.4.jar` | 1.21.4 | 21+ |
| `spawnelytra-fabric-1.6+1.21.5.jar` | 1.21.5 | 21+ |
| `spawnelytra-fabric-1.6+1.21.6-1.21.8.jar` | 1.21.6 – 1.21.8 | 21+ |
| `spawnelytra-fabric-1.6+1.21.9-1.21.10.jar` | 1.21.9 – 1.21.10 | 21+ |
| `spawnelytra-fabric-1.6+1.21.11.jar` | 1.21.11 | 21+ |
| `spawnelytra-fabric-1.6+26.1-26.2.jar` | 26.1 – 26.2 | 25+ |
| `spawnelytra-fabric-1.6+26.3.jar` | 26.3 | 25+ |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/server/) (0.16 or newer) and
   [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the jar for your Minecraft version into `mods/`.
3. Start the server. The configuration is created in `config/spawnelytra/`.

Optional companions, detected at runtime (the mod works without them):

- **LuckPerms** (or any mod implementing fabric-permissions-api) for the permission nodes.
- **[Text Placeholder API](https://modrinth.com/mod/placeholder-api)** for placeholders.
- **Geyser-Fabric / Floodgate-Fabric** for Bedrock players.

## Configuration

`config/spawnelytra/config.yml` has exactly the same format, keys, comments and defaults as the
Paper plugin's `plugins/SpawnElytra/config.yml` (the build bundles the Paper plugin's own
`config.yml` and `lang/*.yml`). A Paper config can be copied over unchanged; old 1.2/1.3/1.4
configs are migrated with a timestamped backup in `config/spawnelytra/backups/`, just like on Paper.
YAML is written with Bukkit's algorithm (same SnakeYAML settings, same comment and header
handling), so files saved by the mod (e.g. by the setup wizard) are byte-identical to the ones
Paper would write.

Other files, as on Paper: `lang/` (language files, upgraded when outdated) and `playerdata/<uuid>.yml`
(flight/boost statistics).

### Zones and world names

1.6 replaces the per-world area with named **zones** (`zones:` section). 1.5 configs (`worlds:`)
are migrated automatically on load with a backup, byte-identical to the Paper migration. Fabric has
no world names, so a zone's `world:` accepts both forms:

| Key | Dimension |
|-----|-----------|
| `world` (or your `level-name`) | `minecraft:overworld` |
| `world_nether` | `minecraft:the_nether` |
| `world_the_end` | `minecraft:the_end` |
| `world_<namespace>_<path>` | a datapack dimension, named like Paper names it |
| `minecraft:overworld`, `mypack:skylands`, ... | the dimension with that id |

Matching is case-insensitive like `Bukkit.getWorld`. Zones created in-game get the Bukkit-style
name (`world`, `world_nether`, ...), so the config stays interchangeable with Paper.

### Sounds

`boost.sound` takes the Bukkit `Sound` names (`ENTITY_BAT_TAKEOFF`, case-insensitive), resolved
against the game's sound registry with Bukkit's naming rule, or a sound id such as
`minecraft:entity.firework_rocket.blast`. An invalid value is replaced with `ENTITY_BAT_TAKEOFF`
and a warning is logged, as on Paper.

## Commands and permissions

Identical to the Paper plugin: `/spawnelytra` (alias `/se`) opens the zone overview for admins;
`zone create|edit|delete|list|tp|enable|disable|rename|priority|set|settings`, `global [set]`,
`toggle`, `stats [player]`, `info`, `reload`, `update`, `visualize [zone] [seconds]`, `settings`,
`options`, `setup`, `set pos1|pos2`, `set language <code>`, `set style classic|small_caps`, `dismiss`.
Non-admins get the help listing only what they may use.

| Permission | Default | Meaning |
|------------|---------|---------|
| `spawnelytra.admin` | operators | overview, zones, editor, global settings, reload, update, visualize |
| `spawnelytra.info` | everyone | `/se info` |
| `spawnelytra.toggle` | everyone | `/se toggle` |
| `spawnelytra.stats` | everyone | own `/se stats` |
| `spawnelytra.stats.others` | operators | `/se stats <player>` |
| `spawnelytra.use` | everyone | use the spawn elytra |
| `spawnelytra.useboost` | everyone | use the boost |
| `spawnelytra.tier.<name>` | nobody (not even operators) | permission tier `<name>` |

Permissions are checked through fabric-permissions-api (bundled), so LuckPerms works out of the box.
Without a permission mod the defaults above apply ("operators" means players in the ops list, like
Bukkit's `isOp()`; the console always has every permission).

## Menus and the zone editor

- **Menus** (`/se`, zone settings, global settings, delete confirmation, stats) are vanilla
  **dialogs** on Minecraft 1.21.6 and newer (the 1.21.6-1.21.8, 1.21.9-1.21.10, 1.21.11, 26.1-26.2
  and 26.3 jars) and **clickable chat menus** on 1.21 - 1.21.5. `menus.mode: chat` forces chat
  menus everywhere. Dialog buttons send `spawnelytra:*` custom click actions carrying a per-player
  token; stale or forged clicks are ignored.
- **Editor** (`/se zone edit <name>`): the inventory is stored (and persisted to
  `playerdata/editor-inventory/<uuid>.yml`), the hotbar gets the nine tools, and the outline is
  drawn with glowing display entities that exist only as packets for the editing player (nothing is
  spawned on the server, nothing can be saved with the world). The inventory is restored on save,
  cancel, quit (or on the next join after a crash), death (on respawn, nothing drops) and world
  change. Tools are marked with the custom data entry `spawnelytra:editor_tool`.

## Placeholders

With Text Placeholder API installed, the same placeholders as the PlaceholderAPI expansion are
available: `%spawnelytra:fly_count%`, `%spawnelytra:boost_count%`, `%spawnelytra:total_count%`,
`%spawnelytra:flying%`, `%spawnelytra:in_area%`, `%spawnelytra:boosts_remaining%` and (1.6)
`%spawnelytra:flights%`, `%spawnelytra:distance%`, `%spawnelytra:boosts%`, `%spawnelytra:glide_time%`,
`%spawnelytra:longest_flight%`, `%spawnelytra:zone%`, `%spawnelytra:enabled%`, `%spawnelytra:tier%`.

## Bedrock players

With Floodgate or Geyser for Fabric installed (or Floodgate-style UUIDs on proxy setups), Bedrock
players get the protected temporary elytra inside the spawn area and boost with sneak, exactly as
described in the main README. The original chestplate is stored in a persistent player attachment
(saved with the player data, kept on death) and other players keep seeing it.

## Differences to the Paper plugin

- **bStats**: there is no official bStats library for Fabric, so no metrics are sent.
- **Auto update** (`/spawnelytra update`): Modrinth versions are filtered by `loader=fabric` and the
  running Minecraft version. Fabric has no `plugins/update` folder, so the new jar is written to
  `mods/` and the old jar is removed (immediately on Linux; on Windows when the server stops).
  A server restart is required, as on Paper. Until a Fabric build is published on Modrinth, the
  update check simply finds nothing (no warning is logged).
- **Legacy `CraftAttackSpawnElytra` migration**: not applicable on Fabric (that plugin never existed
  for Fabric).
- **Double-jump priming after a game mode switch**: if vanilla clears allow-flight while a player is
  primed (e.g. survival → adventure → survival inside the area), the Fabric build primes the player
  again on the next move; the Paper 1.5 build keeps a stale entry until the player leaves the area.
- **Editor preview**: Paper spawns real (hidden, non-persistent) display entities; Fabric sends
  packet-only fake entities. Both are visible only to the editing player.

## Building

The build is a standalone Gradle project using [Stonecutter](https://stonecutter.kikugie.dev/) for
the version nodes and Fabric Loom (remapping Loom with Mojang mappings for 1.21.x, the
non-remapping Loom for the unobfuscated 26.x). It needs **JDK 25** (the 1.21.x jars are compiled
with `--release 21`).

```sh
cd fabric
./gradlew buildAll            # all jars -> fabric/build/libs/
./gradlew :1.21.11:build      # a single node
```

The shared core is compiled into every node from `../common/src/main/java` (an extra source
directory, see `build.gradle.kts`); its unit tests run in the root (Paper) build.

Version-specific code is isolated with Stonecutter comments (`//? if >=1.21.9 { ... }`), mostly in
`util/Compat.java`. The source is kept in the 1.21.11 state (`stonecutter active`). Mixins are only
used where Fabric API has no event: the abilities packet (double jump), the swap-hands action, player
movement and typed commands, sneaking, the elytra glide state, game mode changes, death, inventory
clicks and the advancement broadcast (Bedrock temp elytra), and for the zone editor: hotbar slot
changes (scroll), arm swings (left click in the air), drops, creative inventory edits, pick-block,
item pickup and dialog custom click actions; plus accessors for the display entity data keys.
