<div align="center">
  
<img width="800" height=auto alt="image" src="https://github.com/user-attachments/assets/8eec0f17-e161-4c61-b6af-8eb1101b9900" />
<br><br>

![Downloads](https://shieldcn.dev/modrinth/downloads/Egw2R8Fj.svg?logo=modrinth&label=Downloads&variant=outline)
![CodeFactor](https://shieldcn.dev/badge/CodeFactor-A-green.svg?logo=codefactor&variant=outline)
![License](https://shieldcn.dev/github/license/blax-k/SpawnElytra.svg?label=License&variant=outline)
[![Docs](https://shieldcn.dev/badge/Docs-GitBook-blue.svg?logo=gitbook&variant=outline)](https://blaxk.gitbook.io/spawnelytra)
[![Build](https://shieldcn.dev/github/ci/blax-k/SpawnElytra.svg?label=Build&variant=outline)](https://github.com/blax-k/SpawnElytra/actions/workflows/build.yml)
<br>

_Players can use an Elytra-like feature inside configurable zones (e.g. around spawn) and when leaving them. Boost yourself by pressing the offhand button._
</div>

## Features

### Core Functionality
- **Elytra Flying in Zones**: Players can use elytra-like flying inside configurable zones without needing an actual elytra item
- **Boost System**: Boost with the offhand key (F key by default)
  - Configurable boost strength, direction (forward or upward), number of boosts per flight and cooldown
  - Customizable sound effects for boost activation
  - Boost hints in the actionbar, as a bossbar (remaining boosts + cooldown countdown) or hidden (`boost_display`)
- **Zones** (new in 1.6): any number of named zones in any number of worlds
  - Shapes: circle (fixed center or following the world spawn), rectangle or polygon
  - Optional height limits (`min_y` / `max_y`)
  - Every zone has its own activation mode, boost, F-key, hunger, fireworks and boost-display settings
  - Overlapping zones are resolved by priority (ties alphabetical); a glide stays bound to the zone it started in
- **Permission Tiers**: give groups more boosts, stronger boosts, shorter cooldowns or a higher F-key launch via `spawnelytra.tier.<name>`
- **Player Toggle**: players can switch spawn elytra off and on for themselves with `/se toggle`
- **Statistics**: flights, glided distance, boosts, glide time and longest flight per player (`/se stats`, placeholders)

### Activation Modes
- **Double Jump**: Double-press space bar to activate elytra
- **Auto**: Automatically activates when player has air below and is in spawn area
- **Sneak Jump**: Sneak while jumping to activate elytra
- **F-Key**: Press F (swap hands key) to activate with an upward launch boost

### Configuration & Customization
- **Per-Zone Settings**: Configure different elytra settings for each zone
- **Game Mode Restrictions**:
  - Option to disable elytra in creative mode (prevents buggy flying); with `disable_in_creative: false` creative players glide inside zones instead of creative-flying
  - Option to disable elytra in adventure mode
- **Firework Control**: Option to disable fireworks when using spawn elytra (global or per zone)
- **Hunger Consumption System**:
  - Optional hunger cost for using elytra features (global or per zone)
  - Multiple consumption modes: activation-based, distance-based, or time-based
  - Configurable minimum food level protection
- **Message Customization**:
  - Choose between Classic and Small Caps text styles
  - Toggle boost-related messages
  - Optional actionbar notice when a creative player enters a zone while creative is disabled
  - MiniMessage format support for advanced text formatting and clickable components

### Administration & Setup
- **Zone Overview**: `/se` opens an overview of all zones with buttons for settings, in-world editing, teleport, enable/disable and delete
  - **Dialogs** on Paper/Folia 1.21.6+ (Paper Dialog API, detected at runtime), **clickable chat menus** on older servers (`menus.mode: auto|dialog|chat`)
  - Zone settings and global settings menus cover every option; changes apply immediately, no reload needed
- **In-World Zone Editor**: `/se zone create <name> [circle|rectangle|polygon]` or `/se zone edit <name>` gives you a tool hotbar:
  1. shape tool (circle center / rectangle corners / polygon points), 2. radius / resize (sneak + scroll),
  3. height limits, 4. move, 5. shape switch, 6. undo / redo, 7. cancel (sneak + right-click), 8. settings, 9. save
  - Live preview built from glowing display entities that only you can see, other zones in gray, overlaps in red, a label with name, size, mode and priority, plus a bossbar and actionbar hints
  - Your inventory is stored while editing (also on disk) and restored on save, cancel, logout, death, world change, reload, server stop and even after a crash
- **Commands for everything**: `/se zone list|tp|enable|disable|rename|priority|delete|set ...` (scriptable, with tab completion)
- **Area Visualization**: `/spawnelytra visualize [zone] [seconds]` displays zone boundaries with particles
  - Configurable vertical range and particle effects
  - Enhanced particle options for better visibility
  - Customizable update frequency and particle size
- **Settings Menu**: Language and message style via `/spawnelytra settings`
- **Configuration Reload**: Hot-reload configuration without server restart
- **Automatic Migration**: 1.5 configs (`worlds:`) are converted into zones on startup (a backup is created first)
- **Update Notifications**: Automatic notification when new plugin versions are available

### Integrations & Compatibility
- **Server Software**: Paper 1.21 – 1.21.11 and 26.x, plus Folia 1.21.4+ and 26.x (fully region-thread safe). One jar runs on all of them (Java 21+, Java 25 for 26.x servers)
- **Multi-Language Support**: Built-in support for English, German, Spanish, French and Polish
- **PlaceholderAPI Integration**: Advanced placeholder support for other plugins
- **Geyser/Floodgate Support**: Full Bedrock Edition support (see below)
- **Legacy Migration**: Automatic migration from CraftAttackSpawnElytra plugin with detection to prevent re-migration
- **Metrics**: bStats integration for anonymous usage statistics

### Bedrock (Geyser/Floodgate) Support
Bedrock Edition clients are authoritative about gliding and refuse to glide without a real
elytra equipped, so the invisible-elytra trick used for Java players cannot work there.
Instead, SpawnElytra detects Bedrock players (via the Floodgate API, the Geyser API, or the
Floodgate UUID format on proxy setups) and:

- **Temporary elytra**: While inside a zone, Bedrock players receive a protected,
  unbreakable temporary elytra and deploy it natively (jump, then press jump again while
  falling). Their original chestplate is stored in the player's own persistent data, which
  is saved atomically with the inventory and returned automatically when they leave the
  area, land outside it, log out, die, or the server crashes mid-flight. The temporary
  elytra cannot be moved, dropped, stored, or duplicated. Other players keep seeing the
  original chestplate, and the "Sky's the Limit" advancement is granted without the chat
  broadcast. Players who rejoin mid-flight simply keep flying and get their chestplate
  back when they land.
- **Sneak to boost**: Bedrock has no offhand/F key, so Bedrock players boost by pressing
  sneak while gliding.
- **No clickable chat**: Bedrock cannot click chat components, so all interactive menus
  (welcome, settings, setup, update notifications) show plain commands to Bedrock players.

### Important:
The **default language** of this plugin **is English**, but you can change it (e.g. to German) by changing `language: en` to `language: de` in config.yml, or simply use the in-game language picker shown on first install!

## Configuration

<details>
<summary>Default Config</summary>

```yaml
# Spawn Elytra Plugin by blaxk
# Plugin Version: 1.6
# Modrinth: https://modrinth.com/plugin/spawn-elytra

# ==========================================
# GLOBAL SETTINGS
# ==========================================

# Available languages: en, de, es, fr, pl
language: en

# Game mode restrictions
game_modes:
  # Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)
  # If set to false, creative players can use spawn elytra too: inside a zone, double-jumping starts gliding
  # instead of creative flight (outside zones creative flight works as usual)
  disable_in_creative: true
  # If you don't want to disable elytra in adventure mode, set this to false
  disable_in_adventure: false

# Fireworks settings
fireworks:
  # Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)
  disable_in_spawn_elytra: false

# Bedrock (Geyser/Floodgate) support
bedrock:
  # Bedrock Edition cannot glide without a real elytra equipped. When enabled,
  # Bedrock players inside a zone receive a temporary elytra. They use
  # it like a normal elytra: jump, then press jump again while falling. Since
  # Bedrock has no offhand/F key, Bedrock players always boost by pressing sneak
  # while gliding.
  enabled: true

# Message settings
messages:
  # Set to false to disable the "press to boost" message
  show_press_to_boost: true
  # Set to false to disable the "boost activated" message
  show_boost_activated: true
  # Set to true to show an actionbar when Elytra is disabled in Creative mode (shown once when entering a zone)
  show_creative_disabled: false
  # Message style: classic or small_caps
  style: classic

# Where boost hints are shown while gliding (can be overridden per zone):
# actionbar: "Press F to boost" messages in the actionbar (like 1.5)
# bossbar: a bossbar with the remaining boosts and the cooldown
# none: no boost hints
boost_display: actionbar

# Admin menus (/se, zone settings, global settings)
menus:
  # auto: dialogs on servers that support them (1.21.6+), chat menus otherwise
  # dialog: always use dialogs (falls back to chat if unsupported)
  # chat: always use clickable chat menus
  mode: auto

# Visualization settings for /spawnelytra visualize command
visualization:
  # Vertical range above and below player for particle display
  vertical_range: 20
  # Additional vertical range for corner/cardinal pillars
  pillar_vertical_range: 25
  # Particle update frequency (ticks between updates, lower = more frequent)
  update_frequency: 10
  # Particle size multiplier for better visibility from distance
  particle_size: 2.0
  # Enable enhanced particles (brighter colors, additional effects)
  enhanced_particles: true

# Hunger consumption settings (global defaults, can be overridden per-zone)
hunger_consumption:
  # Enable hunger consumption while using the spawn elytra features
  enabled: false
  # How hunger should be consumed: activation, distance, or time
  mode: activation
  # Minimum food level to keep (players will never drop below this value)
  minimum_food_level: 0

  activation:
    # Hunger consumed each time the elytra activates
    hunger_cost: 1

  distance:
    # Blocks travelled while gliding before hunger is consumed
    blocks_per_point: 50.0
    # Hunger consumed every time the distance threshold is reached
    hunger_cost: 1

  time:
    # Seconds of gliding before hunger is consumed
    seconds_per_point: 30
    # Hunger consumed each time the timer elapses
    hunger_cost: 1

# Permission tiers: give players better boosts with the permission spawnelytra.tier.<name>
# (not granted to anyone by default, not even OPs). The tier with the highest priority wins.
# Every value is optional and overrides the zone value. Example:
# permission_tiers:
#   vip:
#     priority: 10
#     max_boosts: 3
#     strength: 5.0
#     boost_cooldown: 1
#     launch_strength: 2.0
permission_tiers: {}

# ==========================================
# ZONES
# ==========================================

# A zone is a named area in one world where spawn elytra works. You can have many zones
# in many worlds. Manage them in-game with /se (overview), /se zone create <name>,
# /se zone edit <name> and /se zone set <name> <key> <value>.
# Zone names: lowercase letters, digits, '_' and '-' (max. 32 characters).

# Optional keys per zone (remove them to use the global value / no limit):
#   min_y / max_y: height limits of the zone
#   boost_display: actionbar, bossbar or none
#   fireworks: { disable_in_spawn_elytra: true/false }
#   hunger_consumption: same keys as the global hunger_consumption section
zones:
  spawn:
    # World of this zone (e.g. world, world_nether, world_the_end)
    world: world
    # Enable spawn elytra in this zone
    enabled: true
    # If zones overlap, the zone with the highest priority wins (same priority: alphabetical)
    priority: 0

    # Shape of the zone: circle, rectangle or polygon
    # circle: uses 'center' and 'radius'
    # rectangle: uses 'corner1' and 'corner2' ({x, z} each)
    # polygon: uses 'points' (a list of at least 3 {x, z} points)
    shape: circle
    # Center of the circle: world_spawn (follows the world spawn point) or fixed coordinates {x, z}
    center: world_spawn
    # Radius of the circle in blocks
    radius: 100

    # Activation mode for elytra:
    # double_jump: Player needs to double-press space to activate elytra
    # auto: Automatically activates elytra when player has air below and is in the zone
    # sneak_jump: Player needs to sneak while jumping to activate elytra
    # f_key: Player needs to press F (swap hands) to activate elytra, this also boosts a player upwards on activation
    activation_mode: double_jump

    # Boost settings
    boost:
      # Enable boost functionality
      enabled: true
      # The strength of the boost when pressing the boost key
      strength: 4
      # Boost direction: 'forward' or 'upward'
      # forward: Boosts player in the direction they are looking
      # upward: Boosts player straight up
      direction: forward
      # Maximum number of boosts allowed per elytra flight (1 = single boost)
      max_boosts: 1
      # Cooldown in seconds between boosts (0 = no cooldown, only applies when max_boosts > 1)
      boost_cooldown: 0
      # Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html
      # Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING
      sound: ENTITY_BAT_TAKEOFF

    # F-key specific settings (only used when activation_mode: f_key)
    f_key:
      # Launch strength when pressing F key (1.5 = ~14-15 blocks upward)
      launch_strength: 1.5
```
</details>

## Commands and Permissions

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/spawnelytra` or `/se` | Zone overview (admins) / help (everyone else) | `spawnelytra.admin` for the overview | Operators only |
| `/se info` | Plugin info and the settings of the zone you are in (console: list of zones) | `spawnelytra.info` | All players |
| `/se toggle` | Turn spawn elytra off/on for yourself | `spawnelytra.toggle` | All players |
| `/se stats [player]` | Show flight statistics (yourself / another player) | `spawnelytra.stats` / `spawnelytra.stats.others` | All players / Operators |
| `/se zone create <name> [shape]` | Create a zone around you and open the editor | `spawnelytra.admin` | Operators only |
| `/se zone edit <name>` | Edit a zone in the world | `spawnelytra.admin` | Operators only |
| `/se zone settings <name>` | Open the settings of a zone | `spawnelytra.admin` | Operators only |
| `/se zone set <name> <key> <value>` | Change any zone setting (e.g. `boost.strength 5`) | `spawnelytra.admin` | Operators only |
| `/se zone list` | List all zones | `spawnelytra.admin` | Operators only |
| `/se zone tp <name>` | Teleport to a zone | `spawnelytra.admin` | Operators only |
| `/se zone enable\|disable <name>` | Turn a zone on or off | `spawnelytra.admin` | Operators only |
| `/se zone rename <old> <new>` | Rename a zone | `spawnelytra.admin` | Operators only |
| `/se zone priority <name> <number>` | Set the overlap priority | `spawnelytra.admin` | Operators only |
| `/se zone delete <name>` | Delete a zone (asks for confirmation) | `spawnelytra.admin` | Operators only |
| `/se global` / `/se global set <key> <value>` | Global settings menu / change a global setting | `spawnelytra.admin` | Operators only |
| `/se setup` | Create the first zone or open the overview | `spawnelytra.admin` | Operators only |
| `/se set pos1\|pos2` | Set rectangle corners at your position (in the editor) | `spawnelytra.admin` | Operators only |
| `/se visualize [zone] [seconds]` | Visualize zones with particles (default: all zones in your world) | `spawnelytra.admin` | Operators only |
| `/se settings` | Language and message style menu | `spawnelytra.admin` | Operators only |
| `/se reload` | Reload plugin configuration | `spawnelytra.admin` | Operators only |
| `/se update` | Download and install the latest release (requires server restart) | `spawnelytra.admin` | Operators only |
| `/se dismiss` | Dismiss the first install welcome message | `spawnelytra.admin` | Operators only |

### Permissions

- `spawnelytra.admin` - all administrative commands and menus (default: op)
- `spawnelytra.use` / `spawnelytra.useboost` - use spawn elytra / boost (default: everyone)
- `spawnelytra.info`, `spawnelytra.toggle`, `spawnelytra.stats` - default: everyone
- `spawnelytra.stats.others` - see other players' statistics (default: op)
- `spawnelytra.tier.<name>` - permission tier from `permission_tiers` (default: nobody, not even operators)

### Placeholders (PlaceholderAPI)

`%spawnelytra_flights%`, `%spawnelytra_distance%`, `%spawnelytra_boosts%`, `%spawnelytra_glide_time%`,
`%spawnelytra_longest_flight%`, `%spawnelytra_zone%` (current zone or empty), `%spawnelytra_enabled%`,
`%spawnelytra_tier%`, plus the 1.5 placeholders `fly_count`, `boost_count`, `total_count`, `flying`, `in_area`
and `boosts_remaining`.

## Fabric

A server-side Fabric version with the same behaviour, config file, language files, commands and
permissions is available for Minecraft 1.21 – 1.21.11 and 26.1 – 26.3 (vanilla clients can join).
It needs Fabric API; LuckPerms, Text Placeholder API (same placeholders as the PlaceholderAPI
expansion) and Geyser/Floodgate are supported when installed. The config lives in
`config/spawnelytra/config.yml` and a Paper config can be copied over unchanged (1.5 configs are
migrated to zones exactly like on Paper). Everything in 1.6 works the same on Fabric: zones, the
in-world zone editor (its preview uses packet-only display entities that only the editing player
sees), permission tiers via LuckPerms / fabric-permissions-api, statistics, `/se toggle` and the
boost bossbar. The admin menus are vanilla dialogs on Minecraft 1.21.6 and newer and clickable chat
menus on 1.21 – 1.21.5 (`menus.mode` works as on Paper). Placeholders use the Text Placeholder API
syntax, e.g. `%spawnelytra:flights%`, `%spawnelytra:zone%`, `%spawnelytra:enabled%`. See
[`fabric/README.md`](fabric/README.md) for the version table, world naming and how to build it.

## Support

If you encounter any issues while using the plugin, please [create an issue](https://github.com/blax-k/SpawnElytra/issues) on GitHub.
