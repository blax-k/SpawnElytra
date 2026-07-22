/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric;

import com.blaxk.spawnelytra.fabric.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.fabric.config.ConfigSection;
import com.blaxk.spawnelytra.fabric.config.ConfigUpdater;
import com.blaxk.spawnelytra.fabric.config.LanguageUpdater;
import com.blaxk.spawnelytra.fabric.config.YamlConfiguration;
import com.blaxk.spawnelytra.fabric.data.PlayerDataManager;
import com.blaxk.spawnelytra.fabric.editor.EditorManager;
import com.blaxk.spawnelytra.fabric.command.CommandHandler;
import com.blaxk.spawnelytra.fabric.menu.MenuService;
import com.blaxk.spawnelytra.fabric.visual.Visualizer;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import com.blaxk.spawnelytra.fabric.integration.BedrockSupport;
import com.blaxk.spawnelytra.fabric.listener.SpawnElytra;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.DisplayNames;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Scheduler;
import com.blaxk.spawnelytra.fabric.util.SoundResolver;
import com.blaxk.spawnelytra.fabric.util.Texts;
import com.blaxk.spawnelytra.fabric.util.UpdateUtil;
import com.blaxk.spawnelytra.fabric.util.WorldNames;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Fabric counterpart of the Paper plugin's {@code Main}: owns the configuration, the
 * per-world instances, menus, update checks and the plugin lifecycle. One instance exists
 * while a server is running (dedicated or integrated); {@link SpawnElytraMod} wires the
 * Fabric events to it.
 */
public final class Main {
    private static final String CURRENT_VERSION = com.blaxk.spawnelytra.common.SpawnElytraCore.VERSION;
    private static final Logger LOGGER = LoggerFactory.getLogger("SpawnElytra");

    private static Main instance;

    private final MinecraftServer server;
    private final File dataFolder;
    private final Scheduler scheduler;
    private YamlConfiguration config;

    private PlayerDataManager playerDataManager;
    private TempElytraManager tempElytraManager;
    private ZoneService zoneService;
    private SpawnElytra controller;
    private EditorManager editorManager;
    private MenuService menuService;
    private Visualizer visualizer;
    private CommandHandler commandHandler;
    private final Map<String, String> lastMenuSent = new HashMap<>();
    private int remainingFirstInstallShows = 5;


    private volatile String latestVersion;
    private volatile boolean updateAvailable;
    private Scheduler.TaskHandle versionCheckTask;

    private Main(final MinecraftServer server) {
        this.server = server;
        this.dataFolder = FabricLoader.getInstance().getConfigDir().resolve("spawnelytra").toFile();
        this.scheduler = new Scheduler(LOGGER);
    }

    public static Main get() {
        return instance;
    }

    // ---- lifecycle -------------------------------------------------------------------------

    static Main enable(final MinecraftServer server) {
        final Main plugin = new Main(server);
        instance = plugin;
        plugin.onEnable();
        return plugin;
    }

    static void disable() {
        final Main plugin = instance;
        if (plugin != null) {
            try {
                plugin.onDisable();
            } finally {
                instance = null;
            }
        }
    }

    private void onEnable() {
        this.getLogger().info("Enabling SpawnElytra v{}", this.getVersion());
        Texts.init(this.server.registryAccess());

        this.initializeConfiguration();

        this.showFirstInstallWelcomeIfNeeded();
        this.registerListeners();
        this.registerPlaceholders();

        new VersionChecker().start();
    }

    private void initializeConfiguration() {
        this.saveDefaultConfig();
        this.reloadConfig();

        this.playerDataManager = new PlayerDataManager(this);
        this.playerDataManager.initialize();

        final boolean migratedFromV13 = ConfigUpdater.updateConfig(this);

        this.saveLanguageFiles();
        LanguageUpdater.updateLanguages(this, migratedFromV13);
        MessageUtil.loadMessages(this);
        this.zoneService = new ZoneService(this);
        this.zoneService.load();
        this.controller = new SpawnElytra(this, this.zoneService);
    }

    private void showFirstInstallWelcomeIfNeeded() {
        final boolean firstInstallPending = !this.getConfig().getBoolean("first_install_completed", false);
        if (firstInstallPending) {
            for (final ServerPlayer p : this.getOnlinePlayers()) {
                if (Compat.isOp(p)) {
                    final UUID uuid = p.getUUID();
                    this.scheduler.runLater(40L, () -> this.sendFirstInstallWelcome(this.getPlayer(uuid)));
                }
            }
        }
    }

    private void registerListeners() {
        BedrockSupport.initialize(this);
        this.tempElytraManager = new TempElytraManager(this);
        this.menuService = new MenuService(this);
        this.editorManager = new EditorManager(this);
        this.visualizer = new Visualizer(this);
        this.commandHandler = new CommandHandler(this);
        for (final ServerPlayer player : this.getOnlinePlayers()) {
            this.editorManager.onJoin(player);
        }
    }

