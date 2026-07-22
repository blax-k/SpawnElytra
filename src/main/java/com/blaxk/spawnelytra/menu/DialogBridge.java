/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.menu;

import com.blaxk.spawnelytra.common.screen.Screen;
import org.bukkit.entity.Player;

/**
 * Renders screens as Paper dialogs. The implementation lives in the separate {@code dialog} source set (compiled
 * against a Paper API that has the Dialog API) and is only loaded when the running server provides it.
 */
public interface DialogBridge {
    /** Shows the screen as a dialog. Must run on the player's thread. Returns {@code false} on failure. */
    boolean show(Player player, Screen screen);

    /** Unregisters listeners. */
    void shutdown();
}
