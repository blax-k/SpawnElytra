<div align="center">
  
![Spawn Elytra](https://cdn.modrinth.com/data/cached_images/5df9a92fc92fc69760897e41dce2fc254ce90c10.png)

![Downloads](https://img.shields.io/modrinth/dt/Egw2R8Fj?logo=modrinth&style=flat&label=Downloads&color=38B541)
![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/blaxkkkk/spawnelytra?label=CodeFactor&style=flat&color=38B541)
![https://img.shields.io/github/license/blaxkkkk/SpawnElytra](https://img.shields.io/github/license/blaxkkkk/SpawnElytra?&color=38B541&label=License&style=flat)
[![Docs](https://img.shields.io/badge/Docs-GitBook-blue?style=flat&logo=gitbook&color=38B541)](https://blaxk.gitbook.io/spawnelytra)
</div>

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
2. Place the .jar file in your server's plugins folder
3. Restart your server
4. Configure the plugin in the generated config.yml file

### Important:
The **default language** of this plugin **is German**, but you can change it to English by changing `language: de` to `language: en` in config.yml!

## Configuration

<details>
<summary>Default Config</summary>

```yaml
# Spawn Elytra Plugin by Blaxk_
# Plugin Version: 1.3
# Modrinth: https://modrinth.com/plugin/spawn-elytra

# Activation mode for elytra:
# double_jump: Player needs to double-press space to activate elytra
# auto: Automatically activates elytra when player has air below and is in spawn area
# sneak_jump: Player needs to sneak while jumping to activate elytra
# f_key: Player needs to press F (swap hands) to activate elytra, this also boosts a player upwards on activation.
activation_mode: double_jump

# The radius around spawn where elytra boosting is enabled
# (only used in auto mode or if x2, y2, z2 are all set to 0)
radius: 100

# The strength of the boost when pressing the boost key
strength: 2

# Enable or disable the boost feature completely
boost_enabled: true

# Boost direction: 'forward' or 'upward'
# forward: Boosts player in the direction they are looking
# upward: Boosts player straight up
boost_direction: forward

# The world where the spawn elytra feature is enabled
world: world

# Disable fireworks when using spawn elytra (players can still use fireworks if they have a real elytra equipped)
disable_fireworks_in_spawn_elytra: false

# Custom spawn coordinates and dimensions
spawn:
  # Mode options: 'auto' or 'advanced'
  # auto: Uses the world spawn point
  # advanced: Uses custom spawn coordinates defined below
  mode: advanced

  # First point of the elytra area
  x: 0
  y: 64
  z: 0

  # Second point of the elytra area
  # Setting all x2/y2/z2 to 0 will use the radius-based circular area instead
  # Example: Using x=0, y=64, z=0 and x2=100, y2=128, z2=100 creates a rectangular area
  # between those two coordinate points
  x2: 0 # Second X coordinate
  y2: 0 # Second Y coordinate
  z2: 0 # Second Z coordinate

# Boost sound effect - can be any sound from https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html
# Examples: ENTITY_BAT_TAKEOFF, ENTITY_FIREWORK_ROCKET_BLAST, ITEM_ELYTRA_FLYING
boost_sound: ENTITY_BAT_TAKEOFF

# Available languages: en, de, es, fr, hi, zh, ar
language: de

# Automatically disable elytra when player enters creative mode (This prevents buggy flying in Creative)
disable_in_creative: true

# If you don't want to disable elytra in adventure mode, set this to false
disable_in_adventure: false

# Launch strength for F_key activation mode (Only used when activation_mode: f_key) (1.5 = ~14-15 blocks upward)
f_key_launch_strength: 1.5

# Message settings
messages:
  # Set to false to disable the "press to boost" message
  show_press_to_boost: true
  # Set to false to disable the "boost activated" message
  show_boost_activated: true
  # Set to true to use custom messages below instead of language file messages
  use_custom_messages: false

  # Custom messages
  # {key} is used represent the offhand key (F by default)
  # Due to limitations from minecraft, you cant enter any key you want.
  # Legacy color codes (&a, &e, etc.) are supported
  press_to_boost: '&aPress &a&l{key} &ato boost yourself.'
  boost_activated: '&aBoost activated!'
```
</details>

## Commands and Permissions

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
