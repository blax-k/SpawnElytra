package com.blaxk.spawnelytra.util;

import org.bukkit.Bukkit;
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
        @Override public void cancel() { if (handle != null) this.handle.cancel(); }
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

    public static TaskHandle runSync(final Plugin plugin, final Runnable task) {
        return runNow(plugin, task);
    }

    public static TaskHandle runLater(final Plugin plugin, final long delayTicks, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), Math.max(1, delayTicks));
            return new FoliaTaskHandle(t);
        }
        final BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runAtEntityNow(final Plugin plugin, final Player entity, final Runnable task) {
        if (FOLIA) {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                    entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            return new FoliaTaskHandle(t);
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
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

