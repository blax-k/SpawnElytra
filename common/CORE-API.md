# SpawnElytra 1.6 shared core (`common/`)

Pure Java 21, no platform deps. Package root: `com.blaxk.spawnelytra.common`. Compiled into both builds as an extra
source dir (root: `sourceSets.main.java.srcDir("common/src/main/java")`, Fabric: `srcDir(rootProject.file("../common/src/main/java"))`).
Unit tests: `common/src/test/java` (root build, JUnit 5). Decisions: `docs/spec-1.6.md` §13.

## API changes
- **2026-10-10 (1) Zone height bounds are doubles.** Record components `minY/maxY (Integer)` → `minHeight/maxHeight (Double)`
  (YAML keys unchanged: `min_y`, `max_y`). New withers `withMinHeight(Double)`, `withMaxHeight(Double)`.
  Kept for compatibility: `withMinY(Integer)`, `withMaxY(Integer)` and **deprecated** block views `Integer minY()` = `ceil(min_y)`,
  `Integer maxY()` = `floor(max_y)` (an integer Y is inside the bounds iff it is inside the block view) — switch to
  `minHeight()/maxHeight()` for display and exact tests. New `KeyType.OPTIONAL_DOUBLE` (used by `min_y`/`max_y` in
  `ZoneKeyTable`; decimals accepted). Reason: migrated 1.5 rectangles keep their 3D box (`min(y,y2)..max(y,y2)`) with
  exact 1.5 containment (spec §1.4, §13).
- **2026-10-10 (2) `ConfigNumbers.canonical` no longer rounds** (integral → Integer, else the exact Double); `format()` still
  rounds to 4 decimals for display/commands. Pass dialog float inputs as `Float.toString(v)`.

## Packages

| Package | Contents |
|---|---|
| `common` | `SpawnElytraCore` — version `1.6`, namespace, permission node constants, player-data key names |
| `config` | `ConfigView` (YAML adapter interface), `MapConfigView` (in-memory impl), `ConfigNumbers` (canonical numbers), `GlobalSettings` (globals reader), `MenusMode` |
| `geom` | `Vec2`, `Bounds2D`, `Geometry` (point-in-polygon, segment tests, polygon validation) |
| `zone` | `Zone` (immutable record + withers), `Shape` sealed: `CircleShape`/`RectangleShape`/`PolygonShape`, `ShapeType`, `ShapeOps` (create/convert/intersect), `ZoneRegistry` (snapshot + overlap resolution), `ZoneOverlaps`, `ZoneCodec` (read/write `zones:`), `ZoneComments`, `ZoneNames`, enums `ActivationMode`, `BoostDirection`, `BoostDisplay`, `HungerMode`, records `BoostSettings`, `HungerSettings` |
| `migration` | `ConfigMigrator` (1.5 → 1.6 on `ConfigView`) |
| `keys` | `ZoneKeyTable` (`/se zone set` keys), `GlobalKeyTable` (`/se global set` keys), `KeySpec`, `KeyType`, `KeyParsers`, `KeyContext` (platform lookups), `SetResult<T>` |
| `tier` | `PermissionTier`, `TierResolver` (read + resolve), `EffectiveSettings` (zone + globals + tier → what a glide uses) |
| `stats` | `PlayerStats` (immutable, read/write `stats:`), `FlightTracker` (per glide), `StatsFormat` (placeholders/`/se stats`) |
| `editor` | `ZoneDraft` (draft + undo/redo + tool state + save check + HUD texts), `EditHistory<T>`, `EditorTool` (hotbar slots, lang keys), `EditResult`, `Preview` (segments + label for display entities) |
| `screen` | `Screen`, `Element` (`Text`/`Heading`/`Row`), `Field` (`Bool`/`Number`/`TextInput`/`Choice`/`ReadOnly`), `Button`, `Action`, `ActionIds`, `OptionLabels`, `Screens` (factories + form appliers) |
| `text` | `Msg` — lang key + placeholders (String = unparsed, nested `Msg` = render then insert as component; `key == null` = literal in arg `text`) |

## 1. ConfigView adapter (both platforms)

Implement 6 methods: `keys()`, `get(path)`, `set(path, value)`, `createSection(path)`, `comments(path)`, `setComments(path, list)`.
Typed getters are default methods with Bukkit semantics. Contract (see javadoc):
- **Never consult defaults.** Bukkit: `section.get(path, null)`, `getKeys(false)`; wrap returned `ConfigurationSection` as a new adapter.
- `set(path, null)` removes. Core passes `String/Integer/Long/Double/Boolean/List<Map<String,Object>>`; never a `Map` (uses `createSection`).
- Comments: Bukkit `getComments/setComments` 1:1 (`null` entry = blank line). Fabric `ConfigSection` has the same methods.
```java
// Paper
record BukkitView(ConfigurationSection s) implements ConfigView {
  public Set<String> keys() { return s.getKeys(false); }
  public Object get(String p) { Object v = s.get(p, null); return v instanceof ConfigurationSection c ? new BukkitView(c) : v; }
  public void set(String p, Object v) { s.set(p, v); }
  public ConfigView createSection(String p) { return new BukkitView(s.createSection(p)); }
  public List<String> comments(String p) { return s.getComments(p); }
  public void setComments(String p, List<String> c) { s.setComments(p, c); }
}
// Fabric: identical with ConfigSection (get(p, null), getConfigurationSection semantics not needed).
```

