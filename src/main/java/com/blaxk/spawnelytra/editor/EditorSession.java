/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.editor;

import com.blaxk.spawnelytra.common.editor.ZoneDraft;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import net.kyori.adventure.bossbar.BossBar;

import java.util.UUID;

/** One player's editor session. Only touched on that player's thread. */
public final class EditorSession {
    final UUID playerId;
    final ZoneDraft draft;
    final InventorySnapshot snapshot;
    final PreviewRenderer preview;
    final BossBar bossBar;
    /** Lowercase name the zone lock is held under. */
    final String lockName;
    SchedulerUtil.TaskHandle tickTask;
    boolean previewDirty = true;
    int lastBlockX = Integer.MIN_VALUE;
    int lastBlockY = Integer.MIN_VALUE;
    int lastBlockZ = Integer.MIN_VALUE;
    long lastFeedbackMillis;
    long lastHintMillis;
    long lastActionNanos;
    String lastActionKey = "";
    int ticks;

    EditorSession(final UUID playerId, final ZoneDraft draft, final InventorySnapshot snapshot, final PreviewRenderer preview,
                  final BossBar bossBar, final String lockName) {
        this.playerId = playerId;
        this.draft = draft;
        this.snapshot = snapshot;
        this.preview = preview;
        this.bossBar = bossBar;
        this.lockName = lockName;
    }

    public ZoneDraft draft() {
        return this.draft;
    }

    public String lockName() {
        return this.lockName;
    }
}
