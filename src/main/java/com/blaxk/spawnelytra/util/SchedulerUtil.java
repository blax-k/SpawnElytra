/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public enum SchedulerUtil {
    ;

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (final ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public interface TaskHandle {
        void cancel();
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask handle;
        BukkitTaskHandle(final BukkitTask handle) { this.handle = handle; }
        @Override public void cancel() { if (this.handle != null) this.handle.cancel(); }
    }

    private static final class FoliaTaskHandle implements TaskHandle {
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask handle;
        FoliaTaskHandle(final io.papermc.paper.threadedregions.scheduler.ScheduledTask handle) { this.handle = handle; }
        @Override public void cancel() { if (handle != null) this.handle.cancel(); }
    }

    public static TaskHandle runAsync(final Plugin plugin, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return new FoliaTaskHandle(t);
        }
        final BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runAsyncRepeating(final Plugin plugin, final Runnable task, final long initialDelayTicks, final long periodTicks) {
        if (FOLIA) {
            final long initialDelayMs = Math.max(1, initialDelayTicks * 50);
            final long periodMs = Math.max(1, periodTicks * 50);
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(),
                            initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            return new FoliaTaskHandle(t);
        }
        final BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runNow(final Plugin plugin, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return new FoliaTaskHandle(t);
        }
        final BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
        return new BukkitTaskHandle(t);
    }

    /** Whether the calling thread may touch blocks/entities at this location (main thread on Paper, owning region on Folia). */
    public static boolean isOwnedByCurrentThread(final Location location) {
        if (FOLIA) {
            return Bukkit.isOwnedByCurrentRegion(location);
        }
        return Bukkit.isPrimaryThread();
    }

    /** Whether the calling thread owns this (non-player) entity. */
    public static boolean isEntityOwnedByCurrentThread(final Entity entity) {
        if (FOLIA) {
            return Bukkit.isOwnedByCurrentRegion(entity);
        }
        return Bukkit.isPrimaryThread();
    }

    /** Runs the task on the thread owning {@code entity}: inline when already there, otherwise via its scheduler. */
    public static void runForAnyEntity(final Plugin plugin, final Entity entity, final Runnable task) {
        if (isEntityOwnedByCurrentThread(entity)) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs the task on the thread owning the region at {@code location} (main thread on Paper). */
    public static void runAtLocation(final Plugin plugin, final Location location, final Runnable task) {
        if (isOwnedByCurrentThread(location)) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static TaskHandle runAtEntityNow(final Plugin plugin, final Player entity, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            return new FoliaTaskHandle(t);
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * Whether the calling thread may touch this player's state: the main thread on Paper,
     * the owning region thread (or the shutdown thread) on Folia.
     */
    public static boolean isOwnedByCurrentThread(final Player entity) {
        if (FOLIA) {
            return Bukkit.isOwnedByCurrentRegion(entity);
        }
        return Bukkit.isPrimaryThread();
    }

    /**
     * Runs a per-player mutation on the thread that owns the player. Runs inline when the caller
     * already owns the player (always the case for Paper main-thread callers and for Folia shutdown),
     * otherwise hops to the player's scheduler. Silently skipped when the plugin is already disabled
     * or the player has left (Folia retires the entity scheduler).
     */
    public static void runForEntity(final Plugin plugin, final Player entity, final Runnable task) {
        if (isOwnedByCurrentThread(entity)) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        runAtEntityNow(plugin, entity, task);
    }

    public static TaskHandle runAtEntityLater(final Plugin plugin, final Player entity, final long delayTicks, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1, delayTicks));
            return new FoliaTaskHandle(t);
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static TaskHandle runAtEntityTimer(final Plugin plugin, final Player entity, final long initialDelayTicks, final long periodTicks, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), null,
                            Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
            return new FoliaTaskHandle(t);
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
    }
}

