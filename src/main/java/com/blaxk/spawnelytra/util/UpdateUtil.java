package com.blaxk.spawnelytra.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public enum UpdateUtil {
    ;
    
    private static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private static final int DOWNLOAD_TIMEOUT = 30000;
    private static final int BUFFER_SIZE = 8192;
    
    public static boolean downloadAndInstallUpdate(final Plugin plugin, final String versionNumber) throws IOException {
        if (plugin == null || versionNumber == null || versionNumber.isEmpty()) {
            throw new IllegalArgumentException("Plugin and version number must not be null or empty");
        }
        
        final JsonObject versionInfo = fetchVersionInfo(versionNumber);
        
        final JsonArray files = versionInfo.getAsJsonArray("files");
        if (files == null || files.isEmpty()) {
            throw new IOException("No files found for version " + versionNumber);
        }
        
        String downloadUrl = null;
        String fileName = null;
        
        for (final JsonElement fileElement : files) {
            final JsonObject file = fileElement.getAsJsonObject();
            if (file.get("primary").getAsBoolean()) {
                downloadUrl = file.get("url").getAsString();
                fileName = file.get("filename").getAsString();
                break;
            }
        }
        
        if (downloadUrl == null || fileName == null) {
            final JsonObject firstFile = files.get(0).getAsJsonObject();
            downloadUrl = firstFile.get("url").getAsString();
            fileName = firstFile.get("filename").getAsString();
        }
        
        final String sanitizedFileName = sanitizeFileName(fileName);
        if (sanitizedFileName == null || sanitizedFileName.isEmpty()) {
            throw new IOException("Invalid or unsafe filename from Modrinth: " + fileName);
        }
        
        final File pluginsFolder = plugin.getDataFolder().getParentFile();
        final File updateFolder = new File(pluginsFolder, "update");
        
        if (!updateFolder.exists() && !updateFolder.mkdirs()) {
            throw new IOException("Failed to create update folder: " + updateFolder.getAbsolutePath());
        }
        
        final File tempFile = new File(updateFolder, sanitizedFileName + ".tmp");
        final File targetFile = new File(updateFolder, sanitizedFileName);
        
        downloadFile(downloadUrl, tempFile);
        
        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        return true;
    }
    
    private static JsonObject fetchVersionInfo(final String versionNumber) throws IOException {
        final URL url = new URL("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version");
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SpawnElytra-AutoUpdater");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        
        try {
            final int status = conn.getResponseCode();
            if (HttpURLConnection.HTTP_OK != status) {
                throw new IOException("HTTP " + status + " " + conn.getResponseMessage());
            }
            
            try (final InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                final JsonElement root = JsonParser.parseReader(reader);
                
                if (!root.isJsonArray()) {
                    throw new IOException("Unexpected response from Modrinth");
                }
                
                final JsonArray versions = root.getAsJsonArray();
                
                for (final JsonElement elem : versions) {
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
    
    private static void downloadFile(final String fileUrl, final File destination) throws IOException {
        final URL url = new URL(fileUrl);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SpawnElytra-AutoUpdater");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(DOWNLOAD_TIMEOUT);
        
        try {
            final int status = conn.getResponseCode();
            if (HttpURLConnection.HTTP_OK != status) {
                throw new IOException("HTTP " + status + " " + conn.getResponseMessage());
            }
            
            try (final InputStream in = conn.getInputStream();
                 final FileOutputStream out = new FileOutputStream(destination)) {
                
                final byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while (-1 != (bytesRead = in.read(buffer))) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            conn.disconnect();
        }
    }
    
    private static String sanitizeFileName(final String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        String sanitized = fileName.replace("..", "")
                                   .replace("/", "")
                                   .replace("\\", "")
                                   .replace("\0", "");
        
        final int lastSlash = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        
        if (!sanitized.toLowerCase().endsWith(".jar")) {
            return null;
        }
        
        if (!sanitized.matches("^[a-zA-Z0-9._-]+\\.jar$")) {
            return null;
        }
        
        return sanitized;
    }
}
