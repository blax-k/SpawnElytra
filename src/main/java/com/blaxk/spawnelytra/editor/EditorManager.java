/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.editor;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.common.editor.EditResult;
import com.blaxk.spawnelytra.common.editor.EditorTool;
import com.blaxk.spawnelytra.common.editor.Preview;
import com.blaxk.spawnelytra.common.editor.ZoneDraft;
import com.blaxk.spawnelytra.common.geom.Vec2;
import com.blaxk.spawnelytra.common.keys.SetResult;
import com.blaxk.spawnelytra.common.screen.Screens;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.common.zone.ShapeType;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.Texts;
import com.blaxk.spawnelytra.zone.ZoneService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-world zone editor (spec §6.2): tool hotbar, input capture, display-entity preview, HUD and inventory safety.
 * <p>
 * Threading: a session is only touched on its player's thread (events of that player, the player's entity
 * scheduler, or callers hopping there via {@link SchedulerUtil#runForEntity}). The maps are concurrent because
 * commands/console and other regions query them. The inventory snapshot is persisted asynchronously so it survives
 * a crash, and is restored on save, cancel, quit (or next join), death (on respawn), world change and disable.
 */
public final class EditorManager implements Listener {
    private static final long DEBOUNCE_NANOS = 120_000_000L;

    private final Main plugin;
    private final NamespacedKey toolKey;
    private final NamespacedKey previewKey;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> zoneLocks = new ConcurrentHashMap<>();
    /** Snapshots waiting for a respawn (death) or join (crash / missed restore). */
    private final Map<UUID, InventorySnapshot> pendingRestore = new ConcurrentHashMap<>();

    public EditorManager(final Main plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "editor_tool");
        this.previewKey = new NamespacedKey(plugin, "editor_preview");
    }

    // ---- queries ---------------------------------------------------------------------------------------------------

    public EditorSession session(final Player player) {
        return this.sessions.get(player.getUniqueId());
    }

    public boolean isEditing(final Player player) {
        return this.sessions.containsKey(player.getUniqueId());
    }

    /** The player editing the zone (by saved name or draft name), or {@code null}. */
    public UUID editorOf(final String zoneName) {
        if (zoneName == null) {
            return null;
        }
        final String key = zoneName.toLowerCase(Locale.ROOT);
        final UUID locked = this.zoneLocks.get(key);
        if (locked != null) {
            return locked;
        }
        for (final EditorSession s : this.sessions.values()) {
            if (s.draft.zone().name().equalsIgnoreCase(zoneName)) {
                return s.playerId;
            }
        }
        return null;
    }

    /** {@code true} if the session edits the zone with that name (saved or draft name). */
    public static boolean edits(final EditorSession s, final String zoneName) {
        return s != null && zoneName != null && (zoneName.equalsIgnoreCase(s.draft.zone().name())
                || zoneName.equalsIgnoreCase(s.lockName) || zoneName.equalsIgnoreCase(s.draft.originalName()));
    }

    public static String playerName(final UUID id) {
        final Player p = id == null ? null : Bukkit.getPlayer(id);
        return p != null ? p.getName() : String.valueOf(id);
    }

    // ---- start / end ---------------------------------------------------------------------------------------------

    /**
     * Enters the editor for a zone (must run on the player's thread; the player must be in the zone's world).
     *
     * @param originalName saved name, or {@code null} for a new zone
     */
    public boolean start(final Player player, final Zone zone, final String originalName) {
        if (this.sessions.containsKey(player.getUniqueId())) {
            Texts.send(player, Msg.of("zone_error_already_editing"));
            return false;
        }
        final String lock = (originalName != null ? originalName : zone.name()).toLowerCase(Locale.ROOT);
        final UUID holder = this.zoneLocks.putIfAbsent(lock, player.getUniqueId());
        if (holder != null && !holder.equals(player.getUniqueId())) {
            Texts.send(player, Msg.of("zone_error_being_edited", "zone", zone.name(), "player", playerName(holder)));
            return false;
        }

        final InventorySnapshot snapshot = InventorySnapshot.capture(player);
        this.plugin.getPlayerDataManager().saveSnapshotAsync(player.getUniqueId(), snapshot.serialize());

        final ZoneService zones = this.plugin.getZoneService();
        final ZoneDraft draft = new ZoneDraft(zone, originalName, zones.worldSpawn(zone.world()));
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        final EditorSession session = new EditorSession(player.getUniqueId(), draft, snapshot,
                new PreviewRenderer(this.plugin, player, this.previewKey), bar, lock);
        this.sessions.put(player.getUniqueId(), session);

        final PlayerInventory inv = player.getInventory();
        player.setItemOnCursor(null);
        inv.clear();
        this.giveTools(player, session);
        player.updateInventory();

        MessageUtil.showBossBar(player, bar);
        this.updateHud(player, session, true);
        session.tickTask = SchedulerUtil.runAtEntityTimer(this.plugin, player, 1L, 4L, () -> this.tick(player, session));
        Texts.send(player, Msg.of("editor_entered", "zone", zone.name()));
        return true;
    }

    /** Ends the session and gives the inventory back. Must run on the player's thread. */
    private void end(final Player player, final EditorSession session, final boolean restoreNow) {
        if (!this.sessions.remove(player.getUniqueId(), session)) {
            return;
        }
        this.zoneLocks.remove(session.lockName, player.getUniqueId());
        if (session.tickTask != null) {
            session.tickTask.cancel();
        }
        session.preview.clear();
        MessageUtil.hideBossBar(player, session.bossBar);
        if (restoreNow) {
            this.restore(player, session.snapshot);
        } else {
            this.pendingRestore.put(player.getUniqueId(), session.snapshot);
        }
    }

    /** Restores a snapshot, keeps any non-tool item the player got meanwhile, and deletes the persisted copy. */
    private void restore(final Player player, final InventorySnapshot snapshot) {
        final List<ItemStack> extra = new ArrayList<>();
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && !this.isTool(item)) {
                extra.add(item.clone());
            }
        }
        final ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && !this.isTool(cursor)) {
            extra.add(cursor.clone());
        }
        snapshot.restore(player);
        for (final ItemStack item : extra) {
            for (final ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        this.pendingRestore.remove(player.getUniqueId());
        this.plugin.getPlayerDataManager().deleteSnapshotAsync(player.getUniqueId());
    }

    public void cancel(final Player player, final boolean message) {
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            if (message) {
                Texts.send(player, Msg.of("editor_not_in_editor"));
            }
            return;
        }
        final String name = session.draft.zone().name();
        this.end(player, session, true);
        if (message) {
            Texts.send(player, Msg.of("editor_cancelled", "zone", name));
        }
    }

    public void save(final Player player) {
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            Texts.send(player, Msg.of("editor_not_in_editor"));
            return;
        }
        final ZoneService zones = this.plugin.getZoneService();
        final ZoneDraft.SaveCheck check = session.draft.checkSave(zones.registry(), zones.keyContext());
        if (!check.ok()) {
            Texts.send(player, Msg.of("editor_save_failed"));
            for (final Msg error : check.errors()) {
                Texts.send(player, error);
            }
            this.errorSound(player);
            return;
        }
        for (final Msg warning : check.warnings()) {
            Texts.send(player, warning);
        }
        final Zone zone = session.draft.zone();
        zones.saveZone(zone, session.draft.originalName());
        this.end(player, session, true);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        Texts.send(player, Msg.of("editor_saved", "zone", zone.name()));
    }

    /**
     * Ends every session. On disable this runs inline (Paper main thread / Folia shutdown thread own all players);
     * players that cannot be reached keep their persisted snapshot, which is restored on their next join.
     */
    public void stopAll(final boolean disabling) {
        for (final Map.Entry<UUID, EditorSession> e : new ArrayList<>(this.sessions.entrySet())) {
            final Player player = Bukkit.getPlayer(e.getKey());
            if (player == null) {
                this.sessions.remove(e.getKey());
                this.zoneLocks.remove(e.getValue().lockName);
                e.getValue().preview.clear();
                continue;
            }
            final Runnable stop = () -> {
                final String name = e.getValue().draft.zone().name();
                this.end(player, e.getValue(), true);
                if (!disabling) {
                    Texts.send(player, Msg.of("editor_cancelled", "zone", name));
                }
            };
            if (disabling) {
                if (SchedulerUtil.isOwnedByCurrentThread(player)) {
                    try {
                        stop.run();
                    } catch (final Throwable t) {
                        this.plugin.getLogger().warning("Could not restore the editor inventory of " + player.getName()
                                + " (" + t.getMessage() + "); it will be restored on the next join.");
                    }
                }
            } else {
                SchedulerUtil.runForEntity(this.plugin, player, stop);
            }
        }
    }

    // ---- draft updates from commands / menus ------------------------------------------------------------------

    /** {@code /se zone set} on the zone the player edits. Must run on the player's thread. */
    public EditResult applyKey(final Player player, final String key, final String value) {
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return EditResult.fail("editor_not_in_editor");
        }
        final EditResult r = session.draft.applyKey(key, value, this.plugin.getZoneService().keyContext());
        this.afterEdit(player, session, r, false);
        return r;
    }

    /** Zone settings dialog submit in editor context. Must run on the player's thread. */
    public void applySettingsForm(final Player player, final String zoneName, final Map<String, String> inputs) {
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null || !edits(session, zoneName)) {
            Texts.send(player, Msg.of("menu_action_invalid"));
            return;
        }
        final SetResult<Zone> r = Screens.applyZoneForm(session.draft.zone(), inputs, this.plugin.getZoneService().keyContext());
        if (!r.ok()) {
            Texts.send(player, r.error());
            this.errorSound(player);
            return;
        }
        final EditResult applied = session.draft.apply(r.value());
        if (applied.success()) {
            Texts.send(player, Msg.of("editor_draft_updated"));
        }
        this.afterEdit(player, session, applied, false);
    }

    /** {@code /se set pos1|pos2}: rectangle corner at the player's raw position. */
    public void setCorner(final Player player, final int corner) {
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            Texts.send(player, Msg.of("editor_not_in_editor"));
            return;
        }
        final Location l = player.getLocation();
        this.afterEdit(player, session, session.draft.setRectangleCorner(corner, new Vec2(l.getX(), l.getZ())), true);
    }

    // ---- tools ------------------------------------------------------------------------------------------------------

    public boolean isTool(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.toolKey, PersistentDataType.STRING);
    }

    private EditorTool toolOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        final String id = meta == null ? null : meta.getPersistentDataContainer().get(this.toolKey, PersistentDataType.STRING);
        return id == null ? null : EditorTool.byId(id);
    }

    private void giveTools(final Player player, final EditorSession session) {
        final ShapeType shape = session.draft.zone().shape().type();
        for (final EditorTool tool : EditorTool.values()) {
            player.getInventory().setItem(tool.slot(), this.createTool(tool, shape));
        }
    }

    private ItemStack createTool(final EditorTool tool, final ShapeType shape) {
        Material material = Material.matchMaterial(tool.suggestedItem());
        if (material == null) {
            material = Material.STICK;
        }
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        final Component name = Texts.flat(MessageUtil.component(tool.nameKey(shape)));
        final List<Component> lore = new ArrayList<>();
        for (final String line : MessageUtil.raw(tool.loreKey(shape)).split("<br>|\\n")) {
            if (!line.isBlank()) {
                lore.add(Texts.flat(MiniMessage.miniMessage().deserialize("<#aaa8a8>" + line)));
            }
        }
        try {
            meta.displayName(name);
            meta.lore(lore);
        } catch (final Throwable noAdventureMeta) {
            meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(name));
            final List<String> legacy = new ArrayList<>();
            for (final Component c : lore) {
                legacy.add(LegacyComponentSerializer.legacySection().serialize(c));
            }
            meta.setLore(legacy);
        }
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.STRING, tool.id());
        item.setItemMeta(meta);
        return item;
    }

    private void refreshShapeTools(final Player player, final EditorSession session) {
        final ShapeType shape = session.draft.zone().shape().type();
        for (final EditorTool tool : new EditorTool[]{EditorTool.SHAPE, EditorTool.RESIZE}) {
            final ItemStack current = player.getInventory().getItem(tool.slot());
            if (current == null || this.toolOf(current) == tool) {
                player.getInventory().setItem(tool.slot(), this.createTool(tool, shape));
            }
        }
    }

    // ---- HUD / preview ---------------------------------------------------------------------------------------------

    private EditorTool heldTool(final Player player) {
        return this.toolOf(player.getInventory().getItemInMainHand());
    }

    private void tick(final Player player, final EditorSession session) {
        if (!player.isOnline() || this.sessions.get(player.getUniqueId()) != session) {
            return;
        }
        session.ticks++;
        if (session.draft.isMoving()) {
            final Block target = this.targetBlock(player);
            if (target != null && session.draft.moveTo(Vec2.blockCenter(target.getX(), target.getZ()))) {
                session.previewDirty = true;
            }
        }
        final Location l = player.getLocation();
        if (l.getBlockX() != session.lastBlockX || l.getBlockY() != session.lastBlockY || l.getBlockZ() != session.lastBlockZ) {
            session.lastBlockX = l.getBlockX();
            session.lastBlockY = l.getBlockY();
            session.lastBlockZ = l.getBlockZ();
            session.previewDirty = true;
        }
        if (session.previewDirty) {
            this.refreshPreview(player, session);
        }
        this.updateHud(player, session, false);
    }

    private void refreshPreview(final Player player, final EditorSession session) {
        session.previewDirty = false;
        final ZoneService zones = this.plugin.getZoneService();
        final Zone draft = session.draft.zone();
        if (!player.getWorld().getName().equals(draft.world())) {
            session.preview.clear();
            return;
        }
        session.draft.updateWorldSpawn(zones.worldSpawn(draft.world()));
        final List<Zone> others = new ArrayList<>();
        for (final Zone z : zones.registry().inWorld(draft.world())) {
            if (session.draft.originalName() == null || !z.name().equalsIgnoreCase(session.draft.originalName())) {
                others.add(z);
            }
        }
        final Location l = player.getLocation();
        final Preview preview = Preview.build(draft, others, session.draft.worldSpawn(), l.getX(), l.getY(), l.getZ(),
                Preview.Options.DEFAULT);
        Location labelAt = null;
        Component label = null;
        if (preview.label() != null) {
            labelAt = new Location(player.getWorld(), preview.label().position().x(), preview.label().y(), preview.label().position().z());
            label = Texts.render(preview.label().text());
        }
        session.preview.update(preview.segments(), Preview.Options.DEFAULT.range(), labelAt, label);
    }

    private void updateHud(final Player player, final EditorSession session, final boolean force) {
        final EditorTool held = this.heldTool(player);
        session.bossBar.name(Texts.render(session.draft.bossbarTitle(held)));
        final long now = System.currentTimeMillis();
        if (force || (now - session.lastFeedbackMillis > 2500 && now - session.lastHintMillis > 1500)) {
            session.lastHintMillis = now;
            Texts.actionBar(player, session.draft.actionbarHint(held));
        }
    }

    private void afterEdit(final Player player, final EditorSession session, final EditResult result, final boolean sound) {
        if (result.success()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.6f);
            session.previewDirty = true;
            this.refreshShapeTools(player, session);
            this.refreshPreview(player, session);
            if (result.feedback() != null) {
                Texts.actionBar(player, result.feedback());
                session.lastFeedbackMillis = System.currentTimeMillis();
            }
            session.bossBar.name(Texts.render(session.draft.bossbarTitle(this.heldTool(player))));
        } else {
            this.errorSound(player);
            if (result.error() != null) {
                Texts.actionBar(player, result.error());
                session.lastFeedbackMillis = System.currentTimeMillis();
            }
        }
    }

    private void errorSound(final Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    private Block targetBlock(final Player player) {
        try {
            return player.getTargetBlockExact(48);
        } catch (final Throwable offRegion) {
            return null;
        }
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null || event.getAction() == Action.PHYSICAL) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final EditorTool tool = this.toolOf(player.getInventory().getItemInMainHand());
        if (tool == null) {
            return;
        }
        final boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        // A right-click on a block can produce a block and an air interaction; process one per click.
        final long now = System.nanoTime();
        final String key = tool.id() + (right ? ":r" : ":l");
        if (key.equals(session.lastActionKey) && now - session.lastActionNanos < DEBOUNCE_NANOS) {
            return;
        }
        session.lastActionKey = key;
        session.lastActionNanos = now;
        final Block block = event.getClickedBlock() != null ? event.getClickedBlock() : this.targetBlock(player);
        this.handleTool(player, session, tool, right, block);
    }

    private void handleTool(final Player player, final EditorSession session, final EditorTool tool, final boolean right, final Block block) {
        final ZoneDraft d = session.draft;
        final ShapeType shape = d.zone().shape().type();
        final Vec2 at = block != null ? Vec2.blockCenter(block.getX(), block.getZ()) : null;
        EditResult r = null;
        switch (tool) {
            case SHAPE -> {
                if (at == null) {
                    r = EditResult.fail("editor_error_no_target");
                } else if (shape == ShapeType.CIRCLE) {
                    r = right ? d.setCircleCenter(at) : null;
                } else if (shape == ShapeType.RECTANGLE) {
                    r = d.setRectangleCorner(right ? 2 : 1, at);
                } else {
                    r = right ? d.addPolygonPoint(at) : d.removePolygonPoint(at);
                }
            }
            case RESIZE -> {
                if (right && shape == ShapeType.CIRCLE) {
                    r = d.toggleRadiusStep();
                }
            }
            case HEIGHT -> r = right ? d.toggleHeightBound() : d.clearHeight();
            case MOVE -> {
                if (right) {
                    final Location l = player.getLocation();
                    r = d.isMoving() ? d.place() : d.pickUp(at != null ? at : Vec2.blockCenter(l.getBlockX(), l.getBlockZ()));
                }
            }
            case SHAPE_SWITCH -> {
                if (right) {
                    r = d.cycleShape();
                }
            }
            case UNDO_REDO -> r = right ? d.redo() : d.undo();
            case CANCEL -> {
                if (right && player.isSneaking()) {
                    this.cancel(player, true);
                    return;
                }
                if (right) {
                    Texts.actionBar(player, Msg.of("editor_hint_cancel"));
                    session.lastFeedbackMillis = System.currentTimeMillis();
                }
            }
            case SETTINGS -> {
                if (right) {
                    this.plugin.getMenuService().openZoneSettings(player, d.zone(), Screens.Context.EDITOR);
                }
            }
            case SAVE -> {
                if (right) {
                    this.save(player);
                }
            }
        }
        if (r != null) {
            this.afterEdit(player, session, r, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        final int delta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        final EditorTool previous = this.toolOf(player.getInventory().getItem(event.getPreviousSlot()));
        final boolean scrollable = previous == EditorTool.RESIZE || previous == EditorTool.HEIGHT;
        if (player.isSneaking() && scrollable && (delta == 1 || delta == 8)) {
            event.setCancelled(true);
            // Scroll "up" (selection moves to the previous slot) = +1, scroll "down" = -1 (decisions log).
            final int direction = delta == 8 ? 1 : -1;
            final EditResult r = previous == EditorTool.RESIZE
                    ? session.draft.scrollResize(direction)
                    : session.draft.scrollHeight(direction, player.getLocation().getBlockY());
            this.afterEdit(player, session, r, true);
            return;
        }
        SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
            if (this.sessions.get(player.getUniqueId()) == session) {
                this.updateHud(player, session, true);
            }
        });
    }

    // ---- inventory protection ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (this.isTool(event.getCurrentItem()) || this.isTool(event.getCursor())
                || event.getWhoClicked() instanceof final Player p && this.sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (this.isTool(event.getOldCursor())
                || event.getWhoClicked() instanceof final Player p && this.sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(final PlayerDropItemEvent event) {
        if (this.isTool(event.getItemDrop().getItemStack()) || this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(final PlayerSwapHandItemsEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(final BlockPlaceEvent event) {
        if (this.isTool(event.getItemInHand()) || this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(final BlockBreakEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(final PlayerArmorStandManipulateEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof final Player p && this.sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof final Player p && this.sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupArrow(final PlayerPickupArrowEvent event) {
        if (this.sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final Player player = event.getEntity();
        event.getDrops().removeIf(this::isTool);
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session != null) {
            // Real items are not in the inventory (nothing of them drops); they come back on respawn.
            this.end(player, session, false);
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        if (this.pendingRestore.containsKey(player.getUniqueId())) {
            SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> this.restorePending(player));
        }
    }

    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        final Player player = event.getPlayer();
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session != null) {
            this.end(player, session, true);
            Texts.send(player, Msg.of("editor_closed_world_change"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final EditorSession session = this.sessions.get(player.getUniqueId());
        if (session != null) {
            // The inventory is saved after the quit event, so restoring here persists the real items.
            this.end(player, session, true);
        } else if (this.pendingRestore.containsKey(player.getUniqueId()) && !player.isDead()) {
            this.restorePending(player);
        }
    }

    /** Reads a crash-surviving snapshot off-thread before the player joins. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        final String data = this.plugin.getPlayerDataManager().readSnapshotBlocking(event.getUniqueId());
        if (data == null) {
            return;
        }
        try {
            this.pendingRestore.put(event.getUniqueId(), InventorySnapshot.deserialize(data));
        } catch (final Exception e) {
            this.plugin.getLogger().warning("Could not read the editor inventory snapshot of " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
            if (!player.isOnline() || this.sessions.containsKey(player.getUniqueId())) {
                return;
            }
            if (this.pendingRestore.containsKey(player.getUniqueId())) {
                this.restorePending(player);
            } else {
                this.stripTools(player);
            }
        });
    }

    private void restorePending(final Player player) {
        final InventorySnapshot snapshot = this.pendingRestore.remove(player.getUniqueId());
        if (snapshot == null || player.isDead()) {
            if (snapshot != null) {
                this.pendingRestore.put(player.getUniqueId(), snapshot);
            }
            return;
        }
        this.stripTools(player);
        this.restore(player, snapshot);
        Texts.send(player, Msg.of("editor_inventory_restored"));
    }

    private void stripTools(final Player player) {
        final PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getSize(); i++) {
            if (this.isTool(inv.getItem(i))) {
                inv.setItem(i, null);
                changed = true;
            }
        }
        if (this.isTool(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /** Preview entities are never persisted; remove any that survived a crash when their chunk loads. */
    @EventHandler
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        for (final Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(this.previewKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }
}
