# SpawnElytra 1.6 — Shared Specification

This file is the single source of truth for 1.6. The Paper/Folia plugin (`src/`) and the Fabric mod (`fabric/`)
MUST behave identically. Where this spec is silent, mirror the existing 1.5 behavior; where it is ambiguous,
the CORE agent decides and records the decision in section 13 ("Decisions log") so both platforms follow it.

Out of scope for 1.6 (do NOT build): test flight ("Probefliegen"), zone templates/presets.

---

## 1. Zones (replaces the single per-world area)

A **zone** is a named area in one world. Many zones per world, many worlds.

- Name: `^[a-z0-9_-]{1,32}$`, unique across the server (case-insensitive; stored lowercase).
- Fields:
  - `world` — Bukkit world name (`world`, `world_nether`, ...). Fabric accepts dimension ids too (same aliasing as 1.5).
  - `enabled` (bool)
  - `priority` (int, default 0) — overlap resolution, see 1.2
  - `shape`: `circle` | `rectangle` | `polygon`
    - circle: `center` = `{x, z}` or the literal string `world_spawn` (follows the live world spawn, = old "auto" mode); `radius` (double > 0)
    - rectangle: `corner1 {x, z}`, `corner2 {x, z}`
    - polygon: `points` = list of `{x, z}` (≥ 3 points, simple polygon; self-intersection rejected by the editor with a message)
  - `min_y`, `max_y` (optional numbers, doubles; absent = unlimited; the editor writes whole numbers). 1.5 rectangles were a 3D box (`y`/`y2` were checked), so migrated 1.5 rectangles get `min_y = min(y, y2)`, `max_y = max(y, y2)` (see 1.4).
  - `activation_mode`: `double_jump` | `auto` | `sneak_jump` | `f_key`
  - `boost`: `{enabled, strength, direction, max_boosts, boost_cooldown, sound}` (same semantics as 1.5)
  - `f_key`: `{launch_strength}`
  - `hunger_consumption` (optional per-zone override, same shape as global; absent = use global)
  - `fireworks.disable_in_spawn_elytra` (optional override; absent = global)
  - `boost_display` (optional override; absent = global, see 4)
- Containment: horizontal test by shape (circle = distance in XZ ≤ radius, matching 1.5 math; rectangle = inclusive block bounds like 1.5; polygon = even-odd ray casting on XZ, points on the edge count as inside), plus `min_y ≤ y ≤ max_y` when set.

### 1.1 Config layout (`config.yml`, version 1.6)

```yaml
# globals stay as in 1.5 (language, game_modes, fireworks, bedrock, messages, visualization, hunger_consumption)
boost_display: actionbar     # actionbar | bossbar | none  (new, global default)
permission_tiers: {}         # see 3
zones:
  spawn:
    world: world
    enabled: true
    priority: 0
    shape: circle
    center: world_spawn
    radius: 100
    min_y: null
    max_y: null
    activation_mode: double_jump
    boost: { enabled: true, strength: 4, direction: forward, max_boosts: 1, boost_cooldown: 0, sound: ENTITY_BAT_TAKEOFF }
    f_key: { launch_strength: 1.5 }
```
The shipped default `config.yml` contains exactly one zone `spawn` equivalent to the 1.5 default world entry, with
full explanatory comments in the same style as 1.5. The `worlds:` section is removed.

### 1.2 Overlap resolution
For a location, candidate zones = enabled zones in that world that contain the point. Winner = highest `priority`,
ties broken by name (alphabetical ascending). The editor highlights overlapping regions (section 6.3).

### 1.3 Glide session binding
A glide is bound to the zone where it was activated (boost counts, settings). Leaving the zone behaves like
leaving the area in 1.5. Moving from zone A directly into zone B mid-glide keeps the session of A until landing.
Entering a zone grants mayfly etc. exactly like entering the area in 1.5.