## 2. Config load flow
1. Existing 1.4→1.5 `ConfigUpdater` first. **Change needed there:** treat a config containing `zones` as MODERN; never create `worlds` when `zones` exists; per-world fill-ins only for `worlds`; `CURRENT_CONFIG_VERSION = "1.6"`.
2. Load the file fresh (no defaults) → adapter → `ConfigMigrator.migrate(view)`. If `result.changed()`: `BackupUtil.backupFile(..., "config/config.yml")` **before** saving, save, refresh `# Plugin Version: 1.6` label. Log `result.log()` (English, lang may not be loaded yet). Idempotent.
3. Runtime read (from the plugin config; defaults are harmless here):
   - `GlobalSettings g = GlobalSettings.read(view)` (incl. hunger + tiers),
   - `ZoneCodec.ReadResult r = ZoneCodec.readAll(view, g.hunger())` → log `r.warnings()` (Msg, `zone_load_*` keys),
   - `ZoneRegistry reg = new ZoneRegistry(r.zones(), worldKey)` (Paper: identity; Fabric: alias canonicalizer). Publish via `volatile`.
4. Writes: `ZoneCodec.write(root, zone, previousNameOrNull, false)`, `ZoneCodec.remove(root, name)`; then save async and rebuild the registry. Use `defaultComments=true` only if you want the shipped comments on a new zone.

## 3. Hot path (per move/tick)
```java
Optional<Zone> z = reg.resolve(world, x, y, z, w -> spawnXZ(w));   // highest priority, ties by name
EffectiveSettings eff = EffectiveSettings.resolve(zone, globals, tier); // cache per player per glide
```
`Zone.contains(x,y,z,spawn)`: circle `sqrt(dx²+dz²) <= r` (XZ), rectangle 1.5 inclusive raw doubles, polygon even-odd with edge = inside, plus `min_y <= y <= max_y` if set.
Glide binding (§1.3) is platform state: store the zone name + `EffectiveSettings` at activation.

## 4. Tiers
`TierResolver.resolve(globals.tiers(), node -> hasPerm(player, node))` → `Optional<PermissionTier>`. Node `spawnelytra.tier.<name>`, **default false for everyone incl. ops** (Paper: register each node with `PermissionDefault.FALSE` on load/reload; Fabric: `Permissions.check(src, node, false)`).

## 5. Stats
`PlayerStats` per player (immutable; replace + mark dirty). Activation: `stats = stats.withFlightStarted()`; boost: `withBoost()`; glide: `FlightTracker t = new FlightTracker(now, x,y,z)`, `t.move(x,y,z)` each tick/move, on end `stats = t.complete(stats, now)`. Persist: `stats.write(view.createSection("stats"))`, read `PlayerStats.read(view.section("stats"))`. Placeholders: `StatsFormat.placeholder(stats, "flights"|"distance"|"boosts"|"glide_time"|"longest_flight")`.

## 6. `/se zone set` key table
`ZoneKeyTable.keys()` (completion), `ZoneKeyTable.keysFor(zone)` (shape-aware), `ZoneKeyTable.spec(key).suggestions()` (value completion),
`SetResult<Zone> r = ZoneKeyTable.apply(zone, key, value, keyContext)` → on success save (or draft, see below), else send `r.error()`.
Keys: `name, world, enabled, priority, shape, center, radius, corner1, corner2, points, min_y, max_y, activation_mode,
boost.enabled, boost.strength, boost.direction, boost.max_boosts, boost.boost_cooldown, boost.sound, f_key.launch_strength,
boost_display, fireworks.disable_in_spawn_elytra, hunger_consumption, hunger_consumption.minimum_food_level,
hunger_consumption.activation.hunger_cost, hunger_consumption.distance.blocks_per_point, hunger_consumption.distance.hunger_cost,
hunger_consumption.time.seconds_per_point, hunger_consumption.time.hunger_cost`.
Special values: bool `toggle`, option `next`, optional `none`/`inherit`. `name` = rename (check uniqueness via `KeyContext.zoneNameTaken`).
`GlobalKeyTable.apply(rootView, key, value)` writes directly into the config view (`/se global set`); then save + reload/apply.

