/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

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
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.SoundResolver;
import com.blaxk.spawnelytra.fabric.util.Texts;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-world zone editor (spec 6.2), a port of the Paper EditorManager: tool hotbar, input capture
 * (mixins / Fabric interaction callbacks via {@code Hooks}), packet-only display-entity preview,
 * HUD and inventory safety. The inventory snapshot is persisted asynchronously so it survives a
 * crash, and is restored on save, cancel, quit (or next join), death (on respawn), world change
 * and server stop.
 */
public final class EditorManager {
    private static final long DEBOUNCE_NANOS = 120_000_000L;
    private static final double TARGET_RANGE = 48.0;

    private final Main plugin;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> zoneLocks = new ConcurrentHashMap<>();
    /** Snapshots waiting for a respawn (death) or join (crash / missed restore). */
    private final Map<UUID, InventorySnapshot> pendingRestore = new ConcurrentHashMap<>();
    private long tick;

    public EditorManager(final Main plugin) {
        this.plugin = plugin;
        plugin.getScheduler().runTimer(1L, 1L, this::tickAll);
    }

    // ---- queries ----------------------------------------------------------------------------------------------

    public EditorSession session(final ServerPlayer player) {
        return this.sessions.get(player.getUUID());
    }

    public boolean isEditing(final ServerPlayer player) {
        return this.sessions.containsKey(player.getUUID());
    }

    public boolean isEditing(final UUID uuid) {
        return uuid != null && this.sessions.containsKey(uuid);
    }

    /** The player editing the zone (by saved name or draft name), or {@code null}. */
    public UUID editorOf(final String zoneName) {
        if (zoneName == null) {
            return null;
        }
        final UUID locked = this.zoneLocks.get(zoneName.toLowerCase(Locale.ROOT));
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

    public String playerName(final UUID id) {
        final ServerPlayer p = this.plugin.getPlayer(id);
        return p != null ? p.getName().getString() : String.valueOf(id);
    }

    // ---- start / end ------------------------------------------------------------------------------------------

    /**
     * Enters the editor for a zone (the player must be in the zone's world).
     *
     * @param originalName saved name, or {@code null} for a new zone
     */
    public boolean start(final ServerPlayer player, final Zone zone, final String originalName) {
        if (this.sessions.containsKey(player.getUUID())) {
            MessageUtil.send(player, Msg.of("zone_error_already_editing"));
            return false;
        }
        final String lock = (originalName != null ? originalName : zone.name()).toLowerCase(Locale.ROOT);
        final UUID holder = this.zoneLocks.putIfAbsent(lock, player.getUUID());
        if (holder != null && !holder.equals(player.getUUID())) {
            MessageUtil.send(player, Msg.of("zone_error_being_edited", "zone", zone.name(), "player", this.playerName(holder)));
            return false;
        }

        player.closeContainer();
        final InventorySnapshot snapshot = InventorySnapshot.capture(player);
        try {
            this.plugin.getPlayerDataManager().saveSnapshotAsync(player.getUUID(), snapshot.serialize(this.plugin.getServer().registryAccess()));
        } catch (final Exception e) {
            this.zoneLocks.remove(lock, player.getUUID());
            this.plugin.getLogger().error("Could not store the inventory of {} for the zone editor", player.getName().getString(), e);
            return false;
        }

        final ZoneService zones = this.plugin.getZoneService();
        final ZoneDraft draft = new ZoneDraft(zone, originalName, zones.worldSpawn(zone.world()));
        final ServerBossEvent bar = Compat.bossBar(net.minecraft.network.chat.Component.empty(), BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(1f);
        final EditorSession session = new EditorSession(player.getUUID(), draft, snapshot, new PreviewRenderer(player), bar, lock,
                ZoneService.worldOf(Compat.level(player)));
        this.sessions.put(player.getUUID(), session);

        InventorySnapshot.clear(player);
        this.giveTools(player, session);
        Hooks.resync(player);

        bar.addPlayer(player);
        this.updateHud(player, session, true);
        MessageUtil.send(player, Msg.of("editor_entered", "zone", zone.name()));
        return true;
    }

    /** Ends the session and gives the inventory back. */
    private void end(final ServerPlayer player, final EditorSession session, final boolean restoreNow) {
        if (!this.sessions.remove(player.getUUID(), session)) {
            return;
        }
        this.zoneLocks.remove(session.lockName, player.getUUID());
        session.preview.clear();
        session.bossBar.removeAllPlayers();
        if (restoreNow) {
            this.restore(player, session.snapshot);
        } else {
            this.pendingRestore.put(player.getUUID(), session.snapshot);
        }
    }

    /** Restores a snapshot, keeps any non-tool item the player got meanwhile, and deletes the persisted copy. */
    private void restore(final ServerPlayer player, final InventorySnapshot snapshot) {
        final List<ItemStack> extra = new ArrayList<>();
        final Inventory inv = player.getInventory();
        for (int i = 0; i < InventorySnapshot.SLOTS; i++) {
            final ItemStack item = inv.getItem(i);
            if (!item.isEmpty() && !EditorTools.isTool(item)) {
                extra.add(item.copy());
            }
        }
        final ItemStack cursor = player.containerMenu.getCarried();
        if (!cursor.isEmpty() && !EditorTools.isTool(cursor)) {
            extra.add(cursor.copy());
        }
        snapshot.restore(player);
        for (final ItemStack item : extra) {
            inv.add(item);
            if (!item.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), item);
            }
        }
        Hooks.resync(player);
        this.pendingRestore.remove(player.getUUID());
        this.plugin.getPlayerDataManager().deleteSnapshotAsync(player.getUUID());
    }

