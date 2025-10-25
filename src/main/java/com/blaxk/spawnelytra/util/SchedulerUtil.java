package com.blaxk.spawnelytra.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public enum SchedulerUtil {
    ;

    public interface TaskHandle {
        void cancel();
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask handle;
        BukkitTaskHandle(final BukkitTask handle) { this.handle = handle; }
        @Override public void cancel() { if (handle != null) this.handle.cancel(); }
    }

    public static TaskHandle runAsync(final Plugin plugin, final Runnable task) {
        final BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runAsyncRepeating(final Plugin plugin, final Runnable task, final long initialDelayTicks, final long periodTicks) {
        final BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runNow(final Plugin plugin, final Runnable task) {
        final BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
        return new BukkitTaskHandle(t);
    }

    public static TaskHandle runSync(final Plugin plugin, final Runnable task) {
        return runNow(plugin, task);
    }

    public static TaskHandle runAtEntityNow(final Plugin plugin, final Player entity, final Runnable task) {
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
    }

    public static TaskHandle runAtEntityLater(final Plugin plugin, final Player entity, final long delayTicks, final Runnable task) {
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    public static TaskHandle runAtEntityTimer(final Plugin plugin, final Player entity, final long initialDelayTicks, final long periodTicks, final Runnable task) {
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
    }
}

