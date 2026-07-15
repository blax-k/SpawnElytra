/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.bedrock;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.util.SchedulerUtil;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;

public class TempElytraTrackListener implements Listener {
    private final Main plugin;
    private final TempElytraManager manager;

    public TempElytraTrackListener(final Main plugin, final TempElytraManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrack(final PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof final Player wearer) || !this.manager.hasDisguise(wearer)) {
            return;
        }
        final Player viewer = event.getPlayer();
        SchedulerUtil.runAtEntityLater(this.plugin, viewer, 2L, () -> {
            if (viewer.isOnline() && wearer.isOnline()) {
                this.manager.sendDisguise(wearer, viewer);
            }
        });
    }
}