    private void registerPlaceholders() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            try {
                com.blaxk.spawnelytra.fabric.integration.PlaceholderIntegration.register();
            } catch (final Throwable t) {
                this.getLogger().warn("Failed to register Spawn Elytra placeholders: {}", t.toString());
            }
        }
    }

    private void onDisable() {
        this.getLogger().info("Disabling SpawnElytra v{}", this.getVersion());
        if (editorManager != null) {
            // First: give editing players their real inventory back while we can still touch them.
            this.editorManager.stopAll(true);
        }

        if (tempElytraManager != null) {
            this.tempElytraManager.restoreAll();
        }

        if (this.controller != null) {
            for (final ServerPlayer player : this.getOnlinePlayers()) {
                this.controller.cleanupPlayer(player);
                this.visualizer.stop(player, false);
            }
        }

        if (playerDataManager != null) {
            this.playerDataManager.saveAllPlayerData();
        }

        if (this.zoneService != null) {
            this.zoneService.shutdown();
        }

        if (versionCheckTask != null) {
            this.versionCheckTask.cancel();
            this.versionCheckTask = null;
        }

        this.scheduler.shutdown();
    }

    // ---- Bukkit-like plugin API --------------------------------------------------------------

    public MinecraftServer getServer() {
        return this.server;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public Scheduler getScheduler() {
        return this.scheduler;
    }

    public File getDataFolder() {
        return this.dataFolder;
    }

    public YamlConfiguration getConfig() {
        if (this.config == null) {
            this.reloadConfig();
        }
        return this.config;
    }

    public InputStream getResource(final String path) {
        return Main.class.getClassLoader().getResourceAsStream(path);
    }

    public void saveDefaultConfig() {
        final File configFile = new File(this.dataFolder, "config.yml");
        if (configFile.exists()) {
            return;
        }
        try (InputStream in = this.getResource("config.yml")) {
            if (in == null) {
                return;
            }
            Files.createDirectories(this.dataFolder.toPath());
            Files.write(configFile.toPath(), in.readAllBytes());
        } catch (final IOException e) {
            this.getLogger().error("Could not save config.yml to {}", configFile, e);
        }
    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(new File(this.dataFolder, "config.yml"), this.getLogger());
        try (InputStream in = this.getResource("config.yml")) {
            if (in != null) {
                this.config.setDefaults(YamlConfiguration.loadConfiguration(in));
            }
        } catch (final IOException ignored) {
        }
    }

    public void saveConfig() {
        try {
            this.getConfig().save(new File(this.dataFolder, "config.yml"));
        } catch (final IOException e) {
            this.getLogger().error("Could not save config to {}", new File(this.dataFolder, "config.yml"), e);
        }
    }

    public String getVersion() {
        return this.metadata().getVersion().getFriendlyString();
    }

    public String getAuthor() {
        final var authors = this.metadata().getAuthors();
        return authors.isEmpty() ? "Unknown" : authors.iterator().next().getName();
    }

    public String getWebsite() {
        return this.metadata().getContact().get("homepage").orElse(null);
    }

    private ModMetadata metadata() {
        return FabricLoader.getInstance().getModContainer("spawnelytra").orElseThrow().getMetadata();
    }

    public List<ServerPlayer> getOnlinePlayers() {
        return new ArrayList<>(this.server.getPlayerList().getPlayers());
    }

    public ServerPlayer getPlayer(final UUID uuid) {
        return uuid == null ? null : this.server.getPlayerList().getPlayer(uuid);
    }

    // ---- events ----------------------------------------------------------------------------

    public void onPlayerJoin(final ServerPlayer player) {
        if (Compat.isOp(player) && !this.getConfig().getBoolean("first_install_completed", false)) {
            this.sendFirstInstallWelcome(player);
        }

        if (Compat.isOp(player) && this.updateAvailable && latestVersion != null) {
            final UUID uuid = player.getUUID();
            this.scheduler.runLater(20L, () -> {
                final ServerPlayer online = this.getPlayer(uuid);
                if (online != null) {
                    this.sendUpdateNotification(online.createCommandSourceStack());
                }
            });
        }
    }

    public void onPlayerQuit(final ServerPlayer player) {
        this.lastMenuSent.remove(player.getUUID().toString());
        if (this.editorManager != null) {
            this.editorManager.onQuit(player);
        }
        if (this.controller != null) {
            this.controller.onQuit(player);
        }
        if (this.visualizer != null) {
            this.visualizer.onQuit(player);
        }
        if (this.menuService != null) {
            this.menuService.onQuit(player);
        }
        if (this.playerDataManager != null) {
            this.playerDataManager.flushAsync(player.getUUID());
        }
        BedrockSupport.forget(player.getUUID());
    }

    // ---- update notifications ------------------------------------------------------------------

    private void sendUpdateNotification(final CommandSourceStack recipient) {
        if (recipient == null || latestVersion == null) {
            return;
        }

        final String language = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);

        MessageUtil.sendRaw(recipient, this.getNewVersionMessage(language));
        MessageUtil.sendRaw(recipient, this.getUpdateToVersionMessage(language, this.latestVersion));
        final ServerPlayer p = recipient.getPlayer();
        if (p != null && BedrockSupport.isBedrockPlayer(p)) {
            MessageUtil.sendRaw(recipient, this.getDownloadCommandsMessage(language));
        } else {
            MessageUtil.sendRaw(recipient, this.getDownloadButtonsMessage(language, this.latestVersion));
        }
    }

    private Component getDownloadCommandsMessage(final String language) {
        final String modrinthUrl = this.buildUpdateLink();
        final String text = switch (language) {
            case "de" -> "<#91f251>Auto-Update: <#ffd166>/spawnelytra update</#ffd166> <#aaa8a8>• Download: <#5db3ff>" + modrinthUrl;
            case "es" -> "<#91f251>Actualización automática: <#ffd166>/spawnelytra update</#ffd166> <#aaa8a8>• Descarga: <#5db3ff>" + modrinthUrl;
            case "fr" -> "<#91f251>Mise à jour auto : <#ffd166>/spawnelytra update</#ffd166> <#aaa8a8>• Téléchargement : <#5db3ff>" + modrinthUrl;
            case "pl" -> "<#91f251>Automatyczna aktualizacja: <#ffd166>/spawnelytra update</#ffd166> <#aaa8a8>• Pobieranie: <#5db3ff>" + modrinthUrl;
            default -> "<#91f251>Auto update: <#ffd166>/spawnelytra update</#ffd166> <#aaa8a8>• Download: <#5db3ff>" + modrinthUrl;
        };
        return MiniMessage.miniMessage().deserialize(text);
    }

    private String buildUpdateLink() {
        return latestVersion != null
                ? "https://modrinth.com/plugin/spawn-elytra/version/" + this.latestVersion
                : "https://modrinth.com/plugin/spawn-elytra";
    }

    private Component getNewVersionMessage(final String language) {
        final String text = switch (language) {
            case "de" -> "<#5db3ff>Eine neue Version von Spawn Elytra ist verfügbar!";
            case "es" -> "<#5db3ff>¡Una nueva versión de Spawn Elytra está disponible!";
            case "fr" -> "<#5db3ff>Une nouvelle version de Spawn Elytra est disponible !";
            case "pl" -> "<#5db3ff>Dostępna jest aktualizacja pluginu SpawnElytra!";
            default -> "<#5db3ff>A new version of Spawn Elytra is available!";
        };
        return MiniMessage.miniMessage().deserialize(text);
    }

    private Component getUpdateToVersionMessage(final String language, final String latestVersion) {
        final String text = switch (language) {
            case "de" -> "<#fdba5e>Bitte aktualisiere auf Version <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(aktuell: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            case "es" -> "<#fdba5e>Por favor, actualiza a la versión <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(actual: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            case "fr" -> "<#fdba5e>Veuillez mettre à jour vers la version <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(actuelle : <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            case "pl" -> "<#fdba5e>Zaaktualizuj do wersji <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(obecna: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            default -> "<#fdba5e>Please update to version <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(current: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
        };
        return MiniMessage.miniMessage().deserialize(text);
    }

    private Component getDownloadButtonsMessage(final String language, final String version) {
        final String modrinthUrl = this.buildUpdateLink();
        final String githubUrl = "https://github.com/blax-k/SpawnElytra/releases";

        final String text = switch (language) {
            case "de" -> "<#91f251>[<click:run_command:'/spawnelytra update'><hover:show_text:'<#91f251>Automatisch auf Version " + version + " aktualisieren\n<#ffd166>⚠ Server-Neustart erforderlich\n<#FF7F50>    ⏩ Experimentelle Funktion'>⏷ Auto Update</hover></click>] " +
                    "[<click:open_url:'" + githubUrl + "'><hover:show_text:'<#91f251>GitHub-Releases öffnen'>⏷ GitHub</hover></click>] " +
                    "[<click:open_url:'" + modrinthUrl + "'><hover:show_text:'<#91f251>Modrinth-Seite öffnen'>⏷ Modrinth</hover></click>]";
            case "es" -> "<#91f251>[<click:run_command:'/spawnelytra update'><hover:show_text:'<#91f251>Actualizar automáticamente a la versión " + version + "\n<#ffd166>⚠ Se requiere reinicio del servidor\n<#FF7F50>    ⏩ Función experimental'>⏷ Auto Actualización</hover></click>] " +
                    "[<click:open_url:'" + githubUrl + "'><hover:show_text:'<#91f251>Abrir GitHub releases'>⏷ GitHub</hover></click>] " +
                    "[<click:open_url:'" + modrinthUrl + "'><hover:show_text:'<#91f251>Abrir página de Modrinth'>⏷ Modrinth</hover></click>]";
            case "fr" -> "<#91f251>[<click:run_command:'/spawnelytra update'><hover:show_text:'<#91f251>Mettre à jour automatiquement vers la version " + version + "\n<#ffd166>⚠ Redémarrage du serveur requis\n<#FF7F50>    ⏩ Fonction expérimentale'>⏷ Mise à Jour Automatique</hover></click>] " +
                    "[<click:open_url:'" + githubUrl + "'><hover:show_text:'<#91f251>Ouvrir les publications GitHub'>⏷ GitHub</hover></click>] " +
                    "[<click:open_url:'" + modrinthUrl + "'><hover:show_text:'<#91f251>Ouvrir la page Modrinth'>⏷ Modrinth</hover></click>]";
            case "pl" -> "<#91f251>[<click:run_command:'/spawnelytra update'><hover:show_text:'<#91f251>Automatycznie zaaktualizuj do wersji " + version + "\n<#ffd166>⚠ Wymagany jest restart serwera\n<#FF7F50>    ⏩ Funkcja eksperymentalna'>⏷ Zaaktualizuj automatycznie</hover></click>] " +
                    "[<click:open_url:'" + githubUrl + "'><hover:show_text:'<#91f251>Otwórz zakładkę \'Releases\' na GitHubie'>⏷ GitHub</hover></click>] " +
                    "[<click:open_url:'" + modrinthUrl + "'><hover:show_text:'<#91f251>Otwórz stronę pluginu na Modrinth'>⏷ Modrinth</hover></click>]";
            default -> "<#91f251>[<click:run_command:'/spawnelytra update'><hover:show_text:'<#91f251>Automatically update to version " + version + "\n<#ffd166>⚠ Server restart required\n<#FF7F50>    ⏩ Experimental feature'>⏷ Auto Update</hover></click>] " +
                    "[<click:open_url:'" + githubUrl + "'><hover:show_text:'<#91f251>Open GitHub releases'>⏷ GitHub</hover></click>] " +
                    "[<click:open_url:'" + modrinthUrl + "'><hover:show_text:'<#91f251>Open Modrinth release page'>⏷ Modrinth</hover></click>]";
        };
        return MiniMessage.miniMessage().deserialize(text);
    }

    public void performAutoUpdate(final CommandSourceStack sender) {
        if (latestVersion == null) {
            this.sendAutoUpdateMessage(sender, "update_no_version_available");
            return;
        }

        final String version = this.latestVersion;
        this.sendAutoUpdateMessage(sender, "update_starting", version);

        this.scheduler.runAsync(() -> {
            try {
                this.getLogger().info("Starting auto-update to version {}", version);

                final boolean success = UpdateUtil.downloadAndInstallUpdate(this, version);

                if (success) {
                    this.getLogger().info("Auto-update completed successfully. Please restart the server.");

                    this.scheduler.runNow(() -> {
                        this.getOnlinePlayers().stream()
                                .filter(Compat::isOp)
                                .forEach(p -> this.sendAutoUpdateMessage(p.createCommandSourceStack(), "update_success", version));

                        final ServerPlayer senderPlayer = sender.getPlayer();
                        if (senderPlayer == null || !Compat.isOp(senderPlayer)) {
                            this.sendAutoUpdateMessage(sender, "update_success", version);
                        }
                    });
                } else {
                    this.getLogger().warn("Auto-update failed");
                    this.scheduler.runNow(() -> this.sendAutoUpdateMessage(sender, "update_failed"));
                }
            } catch (final Exception e) {
                this.getLogger().error("Auto-update failed: {}", e.getMessage());

                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                this.scheduler.runNow(() -> this.sendAutoUpdateMessage(sender, "update_error", errorMsg));
            }
        });
    }

    private void sendAutoUpdateMessage(final CommandSourceStack sender, final String messageKey, final String... args) {
        if (sender == null) return;

        final String language = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        final Component message = this.getAutoUpdateStatusMessage(language, messageKey, args);
        MessageUtil.sendRaw(sender, message);
    }

    private Component getAutoUpdateStatusMessage(final String language, final String messageKey, final String... args) {
        final String version = args.length > 0 ? args[0] : "";
        final String errorMsg = args.length > 0 ? args[0] : "";
        final String unknownError = switch (language) {
            case "de" -> "Unbekannter Fehler";
            case "es" -> "Error desconocido";
            case "fr" -> "Erreur inconnue";
            case "pl" -> "Nieznany błąd";
            default -> "Unknown error";
        };

        final String text = switch (messageKey) {
            case "update_no_version_available" -> switch (language) {
                case "de" -> "<#fd5e5e>Keine neue Version verfügbar zum Aktualisieren.";
                case "es" -> "<#fd5e5e>No hay una nueva versión disponible para actualizar.";
                case "fr" -> "<#fd5e5e>Aucune nouvelle version disponible pour la mise à jour.";
                case "pl" -> "<#fd5e5e>Nie ma dostępnych aktualizacji.";
                default -> "<#fd5e5e>No new version available to update.";
            };
            case "update_starting" -> switch (language) {
                case "de" -> "<#91f251>Starte automatisches Update auf Version <#ffd166>" + version + "</#ffd166>...";
                case "es" -> "<#91f251>Iniciando actualización automática a la versión <#ffd166>" + version + "</#ffd166>...";
                case "fr" -> "<#91f251>Démarrage de la mise à jour automatique vers la version <#ffd166>" + version + "</#ffd166>...";
                case "pl" -> "<#91f251>Rozpoczęto automatyczną aktualizację do wersji <#ffd166>" + version + "</#ffd166>...";
                default -> "<#91f251>Starting automatic update to version <#ffd166>" + version + "</#ffd166>...";
            };
            case "update_success" -> switch (language) {
                case "de" -> "<#91f251>✔ Update erfolgreich heruntergeladen und installiert!\n<#ffd166>⚠ Bitte starte den Server neu, um Version <#91f251>" + version + "</#91f251> zu laden.";
                case "es" -> "<#91f251>✔ ¡Actualización descargada e instalada con éxito!\n<#ffd166>⚠ Por favor, reinicia el servidor para cargar la versión <#91f251>" + version + "</#91f251>.";
                case "fr" -> "<#91f251>✔ Mise à jour téléchargée et installée avec succès !\n<#ffd166>⚠ Veuillez redémarrer le serveur pour charger la version <#91f251>" + version + "</#91f251>.";
                case "pl" -> "<#91f251>✔ Aktualizacja została pobrana i zainstalowana!\n<#ffd166>⚠ Uruchom ponownie serwer aby załadować wersję <#91f251>" + version + "</#91f251>.";
                default -> "<#91f251>✔ Update downloaded and installed successfully!\n<#ffd166>⚠ Please restart the server to load version <#91f251>" + version + "</#91f251>.";
            };
            case "update_failed" -> switch (language) {
                case "de" -> "<#fd5e5e>✗ Automatisches Update fehlgeschlagen. Bitte aktualisiere manuell.";
                case "es" -> "<#fd5e5e>✗ La actualización automática falló. Por favor, actualiza manualmente.";
                case "fr" -> "<#fd5e5e>✗ La mise à jour automatique a échoué. Veuillez mettre à jour manuellement.";
                case "pl" -> "<#fd5e5e>✗ Nie udało się przeprowadzić aktualizacji. Zainstaluj aktualizację ręcznie.";
                default -> "<#fd5e5e>✗ Automatic update failed. Please update manually.";
            };
            case "update_error" -> switch (language) {
                case "de" -> "<#fd5e5e>✗ Fehler beim Update: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                case "es" -> "<#fd5e5e>✗ Error durante la actualización: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                case "fr" -> "<#fd5e5e>✗ Erreur lors de la mise à jour : <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                case "pl" -> "<#fd5e5e>✗ Błąd aktualizacji: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                default -> "<#fd5e5e>✗ Update error: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
            };
            default -> "<#aaa8a8>Unknown message: " + messageKey;
        };

        return MiniMessage.miniMessage().deserialize(text);
    }

    // ---- zones / controller ---------------------------------------------------------------------

    public ZoneService getZoneService() {
        return this.zoneService;
    }

    /** The spawn elytra controller (one for all zones). */
    public SpawnElytra getController() {
        return this.controller;
    }

    public EditorManager getEditorManager() {
        return this.editorManager;
    }

    public MenuService getMenuService() {
        return this.menuService;
    }

    public Visualizer getVisualizer() {
        return this.visualizer;
    }

    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    /** Applies global settings changed through /se global (language, style, game modes, ...) without a full reload. */
    public void applyGlobalSettings() {
        MessageUtil.loadMessages(this);
        BedrockSupport.reloadSettings(this);
        this.zoneService.reloadGlobals();
    }

    public void markFirstInstallCompleted() {
        if (!this.getConfig().getBoolean("first_install_completed", false)) {
            this.getConfig().set("first_install_completed", true);
            this.saveConfig();
        }
    }

    // ---- settings / menus ----------------------------------------------------------------------

    public void applyLanguageSetting(final CommandSourceStack actor, final String langCode) {
        this.getConfig().set("language", langCode.toLowerCase(Locale.ROOT));
        this.saveConfig();
        MessageUtil.loadMessages(this);

        final ServerPlayer p = actor.getPlayer();
        if (p != null) {
            final String ctx = this.lastMenuSent.get(p.getUUID().toString());
            if ("first_install".equals(ctx)) {
                this.markFirstInstallCompleted();
            }
        }
        final Component confirmation = MiniMessage.miniMessage().deserialize(
                "<#91f251>Language set to <#ffd166>" + langCode + "</#ffd166>.");
        if (p != null) {
            MessageUtil.sendRaw(p, confirmation);
        } else {
            actor.sendSystemMessage(net.minecraft.network.chat.Component.literal(MessageUtil.plain("info_header")));
        }
    }

    public void applyStyleSetting(final CommandSourceStack actor, final String style) {
        final String normalized = ("small_caps".equalsIgnoreCase(style) ? "small_caps" : "classic");
        this.getConfig().set("messages.style", normalized);
        this.saveConfig();
        MessageUtil.loadMessages(this);

        final ServerPlayer p = actor.getPlayer();
        if (p != null) {
            final String ctx = this.lastMenuSent.get(p.getUUID().toString());
            if ("first_install".equals(ctx)) {
                this.markFirstInstallCompleted();
            }
        }
        final Component confirmation = MiniMessage.miniMessage().deserialize(
                "<#91f251>Style set to <#ffd166>" + normalized + "</#ffd166>.");
        if (p != null) {
            MessageUtil.sendRaw(p, confirmation);
        } else {
            actor.sendSystemMessage(net.minecraft.network.chat.Component.literal(MessageUtil.plain("info_header")));
        }
    }

    public void sendFirstInstallWelcome(final ServerPlayer player) {
        if (player == null) return;

        if (this.getConfig().getBoolean("first_install_completed", false)) {
            return;
        }

        if (remainingFirstInstallShows <= 0) {
            this.markFirstInstallCompleted();
            return;
        }

        this.remainingFirstInstallShows--;

        final boolean bedrock = BedrockSupport.isBedrockPlayer(player);

        final Component header = MiniMessage.miniMessage().deserialize("<#ffcc33>Welcome to Spawn Elytra!");
        MessageUtil.sendRaw(player, header);

        this.lastMenuSent.put(player.getUUID().toString(), "first_install");

        final Component languagePrompt = MiniMessage.miniMessage().deserialize("<#fdba5e>✎ Choose your preferred language below:");
        MessageUtil.sendRaw(player, languagePrompt);

        final Component languageButtons;
        if (bedrock) {
            languageButtons = MiniMessage.miniMessage().deserialize(
                    "   <#91f251>/spawnelytra set language <#ffd166>en<#aaa8a8> | <#ffd166>de<#aaa8a8> | <#ffd166>es<#aaa8a8> | <#ffd166>fr<#aaa8a8> | <#ffd166>pl");
        } else {
            languageButtons = MiniMessage.miniMessage().deserialize(
                    "   <#91f251>[<click:run_command:'/spawnelytra set language en'><hover:show_text:'<#91f251>Set language to English'>English</hover></click>] " +
                            "[<click:run_command:'/spawnelytra set language de'><hover:show_text:'<#91f251>Set language to German'>Deutsch</hover></click>] " +
                            "[<click:run_command:'/spawnelytra set language es'><hover:show_text:'<#91f251>Set language to Spanish'>Español</hover></click>] " +
                            "[<click:run_command:'/spawnelytra set language fr'><hover:show_text:'<#91f251>Set language to French'>Français</hover></click>] " +
                            "[<click:run_command:'/spawnelytra set language pl'><hover:show_text:'<#91f251>Set language to Polish'>Polski</hover></click>]"
            );
        }
        MessageUtil.sendRaw(player, languageButtons);

        final Component setupPrompt = MiniMessage.miniMessage().deserialize("<#fdba5e>⚐ Set up the spawn area where the Elytra spawn will work:");
        MessageUtil.sendRaw(player, setupPrompt);

        final Component setupButton;
        if (bedrock) {
            setupButton = MiniMessage.miniMessage().deserialize("   <#91f251>/spawnelytra setup");
        } else {
            setupButton = MiniMessage.miniMessage().deserialize(
                    "   <#91f251>[<click:run_command:'/spawnelytra setup'><hover:show_text:'<#91f251>Currently, the Elytra spawn works 100 blocks in every direction from the world spawn point.\nTo define a specific area, you can either use the Setup Help or configure it yourself in config.yml.'>Start Setup</hover></click>]");
        }
        MessageUtil.sendRaw(player, setupButton);

        final Component dismiss;
        if (bedrock) {
            dismiss = MiniMessage.miniMessage().deserialize("<#aaa8a8>Dismiss this message forever: /spawnelytra dismiss");
        } else {
            dismiss = MiniMessage.miniMessage().deserialize(
                    "<#aaa8a8>[<click:run_command:'/spawnelytra dismiss'><hover:show_text:'<#aaa8a8>Hide this message forever'>Dismiss this message</hover></click>]");
        }
        MessageUtil.sendRaw(player, dismiss);

        if (remainingFirstInstallShows == 0) {
            this.markFirstInstallCompleted();
        }
    }

    public void sendSettingsMenu(final ServerPlayer player) {
        if (player == null) return;

        MessageUtil.send(player, "settings_menu_header");

        final String currentLanguage = this.getConfig().getString("language", "en");
        final String currentStyle = this.getConfig().getString("messages.style", "classic");

        MessageUtil.send(player, "settings_current_language", Placeholder.unparsed("value", DisplayNames.language(currentLanguage)));
        MessageUtil.send(player, "settings_current_style", Placeholder.unparsed("value", this.prettyStyle(currentStyle)));

        final java.util.Set<String> worlds = new java.util.LinkedHashSet<>();
        for (final com.blaxk.spawnelytra.common.zone.Zone zone : this.zoneService.registry().all()) {
            if (zone.enabled()) {
                worlds.add(zone.world());
            }
        }
        final String activeWorlds = worlds.isEmpty() ? "-" : String.join(", ", worlds);
        MessageUtil.send(player, "settings_active_worlds", Placeholder.unparsed("value", activeWorlds));

        MessageUtil.sendRaw(player, Component.text(" "));

        this.lastMenuSent.put(player.getUUID().toString(), "settings");

        this.sendLanguageAndStyleChoices(player);
    }

    public void sendOptionsMenu(final ServerPlayer player) {
        if (player == null) return;

        MessageUtil.send(player, "settings_menu_header");

        MessageUtil.sendRaw(player, Component.text(" "));

        this.lastMenuSent.put(player.getUUID().toString(), "options");

        this.sendLanguageAndStyleChoices(player);
    }

    private String prettyStyle(final String style) {
        if (style == null) return "-";
        final String lang = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        return this.localizedStyleName(style.toLowerCase(Locale.ROOT), lang);
    }

    private void sendLanguageAndStyleChoices(final ServerPlayer player) {
        final String currentLanguage = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        final String rawStyle = this.getConfig().getString("messages.style", "classic");
        final String currentStyle = (rawStyle == null ? "classic" : rawStyle).toLowerCase(Locale.ROOT);

        if (BedrockSupport.isBedrockPlayer(player)) {
            MessageUtil.send(player, "settings_change_language");
            MessageUtil.sendRaw(player, MiniMessage.miniMessage().deserialize(
                    "<#91f251>/spawnelytra set language <#ffd166>en<#aaa8a8> | <#ffd166>de<#aaa8a8> | <#ffd166>es<#aaa8a8> | <#ffd166>fr<#aaa8a8> | <#ffd166>pl"
                            + " <#aaa8a8>(current: <#91f251>" + currentLanguage + "<#aaa8a8>)"));
            MessageUtil.send(player, "settings_change_style");
            MessageUtil.sendRaw(player, MiniMessage.miniMessage().deserialize(
                    "<#91f251>/spawnelytra set style <#ffd166>classic<#aaa8a8> | <#ffd166>small_caps"
                            + " <#aaa8a8>(current: <#91f251>" + currentStyle + "<#aaa8a8>)"));
            return;
        }

        MessageUtil.send(player, "settings_change_language");

        final StringBuilder langBuilder = new StringBuilder("<#91f251>");
        final String[] languages = {"de", "en", "es", "fr", "pl"};
        final String[] langNames = {"Deutsch", "English", "Español", "Français", "Polski"};

        for (int i = 0; i < languages.length; i++) {
            final String lang = languages[i];
            final String langName = langNames[i];
            final boolean isSelected = currentLanguage.equals(lang);

            final String hoverKey = "language_hover_" + lang;
            final String hoverText = MessageUtil.plain(hoverKey);

            langBuilder.append("[");
            langBuilder.append("<click:run_command:'/spawnelytra set language ").append(lang).append("'>");
            langBuilder.append("<hover:show_text:'").append(hoverText).append("'>");
            if (isSelected) {
                langBuilder.append("<underlined>");
            }
            langBuilder.append(langName);
            if (isSelected) {
                langBuilder.append("</underlined>");
            }
            langBuilder.append("</hover></click>")
                    .append("] ");
        }

        final Component langs = MiniMessage.miniMessage().deserialize(langBuilder.toString().trim());
        MessageUtil.sendRaw(player, langs);

        MessageUtil.send(player, "settings_change_style");

        final StringBuilder styleBuilder = new StringBuilder("<#91f251>");
        final String[] styles = {"classic", "small_caps"};
        final String[] styleNames = {this.localizedStyleName("classic", currentLanguage), this.localizedStyleName("small_caps", currentLanguage)};

        for (int i = 0; i < styles.length; i++) {
            final String style = styles[i];
            final String styleName = styleNames[i];
            final boolean isSelected = currentStyle.equals(style);

            final String hoverKey = "style_hover_" + style;
            final String hoverText = MessageUtil.plain(hoverKey);

            styleBuilder.append("[");
            styleBuilder.append("<click:run_command:'/spawnelytra set style ").append(style).append("'>");
            styleBuilder.append("<hover:show_text:'").append(hoverText).append("'>");
            if (isSelected) {
                styleBuilder.append("<underlined>");
            }
            styleBuilder.append(styleName);
            if (isSelected) {
                styleBuilder.append("</underlined>");
            }
            styleBuilder.append("</hover></click>")
                    .append("] ");
        }

        final Component styleComponents = MiniMessage.miniMessage().deserialize(styleBuilder.toString().trim());
        MessageUtil.sendRaw(player, styleComponents);
    }

    private String localizedStyleName(final String style, final String language) {
        final String lang = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        final String s = style == null ? "" : style.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "classic" -> switch (lang) {
                case "de" -> "Klassisch";
                case "es" -> "Clásico";
                case "fr" -> "Classique";
                case "pl" -> "Klasyczny";
                default -> "Classic";
            };
            case "small_caps" -> "ꜱᴍᴀʟʟ ᴄᴀᴘꜱ";
            default -> style;
        };
    }

    private void saveLanguageFiles() {
        this.scheduler.runAsync(() -> {
            final File langDir = new File(this.getDataFolder(), "lang");
            if (!langDir.exists() && !langDir.mkdirs()) {
                this.getLogger().warn("Failed to create language directory");
                return;
            }

            final String[] languages = {"en", "de", "es", "fr", "pl"};
            for (final String lang : languages) {
                final File langFile = new File(langDir, lang + ".yml");
                if (langFile.exists()) {
                    continue;
                }
                try (final InputStream in = this.getResource("lang/" + lang + ".yml")) {
                    if (in == null) {
                        continue;
                    }
                    Files.write(langFile.toPath(), in.readAllBytes());
                } catch (final IOException e) {
                    this.getLogger().warn("Failed to write language file {}.yml: {}", lang, e.getMessage());
                }
            }
        });
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public TempElytraManager getTempElytraManager() {
        return this.tempElytraManager;
    }

    public void reload() {
        this.reloadConfig();
        MessageUtil.loadMessages(this);
        BedrockSupport.reloadSettings(this);

        if (tempElytraManager != null) {
            this.tempElytraManager.restoreAll();
        }
        if (this.editorManager != null) {
            this.editorManager.stopAll(false);
        }
        if (this.controller != null) {
            for (final ServerPlayer player : this.getOnlinePlayers()) {
                this.controller.cleanupPlayer(player);
                this.visualizer.stop(player, false);
            }
        }
        this.zoneService.load();
    }

    /** Iteration snapshot of the active instances, in registration order of the Paper listeners. */
    public List<SpawnElytra> instances() {
        return this.controller == null ? List.of() : List.of(this.controller);
    }

    private class VersionChecker {
        void start() {
            Main.this.versionCheckTask = Main.this.scheduler.runAsyncRepeating(20L * 60, 20L * 60 * 60 * 4, this::tick);
        }

        private void tick() {
            try {
                final String latest = UpdateUtil.fetchLatestVersionNumber(Main.CURRENT_VERSION);
                if (latest != null && !Main.CURRENT_VERSION.equals(UpdateUtil.baseVersion(latest))) {
                    Main.this.latestVersion = latest;
                    Main.this.updateAvailable = true;

                    Main.this.scheduler.runNow(() -> Main.this.getOnlinePlayers().stream()
                            .filter(Compat::isOp)
                            .forEach(p -> Main.this.sendUpdateNotification(p.createCommandSourceStack())));

                    final String language = Main.this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);

                    final String newVersionText = switch (language) {
                        case "de" -> "Eine neue Version von Spawn Elytra ist verfügbar!";
                        case "es" -> "¡Una nueva versión de Spawn Elytra está disponible!";
                        case "fr" -> "Une nouvelle version de Spawn Elytra est disponible !";
                        case "pl" -> "Nowa wersja SpawnElytra jest dostępna!";
                        default -> "A new version of Spawn Elytra is available!";
                    };

                    final String updateToVersionText = switch (language) {
                        case "de" -> "Bitte aktualisiere auf Version " + Main.this.latestVersion + " (aktuell: " + Main.CURRENT_VERSION + ")";
                        case "es" -> "Por favor, actualiza a la versión " + Main.this.latestVersion + " (actual: " + Main.CURRENT_VERSION + ")";
                        case "fr" -> "Veuillez mettre à jour vers la version " + Main.this.latestVersion + " (actuelle : " + Main.CURRENT_VERSION + ")";
                        case "pl" -> "Zaaktualizuj do wersji " + Main.this.latestVersion + " (obecna: " + Main.CURRENT_VERSION + ")";
                        default -> "Please update to version " + Main.this.latestVersion + " (current: " + Main.CURRENT_VERSION + ")";
                    };

                    Main.this.getLogger().warn("{} {}", newVersionText, updateToVersionText);
                    Main.this.getLogger().warn("Download: {}", Main.this.buildUpdateLink());
                } else {
                    Main.this.updateAvailable = false;
                    Main.this.latestVersion = null;
                }
            } catch (final Exception e) {
                final String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                final String sanitized = Main.this.sanitizeMiniMessageValue(errorMessage);
                Main.this.getLogger().warn(MessageUtil.plain("failed_update_check",
                        Placeholder.unparsed("error_message", sanitized)));
            }
        }
    }

    private String sanitizeMiniMessageValue(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', '[').replace('>', ']');
    }
}