### 1.4 Migration 1.5 → 1.6 (automatic, on load)
- Backup first (existing BackupUtil), then convert each `worlds.<w>` entry into a zone:
  - name: `spawn` for the first migrated world (order: `world` first, then config order), others `spawn_<sanitized world name>`.
  - `spawn_area.mode: auto` (or advanced with x2/y2/z2 all 0 and area_type circular) → circle; `auto` → `center: world_spawn`,
    advanced circular → `center: {x, z}`; radius from `radius`.
  - advanced + rectangular with non-zero second corner → rectangle with corner1/corner2 from x/z and x2/z2, plus `min_y = min(y, y2)`, `max_y = max(y, y2)` as exact doubles (identical containment to the 1.5 box test). Circles get no height limits.
  - copy enabled, activation_mode, boost.*, f_key.*, per-world hunger overrides if present.
- Older versions (1.4 and before) keep working: run the existing 1.4→1.5 migration first, then 1.5→1.6.
- Migration must be idempotent and byte-identical between Paper and Fabric (Fabric already has a Bukkit-compatible YAML writer).

## 2. Player toggle
- `/se toggle` — the player turns spawn elytra off/on for themselves. Permission `spawnelytra.toggle` (default: true).
- Persisted in player data. While off: no mayfly grant, no auto activation, no boost prompts; an active glide ends safely (no fall damage for that landing).
- Placeholder `%spawnelytra_enabled%` (PAPI) / `%spawnelytra:enabled%` (Fabric).

## 3. Permission tiers
```yaml
permission_tiers:
  vip:            # permission: spawnelytra.tier.vip
    priority: 10
    max_boosts: 3          # each key optional; overrides the zone value
    strength: 5.0
    boost_cooldown: 1
    launch_strength: 2.0
```
- A player's effective tier = the tier with the highest priority whose permission `spawnelytra.tier.<name>` they have.
  No tier → zone values. Tier values override zone values field by field.
- Fabric: via fabric-permissions-api (LuckPerms). Default for tier permissions: false (not granted to ops automatically —
  matches Bukkit default for undeclared permissions? → CORE decides and logs; must be identical on both).
- `/se info` shows the player's effective tier.

## 4. Boost display
`boost_display: actionbar | bossbar | none` (global, zone override).
- actionbar: 1.5 behavior (press-to-boost / remaining messages).
- bossbar: while gliding in spawn elytra, a bossbar shows remaining boosts (progress = remaining/max) and, while on
  cooldown, a cooldown countdown (progress = time left). Hidden when the glide ends. Titles from lang keys.
- none: no boost prompts (the `messages.show_*` toggles still apply on top of actionbar mode).

## 5. Statistics
- Per player, persisted in player data: `flights` (activations), `distance` (blocks glided, 1 decimal),
  `boosts`, `glide_time` (seconds), `longest_flight` (blocks).
- `/se stats [player]` — self: permission `spawnelytra.stats` (default true); others: `spawnelytra.stats.others` (default op).
- Placeholders: `flights`, `distance`, `boosts`, `glide_time`, `longest_flight`, `zone` (current zone name or empty), `enabled`, `tier`.
- Player data writes are async and batched (dirty flag + periodic flush + flush on quit/disable), never on the tick thread.
  Folia: same rules as 1.5 fixes (entity-thread reads, async IO).

## 6. Setup / Zone editor

### 6.1 Entry points
- `/se` (no args, admin) → overview (dialog or chat, see 7).
- `/se zone create <name> [circle|rectangle|polygon]` → creates a draft zone around the player (circle r=30 centered on the player, or a 30×30 rectangle, or a 4-point square polygon) and enters the editor.
- `/se zone edit <name>`, `/se zone delete <name>` (confirmation required), `/se zone list`, `/se zone tp <name>`,
  `/se zone enable|disable <name>`, `/se zone rename <old> <new>`, `/se zone priority <name> <int>`,
  `/se zone set <name> <key> <value>` (every zone field, dotted keys e.g. `boost.strength`; used by the chat menus and scriptable).