## 7. Editor
```java
ZoneDraft d = new ZoneDraft(zone /* Zone.createDefault(name, world, ShapeOps.createAround(type, Vec2.blockCenter(bx,bz))) */, originalNameOrNull, spawnXZ);
EditResult r = d.setCircleCenter(Vec2.blockCenter(bx,bz)); // or setRectangleCorner(1|2, p), addPolygonPoint(p), removePolygonPoint(p),
   // toggleRadiusStep(), scrollResize(±1), toggleHeightBound(), scrollHeight(±1, playerBlockY), clearHeight(),
   // pickUp(p) / moveTo(p) (bool: refresh) / place() / cancelMove(), cycleShape(), undo(), redo(), applyKey(k, v, ctx), apply(zone)
if (r.success()) { click sound; refresh preview; actionbar r.feedback() if non-null } else { actionbar r.error() }
ZoneDraft.SaveCheck c = d.checkSave(registry, ctx); // errors block, warnings = overlaps
Preview p = Preview.build(d.zone(), registry.inWorld(world) minus original, spawn, px, py, pz, Preview.Options.DEFAULT);
// p.segments(): Segment(from, to, y, Style) -> stretched block display; p.label() -> text display
// bossbar: d.bossbarTitle(heldTool); actionbar hint: d.actionbarHint(heldTool)
```
`EditorTool.bySlot(slot)`, `tool.nameKey(shapeType)`, `loreKey`, `hintKey`, `id()` (store in PDC / custom data `spawnelytra:editor_tool`).
Click positions: pass block centers (`Vec2.blockCenter`); `/se set pos1|pos2` pass the raw player position.
`/se zone set` while the sender edits that zone → `draft.applyKey(...)`; another player editing it → refuse (`zone_error_being_edited`).

## 8. Screens → render
`Screens.overview(registry.all())`, `Screens.zoneSettings(zone, Screens.Context.OVERVIEW|EDITOR, keyContext)`,
`Screens.globalSettings(rootView)`, `Screens.newZone()`, `Screens.deleteConfirm(zone)`, `Screens.stats(playerName, stats)`.
Render: title, then `body` (Text, Heading, Row(text + buttons), Field), then `buttons(dialogMode)`; `exit()` = dialog escape.
- Dialog: fields → inputs keyed by `field.id()`; button click = custom action `action.id()` with `action.payload()` (+ all inputs for `ActionIds.isFormSubmit(id)`).
- Chat: `field.label(): value` + `field.chatControls()` (each a Button with an Action); button = `run_command action.command()` (or `suggest_command` if `action.suggest()`).
- Handlers: `ActionIds.isKnown(id)`, check `spawnelytra.admin` (`ActionIds.requiresAdmin`), check zone exists; submit appliers:
  `Screens.applyZoneForm(zone, inputs, ctx)` → `SetResult<Zone>` (context `overview` → save; `editor` → `draft.apply(zone)`),
  `Screens.applyGlobalForm(rootView, inputs)` → errors (then save + reload).
Chat commands used by actions (platforms must implement): `/se`, `/se zone settings <zone>`, `/se zone edit|tp|enable|disable <zone>`,
`/se zone delete <zone> [confirm]`, `/se zone create <name> [shape]`, `/se zone set <zone> <key> <value>`, `/se global`,
`/se global set <key> <value>`, `/se settings`, `/se stats [player]`.

## 9. Lang
All keys in `src/main/resources/lang/*.yml`. `Msg` keys produced by the core are all present there (`zone_*`, `editor_*`,
`menu_*`, `tier_*`, `stats_*`, `toggle_*`, `bossbar_*`, `info_*`, `migration_*`, `help_*`).

## 10. Notes for platform agents (things outside `common/` that must change)
- `plugin.yml`: the `spawnelytra` command currently has `permission: spawnelytra.admin` → non-admins could not run `/se toggle|stats|info`. Remove it and check per subcommand (§10); declare `spawnelytra.info|toggle|stats` (true), `spawnelytra.stats.others` (op).
- `LanguageUpdater`: all five lang files now have `lang-version: 1.6` and 308 new keys. Bump `REQUIRED_LANG_VERSION` to `1.6` and (spec §9) add missing keys to user files instead of overwriting.
- `ConfigUpdater`: see §2 (treat `zones` as modern, never re-create `worlds`, version 1.6).
- Lore keys (`editor_tool_*_lore`) contain `<br>` between lines: split on `<br>` and deserialize each line.
- Help/usage strings use `‹name›` for arguments (plain text), never `<name>`.
- Fabric adapter: same as the Bukkit one with `ConfigSection` (`get(p, null)`, `getKeys(false)`, `createSection`, `getComments/setComments`). The reference Bukkit adapter is exercised in `common/src/test/java/.../BukkitAdapterTest.java` (migration on real Bukkit YAML == in-memory result, idempotent byte-for-byte).
- Number values in YAML: read with `getDouble` (strength may be `4` or `4.5`).
