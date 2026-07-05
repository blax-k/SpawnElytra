/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public enum UpdateUtil {
    ;

    private static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private static final String VERSION_ENDPOINT = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version";
    private static final String USER_AGENT = "SpawnElytra-AutoUpdater";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    private static final int DOWNLOAD_TIMEOUT = 30000;

    public static boolean downloadAndInstallUpdate(final Plugin plugin, final String versionNumber) throws IOException {
        if (plugin == null || versionNumber == null || versionNumber.isEmpty()) {
            throw new IllegalArgumentException("Plugin and version number must not be null or empty");
        }

        final JsonObject versionInfo = fetchVersionInfo(versionNumber);

        final JsonArray files = versionInfo.getAsJsonArray("files");
        if (files == null || files.isEmpty()) {
            throw new IOException("No files found for version " + versionNumber);
        }

        final JsonObject targetFileInfo = selectPrimaryFile(files);
        final String downloadUrl = targetFileInfo.get("url").getAsString();
        final String fileName = targetFileInfo.get("filename").getAsString();

        final String sanitizedFileName = sanitizeFileName(fileName);
        if (sanitizedFileName == null) {
            throw new IOException("Invalid or unsafe filename from Modrinth: " + fileName);
        }

        final Path updateFolder = plugin.getDataFolder().getParentFile().toPath().resolve("update");
        Files.createDirectories(updateFolder);

        // Resolve the destination strictly inside the update folder. The filename is already
        // sanitized, but we additionally normalize the resolved paths and verify they remain
        // contained within plugins/update/, so a remote-supplied name can never escape it.
        final Path updateRoot = updateFolder.toAbsolutePath().normalize();
        final Path targetFile = updateRoot.resolve(sanitizedFileName).normalize();
        final Path tempFile = updateRoot.resolve(sanitizedFileName + ".tmp").normalize();
        if (!targetFile.startsWith(updateRoot) || !tempFile.startsWith(updateRoot)) {
            throw new IOException("Resolved update path escapes the update directory: " + sanitizedFileName);
        }

        downloadFile(downloadUrl, tempFile);

        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        return true;
    }

    private static JsonObject selectPrimaryFile(final JsonArray files) {
        for (final JsonElement fileElement : files) {
            final JsonObject file = fileElement.getAsJsonObject();
            final JsonElement primary = file.get("primary");
            if (primary != null && primary.getAsBoolean()) {
                return file;
            }
        }
        return files.get(0).getAsJsonObject();
    }

    private static JsonObject fetchVersionInfo(final String versionNumber) throws IOException {
        final HttpURLConnection conn = openConnection(VERSION_ENDPOINT, READ_TIMEOUT);
        try {
            requireOk(conn);

            try (final InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                final JsonElement root = JsonParser.parseReader(reader);

                if (!root.isJsonArray()) {
                    throw new IOException("Unexpected response from Modrinth");
                }

                for (final JsonElement elem : root.getAsJsonArray()) {
                    final JsonObject obj = elem.getAsJsonObject();
                    if (obj.get("version_number").getAsString().equals(versionNumber)) {
                        return obj;
                    }
                }

                throw new IOException("Version " + versionNumber + " not found on Modrinth");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadFile(final String fileUrl, final Path destination) throws IOException {
        final HttpURLConnection conn = openConnection(fileUrl, DOWNLOAD_TIMEOUT);
        try {
            requireOk(conn);

            try (final InputStream in = conn.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(final String url, final int readTimeout) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(readTimeout);
        return conn;
    }

    private static void requireOk(final HttpURLConnection conn) throws IOException {
        final int status = conn.getResponseCode();
        if (HttpURLConnection.HTTP_OK != status) {
            throw new IOException("HTTP " + status + " " + conn.getResponseMessage());
        }
    }

    private static String sanitizeFileName(final String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // Keep only the final path segment, then drop traversal sequences and null bytes.
        String sanitized = fileName.replace('\\', '/');
        final int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replace("..", "").replace("\0", "");

        // Accept only a plain, single-segment .jar name.
        if (!sanitized.matches("^[a-zA-Z0-9._-]+\\.jar$")) {
            return null;
        }

        return sanitized;
    }
}
