<<<<<<< HEAD
<div align="center">
  
<img width="800" height=auto alt="image" src="https://github.com/user-attachments/assets/8eec0f17-e161-4c61-b6af-8eb1101b9900" />


![Downloads](https://img.shields.io/modrinth/dt/Egw2R8Fj?logo=modrinth&style=for-the-badge&label=Downloads&color=2C8904)
![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/blax-k/spawnelytra?label=CodeFactor&style=for-the-badge&color=2C8904)
![https://img.shields.io/github/license/blaxkkkk/SpawnElytra](https://img.shields.io/github/license/blax-k/SpawnElytra?&color=2C8904&label=License&style=for-the-badge)
[![Docs](https://img.shields.io/badge/Docs-GitBook-blue?style=for-the-badge&logo=gitbook&color=2C8904)](https://blaxk.gitbook.io/spawnelytra)
</div>

_Players can use an Elytra-like feature while in spawn and when leaving spawn. You can boost yourself once by pressing the offhand button._

## Features

### Core Functionality
- **Elytra Flying in Spawn Area**: Players can use elytra-like flying within designated spawn areas without needing an actual elytra item
- **Boost System**: One-time boost functionality using the offhand key (F key by default)
  - Configurable boost strength and direction (forward or upward)
  - Customizable sound effects for boost activation
  - Optional "press to boost" messages
- **Flexible Spawn Area Definition**: 
  - Rectangular areas (defined by two corner points)
  - Circular/radius-based areas (defined by center point and radius)
  - Auto mode using world spawn point or advanced custom coordinates
  - Per-world configuration support

### Activation Modes
- **Double Jump**: Double-press space bar to activate elytra
- **Auto**: Automatically activates when player has air below and is in spawn area
- **Sneak Jump**: Sneak while jumping to activate elytra
- **F-Key**: Press F (swap hands key) to activate with an upward launch boost

### Configuration & Customization
- **Per-World Settings**: Configure different elytra settings for each world
- **Game Mode Restrictions**: 
  - Option to disable elytra in creative mode (prevents buggy flying)
  - Option to disable elytra in adventure mode
- **Firework Control**: Option to disable fireworks when using spawn elytra
- **Hunger Consumption System**: 
  - Optional hunger cost for using elytra features
  - Multiple consumption modes: activation-based, distance-based, or time-based
  - Configurable minimum food level protection
- **Message Customization**:
  - Choose between Classic and Small Caps text styles
  - Toggle boost-related messages
  - Configurable creative mode disabled notifications
  - MiniMessage format support for advanced text formatting and clickable components

### Administration & Setup
- **Interactive Setup Wizard**: Easy-to-use `/spawnelytra setup` command for defining spawn areas
- **Area Visualization**: `/spawnelytra visualize` command displays spawn area boundaries with particles
  - Configurable vertical range and particle effects
  - Enhanced particle options for better visibility
  - Customizable update frequency and particle size
- **Settings Menu**: Interactive in-game settings configuration via `/spawnelytra settings`
- **Configuration Reload**: Hot-reload configuration without server restart
- **Update Notifications**: Automatic notification when new plugin versions are available

### Integrations & Compatibility
- **Multi-Language Support**: Built-in support for English, German, Spanish and French
- **PlaceholderAPI Integration**: Advanced placeholder support for other plugins
- **Legacy Migration**: Automatic migration from CraftAttackSpawnElytra plugin with detection to prevent re-migration
- **Metrics**: bStats integration for anonymous usage statistics

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/spawn-elytra) or the [GitHub Releases](https://github.com/blax-k/SpawnElytra/releases)
=======
# Spawn Elytra

![Spawn Elytra](https://cdn.modrinth.com/data/cached_images/5df9a92fc92fc69760897e41dce2fc254ce90c10.png)

![Downloads](https://img.shields.io/modrinth/dt/Egw2R8Fj?logo=modrinth&style=flat&label=Downloads&color=38B541)
![CodeFactor](https://www.codefactor.io/repository/github/blaxkkkk/spawnelytra/badge)

_Players can use an Elytra-like feature while in spawn and when leaving spawn.
You can boost yourself once by pressing the offhand button._

## Features

- Elytra flying in spawn area
- Boost function with offhand key
- Custom configurable spawn area (rectangular or radius-based)
- Multiple activation modes (double jump or automatic)
- Customizable boost strength and sound effects
- Multi-language support (en, de, es, fr, hi, zh, ar)

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/spawn-elytra) or the [Releases page](https://github.com/blaxkkkk/CraftAttackSpawnElytra/releases)
>>>>>>> restore-v1.3
2. Place the .jar file in your server's plugins folder
3. Restart your server
4. Configure the plugin in the generated config.yml file

### Important:
The **default language** of this plugin **is German**, but you can change it to English by changing `language: de` to `language: en` in config.yml!

## Configuration

<details>
<summary>Default Config</summary>

```yaml
<<<<<<< HEAD
# Spawn Elytra Plugin by blaxk
# Plugin Version: 1.4
# Modrinth: https://modrinth.com/plugin/spawn-elytra

# ==========================================
# GLOBAL SETTINGS
# ==========================================

# Available languages: en, de, es, fr, ar
language: en

# Game mode restrictions
game_modes:
  # Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)
  disable_in_creative: true
  # If you don't want to disable elytra in adventure mode, set this to false
  disable_in_adventure: false

# Fireworks settings
fireworks:
  # Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)
  disable_in_spawn_elytra: false

=======
# Spawn Elytra Plugin by Blaxk_
# Plugin Version: 1.2
# Modrinth: https://modrinth.com/plugin/spawn-elytra
# Activation mode for elytra:
# double_jump: Player needs to double-press space to activate elytra
# auto: Automatically activates elytra when player has air below and is in spawn area
activation_mode: double_jump
# Mode options: 'auto' or 'advanced'
# auto: Uses the world spawn point
# advanced: Uses custom spawn coordinates defined below
mode: auto
# The radius around spawn where elytra boosting is enabled
# (only used in auto mode or if x2, y2, z2 are all set to 0)
radius: 100
# The strength of the boost when pressing the boost key
strength: 2
# The world where the spawn elytra feature is enabled
world: world
# Custom spawn coordinates and dimensions (used when mode is 'advanced')
spawn:
  # First point of the elytra area
  x: 0
  y: 64
  z: 0
  # Second point of the elytra area
  # Setting all x2/y2/z2 to 0 will use the radius-based circular area instead
  # Example: Using x=0, y=64, z=0 and x2=100, y2=128, z2=100 creates a rectangular area
  # between those two coordinate points
  x2: 0  # Second X coordinate
  y2: 0  # Second Y coordinate
  z2: 0  # Second Z coordinate
# Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html
# Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING
boost_sound: ENTITY_BAT_TAKEOFF
# Available languages: en, de, es, fr, hi, zh, ar
language: en
# Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)
disable_in_creative: true
>>>>>>> restore-v1.3
# Message settings
messages:
  # Set to false to disable the "press to boost" message
  show_press_to_boost: true
  # Set to false to disable the "boost activated" message
  show_boost_activated: true
<<<<<<< HEAD
  # Set to true to show an actionbar when Elytra is disabled in Creative mode
  show_creative_disabled: false
  # Message style: classic or small_caps
  style: classic

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

# Hunger consumption settings (global defaults, can be overridden per-world)
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

# ==========================================
# WORLD-SPECIFIC SETTINGS
# ==========================================

# Configure elytra settings per world
worlds:
  # If you want to add another world, copy the entire 'world' section and change the name and preferences
  world:
    # Enable spawn elytra in this world
    enabled: true

    # Activation mode for elytra:
    # double_jump: Player needs to double-press space to activate elytra
    # auto: Automatically activates elytra when player has air below and is in spawn area
    # sneak_jump: Player needs to sneak while jumping to activate elytra
    # f_key: Player needs to press F (swap hands) to activate elytra, this also boosts a player upwards on activation
    activation_mode: double_jump

    # The radius around spawn where elytra boosting is enabled
    # (only used when area_mode is 'circular' or spawn coords x2/y2/z2 are all 0)
    radius: 100

    # Spawn area configuration
    spawn_area:
      # Mode options: 'auto' or 'advanced'
      # auto: Uses the world spawn point with radius
      # advanced: Uses custom spawn coordinates defined below
      mode: auto

      # Area type: 'circular' or 'rectangular'
      area_type: circular

      # Primary spawn coordinates (center for circular, first corner for rectangular)
      x: 0
      y: 64
      z: 0

      # Secondary coordinates (only used for rectangular areas)
      # Setting all to 0 uses circular area with radius instead
      x2: 0
      y2: 0
      z2: 0

    # Boost settings
    boost:
      # Enable boost functionality
      enabled: true
      # The strength of the boost when pressing the boost key
      strength: 3
      # Boost direction: 'forward' or 'upward'
      # forward: Boosts player in the direction they are looking
      # upward: Boosts player straight up
      direction: forward
      # Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html
      # Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING
      sound: ENTITY_BAT_TAKEOFF

    # F-key specific settings (only used when activation_mode: f_key)
    f_key:
      # Launch strength when pressing F key (1.5 = ~14-15 blocks upward)
      launch_strength: 1.5
=======
  # Set to true to use custom messages below instead of language file messages
  use_custom_messages: false
  # Custom messages
  # {key} is used represent the offhand key (F by default)
  # Due to limitations from minecraft, you cant enter any key you want.
  # Legacy color codes (&a, &e, etc.) are supported
  press_to_boost: '&6Press &6&l{key} &6to boost yourself.'
  boost_activated: '&a&lBoost activated!'
>>>>>>> restore-v1.3
```
</details>

## Commands and Permissions

<<<<<<< HEAD
| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/spawnelytra` or `/se` | Show help menu | None | All players |
| `/spawnelytra reload` | Reload plugin configuration | `spawnelytra.admin` | Operators only |
| `/spawnelytra info` | Display plugin information | None | All players |
| `/spawnelytra visualize [seconds]` | Visualize the spawn elytra area with particles | `spawnelytra.admin` | Operators only |
| `/spawnelytra settings` | Open the interactive settings menu | `spawnelytra.admin` | Operators only |
| `/spawnelytra setup` | Start interactive setup wizard for defining the elytra area | `spawnelytra.admin` | Operators only |
| `/spawnelytra setup exit` | Exit the setup wizard | `spawnelytra.admin` | Operators only |
| `/spawnelytra set pos1` | Set the first position in setup mode | `spawnelytra.admin` | Operators only |
| `/spawnelytra set pos2` | Set the second position in setup mode | `spawnelytra.admin` | Operators only |
| `/spawnelytra dismiss` | Dismiss the first install welcome message | `spawnelytra.admin` | Operators only |

### Permissions

- `spawnelytra.admin` - Grants access to all administrative commands (reload, visualize, settings, setup)

## Support

If you encounter any issues while using the plugin, please [create an issue](https://github.com/blax-k/SpawnElytra/issues) on GitHub.
=======
- `/spawnelytra reload` - Reloads the plugin configuration
  - Permission: `spawnelytra.admin`
- `/spawnelytra info` - Get info about the Plugin
  - Permission: `spawnelytra.admin`

## Support

If you encounter any issues while using the plugin, please [create an issue](https://github.com/blaxkkkk/CraftAttackSpawnElytra/issues) on GitHub.

## Contributing

Contributions are welcome! Feel free to fork this repository and submit pull requests.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/amazing-feature`)
3. Commit your Changes (`git commit -m 'Add some amazing feature'`)
4. Push to the Branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Credits

- Plugin developed by Blaxk_


## Road Map:
- Add Folia Support
- Release Ports for older Versions
- Add MiniMessage Support
- Feel free to give Ideas :)

## Known Bugs
- Rectangular Based Spawn Radius (Fixed, will be rolled out soon)
>>>>>>> restore-v1.3