    public void cancel(final ServerPlayer player, final boolean message) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null) {
            if (message) {
                MessageUtil.send(player, Msg.of("editor_not_in_editor"));
            }
            return;
        }
        final String name = session.draft.zone().name();
        this.end(player, session, true);
        if (message) {
            MessageUtil.send(player, Msg.of("editor_cancelled", "zone", name));
        }
    }

    public void save(final ServerPlayer player) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null) {
            MessageUtil.send(player, Msg.of("editor_not_in_editor"));
            return;
        }
        final ZoneService zones = this.plugin.getZoneService();
        final ZoneDraft.SaveCheck check = session.draft.checkSave(zones.registry(), zones.keyContext());
        if (!check.ok()) {
            MessageUtil.send(player, Msg.of("editor_save_failed"));
            for (final Msg error : check.errors()) {
                MessageUtil.send(player, error);
            }
            this.errorSound(player);
            return;
        }
        for (final Msg warning : check.warnings()) {
            MessageUtil.send(player, warning);
        }
        final Zone zone = session.draft.zone();
        zones.saveZone(zone, session.draft.originalName());
        this.end(player, session, true);
        sound(player, "BLOCK_AMETHYST_BLOCK_CHIME", 0.8f, 1.2f);
        MessageUtil.send(player, Msg.of("editor_saved", "zone", zone.name()));
    }

    /** Ends every session (disable: inline, without messages; reload: with the cancel message). */
    public void stopAll(final boolean disabling) {
        for (final Map.Entry<UUID, EditorSession> e : new ArrayList<>(this.sessions.entrySet())) {
            final ServerPlayer player = this.plugin.getPlayer(e.getKey());
            if (player == null) {
                this.sessions.remove(e.getKey());
                this.zoneLocks.remove(e.getValue().lockName);
                continue;
            }
            final String name = e.getValue().draft.zone().name();
            try {
                this.end(player, e.getValue(), true);
            } catch (final Throwable t) {
                this.plugin.getLogger().warn("Could not restore the editor inventory of {} ({}); it will be restored on the next join.",
                        player.getName().getString(), t.toString());
                continue;
            }
            if (!disabling) {
                MessageUtil.send(player, Msg.of("editor_cancelled", "zone", name));
            }
        }
    }

    // ---- draft updates from commands / menus ------------------------------------------------------------------

    /** {@code /se zone set} on the zone the player edits. */
    public EditResult applyKey(final ServerPlayer player, final String key, final String value) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null) {
            return EditResult.fail("editor_not_in_editor");
        }
        final EditResult r = session.draft.applyKey(key, value, this.plugin.getZoneService().keyContext());
        this.afterEdit(player, session, r);
        return r;
    }

    /** Zone settings dialog submit in editor context. */
    public void applySettingsForm(final ServerPlayer player, final String zoneName, final Map<String, String> inputs) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null || !edits(session, zoneName)) {
            MessageUtil.send(player, Msg.of("menu_action_invalid"));
            return;
        }
        final SetResult<Zone> r = Screens.applyZoneForm(session.draft.zone(), inputs, this.plugin.getZoneService().keyContext());
        if (!r.ok()) {
            MessageUtil.send(player, r.error());
            this.errorSound(player);
            return;
        }
        final EditResult applied = session.draft.apply(r.value());
        if (applied.success()) {
            MessageUtil.send(player, Msg.of("editor_draft_updated"));
        }
        this.afterEdit(player, session, applied);
    }

    /** {@code /se set pos1|pos2}: rectangle corner at the player's raw position. */
    public void setCorner(final ServerPlayer player, final int corner) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null) {
            MessageUtil.send(player, Msg.of("editor_not_in_editor"));
            return;
        }
        this.afterEdit(player, session, session.draft.setRectangleCorner(corner, new Vec2(player.getX(), player.getZ())));
    }

    // ---- tools ------------------------------------------------------------------------------------------------

    private void giveTools(final ServerPlayer player, final EditorSession session) {
        final ShapeType shape = session.draft.zone().shape().type();
        for (final EditorTool tool : EditorTool.values()) {
            player.getInventory().setItem(tool.slot(), EditorTools.create(tool, shape));
        }
    }

    private void refreshShapeTools(final ServerPlayer player, final EditorSession session) {
        final ShapeType shape = session.draft.zone().shape().type();
        boolean changed = false;
        for (final EditorTool tool : new EditorTool[]{EditorTool.SHAPE, EditorTool.RESIZE}) {
            final ItemStack current = player.getInventory().getItem(tool.slot());
            if (current.isEmpty() || EditorTools.toolOf(current) == tool) {
                final ItemStack next = EditorTools.create(tool, shape);
                if (!ItemStack.isSameItemSameComponents(current, next)) {
                    player.getInventory().setItem(tool.slot(), next);
                    changed = true;
                }
            }
        }
        if (changed) {
            Hooks.resync(player);
        }
    }

    // ---- HUD / preview ------------------------------------------------------------------------------------------

    private EditorTool heldTool(final ServerPlayer player) {
        return EditorTools.toolOf(player.getMainHandItem());
    }

    private void tickAll() {
        this.tick++;
        for (final EditorSession session : new ArrayList<>(this.sessions.values())) {
            final ServerPlayer player = this.plugin.getPlayer(session.playerId);
            if (player == null) {
                continue;
            }
            if ((this.tick + session.playerId.hashCode()) % 4 == 0) {
                this.tick(player, session);
            }
        }
    }

    private void tick(final ServerPlayer player, final EditorSession session) {
        if (this.sessions.get(player.getUUID()) != session) {
            return;
        }
        session.ticks++;
        if (session.draft.isMoving()) {
            final BlockPos target = this.targetBlock(player, TARGET_RANGE);
            if (target != null && session.draft.moveTo(Vec2.blockCenter(target.getX(), target.getZ()))) {
                session.previewDirty = true;
            }
        }
        final int bx = (int) Math.floor(player.getX());
        final int by = (int) Math.floor(player.getY());
        final int bz = (int) Math.floor(player.getZ());
        if (bx != session.lastBlockX || by != session.lastBlockY || bz != session.lastBlockZ) {
            session.lastBlockX = bx;
            session.lastBlockY = by;
            session.lastBlockZ = bz;
            session.previewDirty = true;
        }
        if (session.previewDirty) {
            this.refreshPreview(player, session);
        }
        this.updateHud(player, session, false);
    }

    private void refreshPreview(final ServerPlayer player, final EditorSession session) {
        session.previewDirty = false;
        final ZoneService zones = this.plugin.getZoneService();
        final Zone draft = session.draft.zone();
        if (!zones.worldKey(draft.world()).equals(ZoneService.worldOf(Compat.level(player)))) {
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
        final Preview preview = Preview.build(draft, others, session.draft.worldSpawn(), player.getX(), player.getY(), player.getZ(),
                Preview.Options.DEFAULT);
        Vec3 labelAt = null;
        net.minecraft.network.chat.Component label = null;
        if (preview.label() != null) {
            labelAt = new Vec3(preview.label().position().x(), preview.label().y(), preview.label().position().z());
            label = Texts.toNative(MessageUtil.render(preview.label().text()));
        }
        session.preview.update(player, preview.segments(), Preview.Options.DEFAULT.range(), labelAt, label);
    }

    private void updateHud(final ServerPlayer player, final EditorSession session, final boolean force) {
        final EditorTool held = this.heldTool(player);
        session.bossBar.setName(Texts.toNative(MessageUtil.render(session.draft.bossbarTitle(held))));
        final long now = System.currentTimeMillis();
        if (force || (now - session.lastFeedbackMillis > 2500 && now - session.lastHintMillis > 1500)) {
            session.lastHintMillis = now;
            MessageUtil.sendActionBar(player, session.draft.actionbarHint(held));
        }
    }

    private void afterEdit(final ServerPlayer player, final EditorSession session, final EditResult result) {
        if (result.success()) {
            sound(player, "UI_BUTTON_CLICK", 0.35f, 1.6f);
            session.previewDirty = true;
            this.refreshShapeTools(player, session);
            this.refreshPreview(player, session);
            if (result.feedback() != null) {
                MessageUtil.sendActionBar(player, result.feedback());
                session.lastFeedbackMillis = System.currentTimeMillis();
            }
            session.bossBar.setName(Texts.toNative(MessageUtil.render(session.draft.bossbarTitle(this.heldTool(player)))));
        } else {
            this.errorSound(player);
            if (result.error() != null) {
                MessageUtil.sendActionBar(player, result.error());
                session.lastFeedbackMillis = System.currentTimeMillis();
            }
        }
    }

    private void errorSound(final ServerPlayer player) {
        sound(player, "BLOCK_NOTE_BLOCK_BASS", 0.6f, 0.6f);
    }

    private static void sound(final ServerPlayer player, final String name, final float volume, final float pitch) {
        final Holder<SoundEvent> sound = SoundResolver.resolve(name);
        if (sound != null) {
            Compat.playSound(player, sound, player.getX(), player.getY(), player.getZ(), volume, pitch);
        }
    }

    /** Bukkit {@code getTargetBlockExact(range)}. */
    private BlockPos targetBlock(final ServerPlayer player, final double range) {
        final HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof final BlockHitResult block) {
            return block.getBlockPos();
        }
        return null;
    }

    // ---- input ------------------------------------------------------------------------------------------------

    /** Left click into the air (arm swing / punch); clicks on blocks arrive as {@link #onLeftClick}. */
    public void onSwing(final ServerPlayer player) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null || session.lastInputTick == this.tick) {
            return; // the swing of a block click / use in this tick
        }
        if (this.targetBlock(player, player.blockInteractionRange()) != null) {
            return; // a block in reach: the attack packet carries the click
        }
        this.interact(player, session, false, null);
    }

    public void onLeftClick(final ServerPlayer player, final BlockPos pos) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session != null) {
            this.interact(player, session, false, pos);
        }
    }

    public void onRightClick(final ServerPlayer player, final BlockPos pos) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session != null) {
            this.interact(player, session, true, pos);
        }
    }

    /** Bukkit {@code PlayerInteractEvent} handler of the Paper editor. */
    private void interact(final ServerPlayer player, final EditorSession session, final boolean right, final BlockPos clicked) {
        session.lastInputTick = this.tick;
        final EditorTool tool = this.heldTool(player);
        if (tool == null) {
            return;
        }
        // A right-click on a block can produce a block and an air interaction; process one per click.
        final long now = System.nanoTime();
        final String key = tool.id() + (right ? ":r" : ":l");
        if (key.equals(session.lastActionKey) && now - session.lastActionNanos < DEBOUNCE_NANOS) {
            return;
        }
        session.lastActionKey = key;
        session.lastActionNanos = now;
        final BlockPos block = clicked != null ? clicked : this.targetBlock(player, TARGET_RANGE);
        this.handleTool(player, session, tool, right, block);
    }

    private void handleTool(final ServerPlayer player, final EditorSession session, final EditorTool tool, final boolean right, final BlockPos block) {
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
                    r = d.isMoving() ? d.place()
                            : d.pickUp(at != null ? at : Vec2.blockCenter((int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
                }
            }
            case SHAPE_SWITCH -> {
                if (right) {
                    r = d.cycleShape();
                }
            }
            case UNDO_REDO -> r = right ? d.redo() : d.undo();
            case CANCEL -> {
                if (right && player.isShiftKeyDown()) {
                    this.cancel(player, true);
                    return;
                }
                if (right) {
                    MessageUtil.sendActionBar(player, Msg.of("editor_hint_cancel"));
                    session.lastFeedbackMillis = System.currentTimeMillis();
                }
            }
            case SETTINGS -> {
                if (right) {
                    this.plugin.getMenuService().openZoneSettings(player.createCommandSourceStack(), d.zone(), Screens.Context.EDITOR);
                }
            }
            case SAVE -> {
                if (right) {
                    this.save(player);
                }
            }
        }
        if (r != null) {
            this.afterEdit(player, session, r);
        }
    }

    /**
     * Bukkit {@code PlayerItemHeldEvent}: while sneaking with the resize/height tool, a slot step
     * is cancelled and used as +1/-1. Returns {@code true} to cancel the slot change.
     */
    public boolean onHeldSlotChange(final ServerPlayer player, final int newSlot) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session == null) {
            return false;
        }
        final int previousSlot = Compat.selectedSlot(player);
        final int delta = Math.floorMod(newSlot - previousSlot, 9);
        final EditorTool previous = EditorTools.toolOf(player.getInventory().getItem(previousSlot));
        final boolean scrollable = previous == EditorTool.RESIZE || previous == EditorTool.HEIGHT;
        if (player.isShiftKeyDown() && scrollable && (delta == 1 || delta == 8)) {
            Compat.sendSelectedSlot(player, previousSlot);
            // Scroll "up" (selection moves to the previous slot) = +1, scroll "down" = -1 (decisions log).
            final int direction = delta == 8 ? 1 : -1;
            final EditResult r = previous == EditorTool.RESIZE
                    ? session.draft.scrollResize(direction)
                    : session.draft.scrollHeight(direction, (int) Math.floor(player.getY()));
            this.afterEdit(player, session, r);
            return true;
        }
        final UUID uuid = player.getUUID();
        this.plugin.getScheduler().runLater(1L, () -> {
            final ServerPlayer online = this.plugin.getPlayer(uuid);
            if (online != null && this.sessions.get(uuid) == session) {
                this.updateHud(online, session, true);
            }
        });
        return false;
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    /** Death (before drops): real items are not in the inventory; nothing of the tools drops either. */
    public void onDeath(final ServerPlayer player) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session != null) {
            this.end(player, session, false);
            InventorySnapshot.clear(player);
        } else {
            this.stripTools(player);
        }
    }

    public void onRespawn(final ServerPlayer player) {
        if (this.pendingRestore.containsKey(player.getUUID())) {
            final UUID uuid = player.getUUID();
            this.plugin.getScheduler().runLater(1L, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (online != null) {
                    this.restorePending(online);
                }
            });
        }
    }

    public void onWorldChange(final ServerPlayer player) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session != null) {
            // The client dropped the preview entities with the old dimension.
            session.preview.forget();
            this.end(player, session, true);
            MessageUtil.send(player, Msg.of("editor_closed_world_change"));
        }
    }

    public void onQuit(final ServerPlayer player) {
        final EditorSession session = this.sessions.get(player.getUUID());
        if (session != null) {
            // The player file is saved after the disconnect callback, so restoring here persists the real items.
            this.end(player, session, true);
        } else if (this.pendingRestore.containsKey(player.getUUID()) && player.isAlive()) {
            this.restorePending(player);
        }
    }

    /** Reads a crash-surviving snapshot off-thread, then restores it (or strips stray tools). */
    public void onJoin(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        this.plugin.getScheduler().runAsync(() -> {
            final String data = this.plugin.getPlayerDataManager().readSnapshotBlocking(uuid);
            this.plugin.getScheduler().runLater(1L, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (online == null || this.sessions.containsKey(uuid)) {
                    return;
                }
                if (data != null && !this.pendingRestore.containsKey(uuid)) {
                    try {
                        this.pendingRestore.put(uuid, InventorySnapshot.deserialize(this.plugin.getServer().registryAccess(), data));
                    } catch (final Exception e) {
                        this.plugin.getLogger().warn("Could not read the editor inventory snapshot of {}: {}", online.getName().getString(), e.getMessage());
                    }
                }
                if (this.pendingRestore.containsKey(uuid)) {
                    this.restorePending(online);
                } else {
                    this.stripTools(online);
                }
            });
        });
    }

    private void restorePending(final ServerPlayer player) {
        final InventorySnapshot snapshot = this.pendingRestore.remove(player.getUUID());
        if (snapshot == null || !player.isAlive()) {
            if (snapshot != null) {
                this.pendingRestore.put(player.getUUID(), snapshot);
            }
            return;
        }
        this.stripTools(player);
        this.restore(player, snapshot);
        MessageUtil.send(player, Msg.of("editor_inventory_restored"));
    }

    private void stripTools(final ServerPlayer player) {
        final Inventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (EditorTools.isTool(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (EditorTools.isTool(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (changed) {
            Hooks.resync(player);
        }
    }

    /** Small bridge so the editor does not depend on the event package. */
    private static final class Hooks {
        static void resync(final ServerPlayer player) {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
