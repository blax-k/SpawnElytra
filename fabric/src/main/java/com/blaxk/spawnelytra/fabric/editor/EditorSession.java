/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.editor;

import com.blaxk.spawnelytra.common.editor.ZoneDraft;
import net.minecraft.server.level.ServerBossEvent;

import java.util.UUID;

/** One player's editor session (server thread only). */
public final class EditorSession {
    final UUID playerId;
    final ZoneDraft draft;
    final InventorySnapshot snapshot;
    final PreviewRenderer preview;
    final ServerBossEvent bossBar;
    /** Lowercase name the zone lock is held under. */
    final String lockName;
    /** Dimension id the editor was opened in. */
    final String world;
    boolean previewDirty = true;
    int lastBlockX = Integer.MIN_VALUE;
    int lastBlockY = Integer.MIN_VALUE;
    int lastBlockZ = Integer.MIN_VALUE;
    long lastFeedbackMillis;
    long lastHintMillis;
    long lastActionNanos;
    String lastActionKey = "";
    int ticks;
    long lastInputTick = Long.MIN_VALUE;

    EditorSession(final UUID playerId, final ZoneDraft draft, final InventorySnapshot snapshot, final PreviewRenderer preview,
                  final ServerBossEvent bossBar, final String lockName, final String world) {
        this.playerId = playerId;
        this.draft = draft;
        this.snapshot = snapshot;
        this.preview = preview;
        this.bossBar = bossBar;
        this.lockName = lockName;
        this.world = world;
    }

    public ZoneDraft draft() {
        return this.draft;
    }

    public String lockName() {
        return this.lockName;
    }
}
