package com.blaxk.spawnelytra.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public enum BackupUtil {
    ;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void backupFile(final JavaPlugin plugin, final File source, final String relativePath) {
        if (source == null || !source.exists()) {
            return;
        }
        try {
            final File backupsRoot = new File(plugin.getDataFolder(), "backups");
            if (!backupsRoot.exists() && !backupsRoot.mkdirs()) {
                plugin.getLogger().warning("Failed to create backups directory at: " + backupsRoot.getAbsolutePath());
            }

            final String timestamp = LocalDateTime.now().format(BackupUtil.TS);
            final File tsDir = new File(backupsRoot, timestamp);
            if (!tsDir.exists() && !tsDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create timestamped backups directory at: " + tsDir.getAbsolutePath());
            }

            final File target = new File(tsDir, relativePath.replace('/', File.separatorChar));
            final File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Failed to create backup subdirectory: " + parent.getAbsolutePath());
            }

            final Path src = source.toPath();
            final Path dst = target.toPath();
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created backup: " + tsDir.getName() + File.separator + relativePath);
        } catch (final IOException ex) {
            plugin.getLogger().warning("Failed to create backup for '" + relativePath + "': " + ex.getMessage());
        }
    }

    public static File backupDirectory(final JavaPlugin plugin, final File sourceDir, final String relativeBase) {
        if (plugin == null || sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return null;
        }
        try {
            final File backupsRoot = new File(plugin.getDataFolder(), "backups");
            if (!backupsRoot.exists() && !backupsRoot.mkdirs()) {
                plugin.getLogger().warning("Failed to create backups directory at: " + backupsRoot.getAbsolutePath());
            }

            final String timestamp = LocalDateTime.now().format(BackupUtil.TS);
            final File tsDir = new File(backupsRoot, timestamp);
            if (!tsDir.exists() && !tsDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create timestamped backups directory at: " + tsDir.getAbsolutePath());
            }

            final Path base = sourceDir.toPath();
            
            Files.walk(base)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            final String rel = base.relativize(p).toString().replace('\\', '/');
                            final String combined = (relativeBase == null || relativeBase.isEmpty()) ? rel : (relativeBase.replace('\\', '/') + "/" + rel);
                            final File target = new File(tsDir, combined.replace('/', File.separatorChar));
                            final File parent = target.getParentFile();
                            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                                plugin.getLogger().warning("Failed to create backup subdirectory: " + parent.getAbsolutePath());
                            }
                            Files.copy(p, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (final IOException e) {
                            plugin.getLogger().warning("Failed to back up file '" + p + "': " + e.getMessage());
                        }
                    });

            plugin.getLogger().info("Created directory backup at: " + tsDir.getAbsolutePath());
            return tsDir;
        } catch (final IOException ex) {
            plugin.getLogger().warning("Failed to create directory backup: " + ex.getMessage());
            return null;
        }
    }
}

