/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import com.blaxk.spawnelytra.fabric.Main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Timestamped backups under {@code config/spawnelytra/backups/<yyyyMMdd-HHmmss>/}, as on Paper. */
public enum BackupUtil {
    ;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void backupFile(final Main plugin, final File source, final String relativePath) {
        if (source == null || !source.exists()) {
            return;
        }
        try {
            final File backupsRoot = new File(plugin.getDataFolder(), "backups");
            if (!backupsRoot.exists() && !backupsRoot.mkdirs()) {
                plugin.getLogger().warn("Failed to create backups directory at: {}", backupsRoot.getAbsolutePath());
            }

            final String timestamp = LocalDateTime.now().format(TS);
            final File tsDir = new File(backupsRoot, timestamp);
            if (!tsDir.exists() && !tsDir.mkdirs()) {
                plugin.getLogger().warn("Failed to create timestamped backups directory at: {}", tsDir.getAbsolutePath());
            }

            final File target = new File(tsDir, relativePath.replace('/', File.separatorChar));
            final File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warn("Failed to create backup subdirectory: {}", parent.getAbsolutePath());
            }

            final Path src = source.toPath();
            final Path dst = target.toPath();
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created backup: {}{}{}", tsDir.getName(), File.separator, relativePath);
        } catch (final IOException ex) {
            plugin.getLogger().warn("Failed to create backup for '{}': {}", relativePath, ex.getMessage());
        }
    }
}
