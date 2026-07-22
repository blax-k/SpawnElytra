/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.data;

import com.blaxk.spawnelytra.common.stats.FlightTracker;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.config.BukkitConfigView;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Player data (toggle, statistics) and editor inventory snapshots.
 * <p>
 * All file IO runs on one dedicated writer thread, never on a tick/region thread: mutations only mark the entry
 * dirty, a periodic flush (every 30 s) and quit/disable flush what changed. Files are written atomically
 * (temp file + move). Data is loaded once on enable (before any player can join).
 */
public class PlayerDataManager {
    private static final long FLUSH_PERIOD_TICKS = 20L * 30;

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File snapshotFolder;
    private final ConcurrentHashMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "SpawnElytra-PlayerData-IO");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean initialized;
    private volatile boolean shutdown;
    private SchedulerUtil.TaskHandle flushTask;

    public PlayerDataManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.snapshotFolder = new File(this.dataFolder, "editor-inventory");
        this.ensureDataFolder();
    }

    private void ensureDataFolder() {
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            this.plugin.getLogger().warning("Could not create playerdata folder: " + this.dataFolder.getAbsolutePath());
        }
    }

    public void initialize() {
        this.ensureDataFolder();

        final File[] dataFiles = this.dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (dataFiles != null) {
            for (final File file : dataFiles) {
                this.loadPlayerData(file);
            }
        }

        this.initialized = true;
    }

    public void startAutoFlush() {
        if (this.flushTask == null) {
            this.flushTask = SchedulerUtil.runAsyncRepeating(this.plugin, this::flushDirtyAsync, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
        }
    }

    private void loadPlayerData(final File file) {
        final String filename = file.getName();
        if (!filename.endsWith(".yml")) {
            return;
        }
        try {
            final UUID uuid = UUID.fromString(filename.substring(0, filename.length() - 4));
            final FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            final PlayerData data = new PlayerData(uuid);
            data.load(config);
            this.playerDataMap.put(uuid, data);
        } catch (final IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid player data file: " + file.getName());
        }
    }

    public PlayerData getPlayerData(final UUID uuid) {
        if (!this.initialized) {
            this.initialize();
        }
        return this.playerDataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    /** Marks the player's data as changed; it is written by the next flush. */
    public void markDirty(final UUID uuid) {
        this.dirty.add(uuid);
    }

    public void incrementFlyCount(final Player player) {
        this.getPlayerData(player.getUniqueId()).incrementFlyCount();
        this.markDirty(player.getUniqueId());
    }

    public void incrementBoostCount(final Player player) {
        this.getPlayerData(player.getUniqueId()).incrementBoostCount();
        this.markDirty(player.getUniqueId());
    }

    /** Schedules a write of the player's data now (quit). */
    public void flushAsync(final UUID uuid) {
        if (!this.dirty.remove(uuid)) {
            return;
        }
        final PlayerData data = this.playerDataMap.get(uuid);
        if (data == null) {
            return;
        }
        final String content = data.serialize();
        this.submit(() -> this.write(new File(this.dataFolder, uuid + ".yml"), content));
    }

    private void flushDirtyAsync() {
        final List<UUID> ids = new ArrayList<>(this.dirty);
        for (final UUID id : ids) {
            this.flushAsync(id);
        }
    }

    /** Synchronous flush of everything dirty plus pending IO; used on disable. */
    public void saveAllPlayerData() {
        if (this.flushTask != null) {
            this.flushTask.cancel();
            this.flushTask = null;
        }
        this.flushDirtyAsync();
        this.shutdown = true;
        this.io.shutdown();
        try {
            if (!this.io.awaitTermination(10, TimeUnit.SECONDS)) {
                this.plugin.getLogger().warning("Player data writer did not finish within 10 s");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void submit(final Runnable task) {
        if (this.shutdown) {
            task.run();
            return;
        }
        try {
            this.io.execute(task);
        } catch (final java.util.concurrent.RejectedExecutionException e) {
            task.run();
        }
    }

    private void write(final File file, final String content) {
        try {
            final File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                this.plugin.getLogger().warning("Could not create folder " + parent.getAbsolutePath());
            }
            final File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicUnsupported) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            this.plugin.getLogger().warning("Failed to save player data " + file.getName() + ": " + e.getMessage());
        }
    }

    // ---- editor inventory snapshots ----------------------------------------------------------------------------

    private File snapshotFile(final UUID uuid) {
        return new File(this.snapshotFolder, uuid + ".yml");
    }

    /** Persists an editor inventory snapshot (async). */
    public void saveSnapshotAsync(final UUID uuid, final String serialized) {
        this.submit(() -> this.write(this.snapshotFile(uuid), serialized));
    }

    /** Deletes a persisted snapshot (async, ordered after any pending save of the same snapshot). */
    public void deleteSnapshotAsync(final UUID uuid) {
        this.submit(() -> {
            try {
                Files.deleteIfExists(this.snapshotFile(uuid).toPath());
            } catch (final IOException e) {
                this.plugin.getLogger().warning("Failed to delete editor snapshot of " + uuid + ": " + e.getMessage());
            }
        });
    }

    /**
     * Reads a persisted snapshot. Called from the async pre-login event, so blocking IO is fine there; waits for
     * pending writes first so a quick rejoin sees the latest state.
     */
    public String readSnapshotBlocking(final UUID uuid) {
        try {
            if (!this.shutdown) {
                this.io.submit(() -> { }).get(5, TimeUnit.SECONDS);
            }
        } catch (final Exception ignored) {
        }
        final File file = this.snapshotFile(uuid);
        if (!file.isFile()) {
            return null;
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("Failed to read editor snapshot of " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        // Replaced on the owning player's thread, read by placeholders/commands and the IO thread.
        private volatile PlayerStats stats = PlayerStats.ZERO;
        private volatile boolean enabled = true;

        public PlayerData(final UUID uuid) {
            this.uuid = uuid;
        }

        synchronized void load(final FileConfiguration config) {
            final PlayerStats read = PlayerStats.read(new BukkitConfigView(config).section(PlayerStats.SECTION));
            if (config.getConfigurationSection(PlayerStats.SECTION) == null) {
                // 1.5 files only have the two counters.
                this.stats = new PlayerStats(config.getInt("fly_count", 0), 0, config.getInt("boost_count", 0), 0, 0);
            } else {
                this.stats = read;
            }
            this.enabled = config.getBoolean("enabled", true);
        }

        synchronized String serialize() {
            final YamlConfiguration config = new YamlConfiguration();
            final PlayerStats s = this.stats;
            config.set("enabled", this.enabled);
            // Kept for 1.5 compatibility (downgrades, external tools).
            config.set("fly_count", s.flights());
            config.set("boost_count", s.boosts());
            s.write(new BukkitConfigView(config).createSection(PlayerStats.SECTION));
            return config.saveToString();
        }

        public UUID getUuid() {
            return this.uuid;
        }

        public PlayerStats stats() {
            return this.stats;
        }

        public int getFlyCount() {
            return (int) this.stats.flights();
        }

        public int getBoostCount() {
            return (int) this.stats.boosts();
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public synchronized void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public synchronized void incrementFlyCount() {
            this.stats = this.stats.withFlightStarted();
        }

        public synchronized void incrementBoostCount() {
            this.stats = this.stats.withBoost();
        }

        /** Adds one finished flight (see {@link FlightTracker#complete}). */
        public synchronized void completeFlight(final FlightTracker tracker, final long endMillis) {
            this.stats = tracker.complete(this.stats, endMillis);
        }
    }
}
