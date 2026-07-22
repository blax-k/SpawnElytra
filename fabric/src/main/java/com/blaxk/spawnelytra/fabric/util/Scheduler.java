/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tick based scheduler with the timing semantics of the Bukkit scheduler: a task scheduled
 * with a delay of {@code d} ticks runs at the start of tick {@code current + max(d, 1)},
 * repeating tasks then run every {@code period} ticks. Async work runs on a daemon pool.
 */
public final class Scheduler {
    public interface TaskHandle {
        void cancel();
    }

    private static final class SyncTask implements TaskHandle {
        private final Runnable runnable;
        private final long period;
        private long nextRun;
        private volatile boolean cancelled;

        SyncTask(final Runnable runnable, final long nextRun, final long period) {
            this.runnable = runnable;
            this.nextRun = nextRun;
            this.period = period;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }
    }

    private final Logger logger;
    private final List<SyncTask> tasks = new ArrayList<>();
    private final List<SyncTask> pending = new ArrayList<>();
    private final ScheduledExecutorService async;
    private long currentTick;

    public Scheduler(final Logger logger) {
        this.logger = logger;
        this.async = Executors.newScheduledThreadPool(2, r -> {
            final Thread t = new Thread(r, "SpawnElytra-Async");
            t.setDaemon(true);
            return t;
        });
    }

    /** Called once at the start of every server tick, on the server thread. */
    public void tick() {
        this.currentTick++;
        synchronized (this.pending) {
            this.tasks.addAll(this.pending);
            this.pending.clear();
        }
        final Iterator<SyncTask> it = this.tasks.iterator();
        final List<SyncTask> due = new ArrayList<>();
        while (it.hasNext()) {
            final SyncTask task = it.next();
            if (task.cancelled) {
                it.remove();
            } else if (task.nextRun <= this.currentTick) {
                due.add(task);
            }
        }
        for (final SyncTask task : due) {
            if (task.cancelled) {
                continue;
            }
            try {
                task.runnable.run();
            } catch (final Throwable t) {
                this.logger.warn("Task generated an exception", t);
            }
            if (task.period > 0 && !task.cancelled) {
                task.nextRun = this.currentTick + task.period;
            } else {
                task.cancelled = true;
            }
        }
        this.tasks.removeIf(t -> t.cancelled);
    }

    private TaskHandle schedule(final Runnable runnable, final long delay, final long period) {
        final SyncTask task = new SyncTask(runnable, this.currentTick + Math.max(1L, delay), period);
        synchronized (this.pending) {
            this.pending.add(task);
        }
        return task;
    }

    /** Runs on the next server tick (Bukkit {@code runTask}). */
    public TaskHandle runNow(final Runnable runnable) {
        return this.schedule(runnable, 0L, 0L);
    }

    public TaskHandle runLater(final long delayTicks, final Runnable runnable) {
        return this.schedule(runnable, delayTicks, 0L);
    }

    public TaskHandle runTimer(final long initialDelayTicks, final long periodTicks, final Runnable runnable) {
        return this.schedule(runnable, initialDelayTicks, Math.max(1L, periodTicks));
    }

    public TaskHandle runAsync(final Runnable runnable) {
        final var future = this.async.submit(runnable);
        return () -> future.cancel(false);
    }

    public TaskHandle runAsyncRepeating(final long initialDelayTicks, final long periodTicks, final Runnable runnable) {
        final ScheduledFuture<?> future = this.async.scheduleAtFixedRate(() -> {
            try {
                runnable.run();
            } catch (final Throwable t) {
                this.logger.warn("Async task generated an exception", t);
            }
        }, Math.max(1L, initialDelayTicks * 50L), Math.max(1L, periodTicks * 50L), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    public void shutdown() {
        synchronized (this.pending) {
            this.pending.clear();
        }
        this.tasks.clear();
        this.async.shutdownNow();
    }
}