- `/se setup` (1.5 command) → kept as an alias: with no zones → `zone create spawn`; otherwise opens the overview.
  `/se set pos1|pos2` keep working inside the editor (set rectangle corners at the player's position).
- Tab completion for all of it, including zone names and keys.

### 6.2 In-world editor (ALL versions)
Entering the editor snapshots the player's inventory (main, armor, offhand, cursor), XP is untouched, and replaces the
hotbar with tool items. The snapshot is also persisted to disk (player data) so it survives crashes; it is restored on
save, cancel, quit (on next join if needed), death (no tool items drop; real items restored on respawn), world change,
plugin disable/server stop. Tool items are unmovable, undroppable, unplaceable, can't be put in containers, and are
identified by PDC (Paper) / custom data component (Fabric). Only one editor per zone at a time.

Hotbar (slots 1–9):
| Slot | Tool | Input |
|---|---|---|
| 1 | Shape tool (per shape) | circle: right-click block = set center; rectangle: left = corner1, right = corner2; polygon: right = add point after nearest edge, left = remove nearest point |
| 2 | Radius / resize | circle: sneak+scroll = radius ±step, right-click toggles step 1 ↔ 5 (shown in actionbar); rectangle/polygon: sneak+scroll = grow/shrink outward by 1 |
| 3 | Height | sneak+scroll = move the selected bound; right-click toggles selected bound (top/bottom); left-click clears both (unlimited) |
| 4 | Move | right-click = pick up (zone follows the looked-at block, snapping to blocks), right-click again = place |
| 5 | Shape switch | right-click cycles circle → rectangle → polygon (converts using the current bounding box/approximation) |
| 6 | Undo / Redo | left = undo, right = redo (history of every geometry/settings change in this session, ≥ 50 steps) |
| 7 | Cancel | sneak + right-click = discard draft and exit |
| 8 | Settings | right-click = open zone settings (dialog or chat) — edits apply to the draft |
| 9 | Save | right-click = validate + save to config + apply live (no full reload of unrelated state) + exit |

"Scroll" = held-slot change while sneaking with the tool held: the slot change is cancelled and its direction used as ±1.

Feedback:
- Preview: glowing outline built from display entities (block or text displays), visible ONLY to the editing player,
  never persisted. Edges rendered only within ~64 blocks of the player, refreshed as they move/change. Polygon edges and
  circle (as segments) too. Height bounds shown as top/bottom rings when set.
  Paper: real entities with `setVisibleByDefault(false)` + `showEntity`, `setPersistent(false)` (Folia: spawn/remove on the owning region thread).
  Fabric: packet-only fake entities (no server entities).
- Other zones in the same world shown as a dim gray outline; overlapping parts with another zone shown red.
- Floating label (text display) at the zone center: `name · shape dims · activation mode · priority`.
- Bossbar during editing: zone name, current tool, key dims. Actionbar: context hint for the held tool.
- Every change plays a subtle click sound; invalid actions (self-intersecting polygon, <3 points, radius ≤ 0) show an error and are not applied.

### 6.3 Validation on save
Name unique, world exists, geometry valid. Overlaps are allowed (warn + show priorities).

## 7. Menus: dialogs (≥ 1.21.6) and chat fallback (< 1.21.6)

### 7.1 Which one
- Dialogs when the running server supports them: Fabric nodes with vanilla dialogs (MC ≥ 1.21.6); Paper/Folia when the
  Paper Dialog API is present at runtime (detect by class, never by version string). Otherwise chat.
- Config `menus.mode: auto | dialog | chat` (default auto) to force a mode.

### 7.2 Screens (identical content in both modes)
1. **Overview**: one row per zone (name, world, shape+dims, mode, enabled state), buttons per zone: Settings, Edit in world,
   Teleport, Enable/Disable, Delete; global buttons: New zone (asks for name + shape), Global settings, Language/Style (the 1.5 settings menu), Close.
2. **Zone settings**: name (text), world (read-only), enabled (bool), priority (number), activation mode (single option),
   boost enabled (bool), strength (0.5–10, step 0.5), direction (forward/upward), max boosts (1–20), cooldown (0–60 s),
   sound (text, validated against the sound registry, Bukkit enum names accepted), f-key launch strength (0.1–5, step 0.1),
   boost display (inherit/actionbar/bossbar/none), fireworks override (inherit/on/off), hunger override (inherit/off/activation/distance/time + its numbers).
   Buttons: Save, Cancel (+ Back to overview).
   From the overview: Save writes immediately. From the editor (slot 8): Save updates the draft only.
3. **Global settings**: language, style, boost_display, game_modes.*, fireworks, bedrock.enabled, messages.*, hunger defaults, menus.mode.
4. **Delete confirmation**: confirmation dialog / chat [Confirm] [Cancel].
5. **Stats** (`/se stats`) may be a dialog too (notice-type) — chat on < 1.21.6.

### 7.3 Dialog plumbing
- Dialog actions use custom click actions with ids in the `spawnelytra:` namespace and a payload; validate the sender has
  permission and the referenced zone still exists; ignore stale/forged payloads.
- Chat fallback: clickable components (`run_command` for buttons, `suggest_command` for text input like rename);
  number fields as `[-] value [+]` with ±small/±large steps; option fields cycle on click. Every setting reachable.

## 8. Small fixes (behavior changes vs 1.5)
1. `/se info`: permission `spawnelytra.info` (default true) — shows the info for the zone the player is in (or the
   overview list for console), as the README already claims.
2. `messages.show_creative_disabled: true` → actionbar message when a player in creative is inside a zone and
   `disable_in_creative` is true (once per entering, not every tick).
3. `game_modes.disable_in_creative: false` → creative players can use spawn elytra; with double_jump the double-tap
   inside a zone starts gliding instead of creative flight (outside zones creative flight is untouched; gliding ends and
   normal creative flight is restored on landing/leaving).
4. Player data IO async (section 5).

## 9. Messages / lang
- Every new user-facing string is a lang key in ALL FIVE files (en, de, es, fr, pl) with real translations (not English copies),
  MiniMessage, same color palette as 1.5, works with `small_caps` style.
- Key prefixes: `zone_*`, `editor_*`, `menu_*`, `tier_*`, `stats_*`, `toggle_*`, `bossbar_*`, `info_*` (extend).
- The LanguageUpdater must add missing keys to existing user lang files (as in 1.5).
- Help output lists the new commands.

## 10. Commands & permissions summary
| Command | Permission | Default |
|---|---|---|
| `/se`, `/se zone ...`, `/se setup`, `/se set`, reload, update, visualize, settings, options | `spawnelytra.admin` | op |
| `/se info` | `spawnelytra.info` | true |
| `/se toggle` | `spawnelytra.toggle` | true |
| `/se stats` | `spawnelytra.stats` | true |
| `/se stats <player>` | `spawnelytra.stats.others` | op |
| use / boost | `spawnelytra.use`, `spawnelytra.useboost` | true |
| tiers | `spawnelytra.tier.<name>` | false |
Non-admins running `/se` with no args get the help listing only what they may use.
`/se visualize [zone] [seconds]` — no zone = all zones in the current world.

## 11. Versioning
Version 1.6 everywhere (root `build.gradle.kts`, Fabric `stonecutter.properties.toml` `mod.version`, config header
`Plugin Version: 1.6`, info output). `api-version` unchanged.

## 12. Architecture: shared core
- New directory `common/src/main/java/com/blaxk/spawnelytra/common/` — pure Java 21, NO platform dependencies
  (no Bukkit, no Minecraft, no Adventure). Compiled into BOTH builds as an extra source dir
  (root `build.gradle.kts`: `sourceSets.main.java.srcDir("common/src/main/java")`; Fabric: `srcDir(rootProject.file("../common/src/main/java"))`).
  Unit tests in `common/src/test/java` run by the root build (JUnit 5).
- Contents (CORE agent): zone model + geometry (containment, polygon validation, bounding boxes, overlap detection,
  shape conversion, circle/polygon edge sampling for previews), zone registry + overlap resolution, config schema
  read/write against a small `ConfigView` interface (both platforms adapt their YAML), 1.5→1.6 migration on `ConfigView`,
  tier resolution, stats model + accumulation math, editor draft + undo/redo history, chat-menu/dialog *screen models*
  (platform-neutral description of rows, fields, buttons, actions — each platform renders them), zone-name validation,
  `/se zone set` key table (key → type, range, validator).
- Platforms own: events/mixins, schedulers, rendering of screens (dialog API / packets / chat components), display
  entities, inventory snapshot, permissions, commands wiring, IO.

## 13. Decisions log
(CORE agent appends decisions here; platform agents read it before implementing each area.)

### Geometry / zones (CORE)
- **Circle containment is horizontal**: `sqrt(dx²+dz²) <= radius` (inclusive). Note: 1.5 actually used the 3D distance to the spawn location (incl. Y); 1.6 follows §1 (XZ cylinder, height via `min_y`/`max_y`), as the 1.6 schema has no center Y.
- **Rectangle containment is 1.5-identical**: `min <= x <= max && min <= z <= max` on raw doubles, inclusive.
- **1.5 rectangle `y`/`y2`** (revised): 1.5 checked them (3D box). Migrated rectangles keep that box exactly: `min_y = min(y, y2)`, `max_y = max(y, y2)` stored as doubles (no rounding), so containment is identical to 1.5 for every existing config, including non-integer setup-wizard positions. If `spawn_area.y` is missing (1.5 then used the live world-spawn Y), height stays unlimited. Circles stay the intentional 1.6 XZ cylinder with no height limit.
- Height bounds are doubles (`Zone.minHeight()/maxHeight()`); test: `min_y <= y <= max_y` on the player's raw feet Y (inclusive), each bound optional. `/se zone set min_y|max_y` accepts decimals; editor scrolling steps by 1.
- Editor clicks on blocks store **block centers** (`bx+0.5, bz+0.5`) for circle centers, rectangle corners and polygon points; `/se set pos1|pos2` stores the raw player X/Z (1.5 behaviour). `/se zone create` centres the draft on the player's block center.
- Polygon: max **64** points; invalid = <3 points, duplicate points, zero area, crossing/touching non-adjacent edges, adjacent edges folding back. Editor add-point inserts into the nearest edge; remove-point removes the nearest vertex (refused below 3).
- Polygon grow/shrink (slot 2): every vertex moves 1 block away from / towards the vertex centroid (refused when a vertex would come within 0.5 blocks of it). Rectangle grow/shrink: every side ±1 (refused below 1×1).
- Shape conversion (slot 5 and `zone set shape`): via the bounding box. →rectangle = bbox; →circle = bbox center, radius = half the larger bbox side (min 1); rectangle→polygon = 4 corners; circle→polygon = 8 points on the circle. Coordinates rounded to 2 decimals. A world-spawn circle becomes fixed-center when moved/converted.
- Radius limits 1…100000; `/se zone create` sizes: circle r=30, rectangle 30×30, polygon 30×30 square.
- Overlap check (editor red + save warning): same world, height ranges intersect, shapes share at least one point (touching counts).
- Zones in config that fail validation (bad name, duplicate case-insensitive name, no world, invalid geometry) are skipped with a `zone_load_*` warning. Config keys are matched case-insensitively and normalised to lowercase.
- Numbers written by core are canonical: integral → int (`4`), else the exact double (never rounded, for migration parity); display/command formatting rounds to 4 decimals. `boost.strength` is now read as a double (1.5 read it with getInt). Platforms should pass dialog float inputs as `Float.toString(v)` to avoid float noise.

### Migration (CORE)
- Runs after the platform's 1.4→1.5 updater, only when `worlds` exists and `zones` does not. Order: key `world` first, else `minecraft:overworld`, then config order. First → `spawn`, others → `spawn_<sanitized world>` (lowercase, invalid chars → `_`, max 32 chars, `_2`, `_3`… on collision).
- Shape rule mirrors 1.5 exactly: rectangle (with `min_y`/`max_y` from y/y2) iff `mode: advanced` && `area_type: rectangular` && not (x2 == y2 == z2 == 0); otherwise circle (`auto` → `center: world_spawn`; advanced → `center: {x, z}`; advanced without x/z → world_spawn). Any mode other than `advanced` counts as auto (1.5).
- Copied: enabled, activation_mode, boost.*, f_key.launch_strength, per-world `hunger_consumption` (verbatim) and per-world `fireworks.disable_in_spawn_elytra` if present. Priority 0. Shipped comments are applied to the migrated zones.
- Adds missing globals `boost_display: actionbar`, `menus.mode: auto`, `permission_tiers: {}` (with comments) on every load (also for already-1.6 configs) → `changed=true` only if something was added. If both `zones` and `worlds` exist, `worlds` is left untouched and a warning is logged.
- Backup before save; header label → `# Plugin Version: 1.6`. The comment "can be overridden per-world" on `hunger_consumption` is updated to "per-zone".

### Tiers / stats / toggle (CORE)
- **Tier permission default: false for everyone, including OPs**, on both platforms. Paper must register `spawnelytra.tier.<name>` with `PermissionDefault.FALSE` (undeclared Bukkit perms default to OP); Fabric checks with `Permissions.check(src, node, false)` (no op-level fallback).
- Tier ties (same priority) → name ascending. Tier names follow the zone-name pattern; invalid ones are skipped (`tier_load_invalid`). Tier values are not range-clamped except 1.5's `max_boosts >= 1`, `cooldown >= 0`.
- Stats: `distance` = 3D path length while gliding, steps > 50 blocks ignored (teleports). `flights` counted at activation, `boosts` per boost, `glide_time` and `longest_flight` on landing. Stored under `stats:` in the player data file (`distance`, `glide_time`, `longest_flight` rounded to 1 decimal). Placeholders: distance/longest with 1 decimal, `glide_time` in whole seconds; `/se stats` shows glide time as `1h 2m 3s`.
- Toggle state key in player data: `enabled` (default true). Editor inventory snapshot: `editor_snapshot` section (platform format).

### Commands / menus (CORE)
- Additional commands needed by the menus (chat fallback): `/se zone settings <zone>`, `/se zone delete <zone> confirm`, `/se global` (global settings screen), `/se global set <key> <value>` (keys = `GlobalKeyTable`). `/se settings` stays the 1.5 language/style menu.
- `/se zone set` value syntax: booleans `true|false|on|off|toggle`; options `<value>|next`; optional values `none|inherit`; points `x,z`; polygon `x,z;x,z;x,z`; `center world_spawn|x,z`; `name <new>` renames. Ranges: priority −1000…1000, radius 1…100000, strength 0.5…10, max_boosts 1…20, cooldown 0…60, launch_strength 0.1…5, min/max_y −2048…4096, hunger costs 0…20, blocks_per_point 1…10000, seconds_per_point 1…3600.
- Setting a `hunger_consumption.*` number on a zone that inherits creates a per-zone override seeded from the global hunger settings. `hunger_consumption` option values: `inherit|off|activation|distance|time`. Fireworks override: `inherit|true|false` (true = fireworks blocked; aliases on/off).
- `/se zone set <zone> …` while the sender has that zone open in the editor → applies to the draft (`zone_set_success`, undoable). If another player edits it → refused with `zone_error_being_edited`.
- Chat menus apply every field change immediately (via the commands above); dialogs collect inputs and submit with Save (`Screens.applyZoneForm/applyGlobalForm`). Zone settings screen does not show geometry or height (editor only); `world` is read-only.
- Editor history depth 100 (≥ 50). Radius step toggle and height-bound toggle are not undo steps. Height tool: scrolling an unset bound first sets it to the player's block Y. A move in progress must be placed before other edits; undo/redo cancels it.
- Editor preview: segments max 4 blocks before classification, range 64, cap 256 segments (nearest first); outline drawn at the player's block Y clamped into the height bounds; label 2 blocks above.
- Lang: `lang-version` bumped to `1.6` in all five files. Lore lines are separated by `<br>` (platforms split on it). Argument names in help/usage use `‹name›` (no `<…>`, which MiniMessage would parse as tags). The creative message key is `creative_mode_elytra_disabled`.
