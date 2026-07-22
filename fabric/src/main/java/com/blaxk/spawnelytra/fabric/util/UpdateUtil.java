/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import com.blaxk.spawnelytra.fabric.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modrinth update check and auto-update for the Fabric build. Versions are filtered by
 * {@code loader=fabric} and the running Minecraft version, so only a jar that can actually
 * load on this server is offered. Fabric has no {@code plugins/update} folder, so the new
 * jar is written to {@code mods/} and the running jar is removed (immediately where the OS
 * allows it, otherwise when the server stops).
 */
public enum UpdateUtil {
    ;

    public static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private static final String USER_AGENT = "SpawnElytra-AutoUpdater";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;
    private static final int DOWNLOAD_TIMEOUT = 30000;
    private static final Pattern BASE_VERSION = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)");

    /** The Modrinth versions endpoint filtered to Fabric builds for the running game version. */
    public static String versionEndpoint() {
        final String loaders = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
        final String games = URLEncoder.encode("[\"" + Compat.gameVersion() + "\"]", StandardCharsets.UTF_8);
        return "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version?loaders=" + loaders + "&game_versions=" + games;
    }

    /** The numeric part of a version string ({@code 1.6+fabric-1.21.1} -> {@code 1.6}). */
    public static String baseVersion(final String version) {
        if (version == null) {
            return "";
        }
        final Matcher m = BASE_VERSION.matcher(version.trim());
        return m.find() ? m.group(1) : version.trim();
    }

    /** Latest release version number, or {@code null} when no Fabric release exists for this game version. */
    public static String fetchLatestVersionNumber(final String currentVersion) throws IOException {
        final HttpURLConnection conn = openConnection(versionEndpoint(), READ_TIMEOUT, "SpawnElytra/" + currentVersion);
        try {
            requireOk(conn);
            try (final InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                final JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    throw new IOException("Unexpected response from Modrinth");
                }
                final JsonArray versions = root.getAsJsonArray();
                if (versions.isEmpty()) {
                    return null;
                }
                for (final JsonElement elem : versions) {
                    final JsonObject obj = elem.getAsJsonObject();
                    if ("release".equalsIgnoreCase(obj.get("version_type").getAsString())) {
                        return obj.get("version_number").getAsString();
                    }
                }
                throw new IOException("No release versions found");
            }
        } finally {
            conn.disconnect();
        }
    }

    public static boolean downloadAndInstallUpdate(final Main plugin, final String versionNumber) throws IOException {
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

        final Path modsRoot = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        Files.createDirectories(modsRoot);
        final Path targetFile = modsRoot.resolve(sanitizedFileName).normalize();
        final Path tempFile = modsRoot.resolve(sanitizedFileName + ".tmp").normalize();
        if (!targetFile.startsWith(modsRoot) || !tempFile.startsWith(modsRoot)) {
            throw new IOException("Resolved update path escapes the mods directory: " + sanitizedFileName);
        }

        final Optional<Path> currentJar = currentJar();
        if (currentJar.isPresent() && currentJar.get().toAbsolutePath().normalize().equals(targetFile)) {
            throw new IOException("Version " + versionNumber + " is already installed");
        }

        downloadFile(downloadUrl, tempFile);
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        currentJar.ifPresent(jar -> scheduleRemoval(plugin, jar));
        return true;
    }

    private static Optional<Path> currentJar() {
        return FabricLoader.getInstance().getModContainer("spawnelytra")
                .map(ModContainer::getOrigin)
                .flatMap(origin -> {
                    try {
                        final List<Path> paths = origin.getPaths();
                        return paths.stream().filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).findFirst();
                    } catch (final UnsupportedOperationException e) {
                        return Optional.empty();
                    }
                });
    }

    /** Removes the running jar so the next start only loads the new one. */
    private static void scheduleRemoval(final Main plugin, final Path jar) {
        try {
            Files.deleteIfExists(jar);
            return;
        } catch (final IOException locked) {
            // Windows keeps the running jar locked; retry when the JVM exits.
        }
        final Path absolute = jar.toAbsolutePath();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(absolute);
            } catch (final IOException e) {
                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                    try {
                        new ProcessBuilder("cmd.exe", "/c", "ping -n 4 127.0.0.1 >nul & del /f /q \"" + absolute + "\"")
                                .inheritIO().start();
                    } catch (final IOException ignored) {
                    }
                }
            }
        }, "SpawnElytra-UpdateCleanup"));
        plugin.getLogger().info("The old jar {} will be removed when the server stops.", absolute.getFileName());
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
        final HttpURLConnection conn = openConnection(versionEndpoint(), READ_TIMEOUT, USER_AGENT);
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
        final HttpURLConnection conn = openConnection(fileUrl, DOWNLOAD_TIMEOUT, USER_AGENT);
        try {
            requireOk(conn);

            try (final InputStream in = conn.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(final String url, final int readTimeout, final String userAgent) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
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

        String sanitized = fileName.replace('\\', '/');
        final int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        sanitized = sanitized.replace("..", "").replace("\0", "");

        if (!sanitized.matches("^[a-zA-Z0-9._+-]+\\.jar$")) {
            return null;
        }

        return sanitized;
    }
}
