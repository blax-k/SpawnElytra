/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.SoundUtil;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.UpdateUtil;
import com.blaxk.spawnelytra.util.DisplayNames;
import com.blaxk.spawnelytra.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.command.CommandHandler;
import com.blaxk.spawnelytra.config.ConfigUpdater;
import com.blaxk.spawnelytra.config.LanguageUpdater;
import com.blaxk.spawnelytra.listener.SpawnElytra;
import com.blaxk.spawnelytra.editor.EditorManager;
import com.blaxk.spawnelytra.menu.MenuService;
import com.blaxk.spawnelytra.visual.Visualizer;
import com.blaxk.spawnelytra.zone.ZoneService;
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.integration.BedrockSupport;
import com.blaxk.spawnelytra.integration.PlaceholderAPIIntegration;
import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin implements Listener {
    public static Main plugin;
    private static final String CURRENT_VERSION = com.blaxk.spawnelytra.common.SpawnElytraCore.VERSION;
    private static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private static final String MIGRATION_NOTICE_FILENAME = "MIGRATED_TO_SPAWN_ELYTRA.txt";

    private PlayerDataManager playerDataManager;
    private TempElytraManager tempElytraManager;
    // Read from every region thread on Folia (events, placeholders, commands), so these must be concurrent.
    private final Map<String, String> lastMenuSent = new ConcurrentHashMap<>();
    private final ZoneService zoneService = new ZoneService(this);
    private SpawnElytra elytra;
    private EditorManager editorManager;
    private MenuService menuService;
    private Visualizer visualizer;
    private CommandHandler commandHandler;
    private final java.util.Set<String> registeredTierPermissions = ConcurrentHashMap.newKeySet();
    private final AtomicInteger remainingFirstInstallShows = new AtomicInteger(5);


    // Written by the async version checker, read on join from region/main threads.
    private volatile String latestVersion;
    private volatile boolean updateAvailable;
    private SchedulerUtil.TaskHandle versionCheckTask;
    private Metrics metrics;

    @Override
    public void onEnable() {
        Main.plugin = this;
        
        initializeConfiguration();
        
        showFirstInstallWelcomeIfNeeded();
        setupBStats();
        registerListenersAndCommands();
        registerPlaceholders();
        
        new VersionChecker().start();
    }

    private void initializeConfiguration() {
        final boolean migrated = this.migrateFromOldDataFolderIfPresent();
        this.saveDefaultConfig();
        
        this.playerDataManager = new PlayerDataManager(this);
        this.playerDataManager.initialize();
        
        MessageUtil.initialize(this);
        final boolean migratedFromV13 = ConfigUpdater.updateConfig(this);
        
        if (migrated) {
            this.getConfig().set("first_install_completed", true);
            this.saveConfig();
        }
        
        this.saveLanguageFiles();
        LanguageUpdater.updateLanguages(this, migratedFromV13);
        MessageUtil.loadMessages(this);
        this.zoneService.load();
        this.registerTierPermissions();
    }

    /** Tier permissions default to false for everyone, ops included (spec section 3). */
    private void registerTierPermissions() {
        final org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        for (final com.blaxk.spawnelytra.common.tier.PermissionTier tier : this.zoneService.globals().tiers()) {
            final String node = tier.permission();
            if (this.registeredTierPermissions.add(node) && pm.getPermission(node) == null) {
                try {
                    pm.addPermission(new org.bukkit.permissions.Permission(node,
                            "Spawn Elytra permission tier '" + tier.name() + "'", org.bukkit.permissions.PermissionDefault.FALSE));
                } catch (final IllegalArgumentException alreadyRegistered) {
                }
            }
        }
    }

    private void showFirstInstallWelcomeIfNeeded() {
        final boolean firstInstallPending = !this.getConfig().getBoolean("first_install_completed", false);
        if (firstInstallPending) {
            for (final Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) {
                    SchedulerUtil.runAtEntityLater(this, p, 40L, () -> this.sendFirstInstallWelcome(p));
                }
            }
        }
    }

    private void setupBStats() {
        final int pluginId = 25081;
        this.metrics = new Metrics(this, pluginId);
        this.setupMetrics(this.metrics);
    }

    private void registerListenersAndCommands() {
        Bukkit.getPluginManager().registerEvents(this, this);

        BedrockSupport.initialize(this);
        this.tempElytraManager = new TempElytraManager(this);
        Bukkit.getPluginManager().registerEvents(this.tempElytraManager, this);
        try {
            Class.forName("io.papermc.paper.event.player.PlayerTrackEntityEvent");
            Bukkit.getPluginManager().registerEvents(
                    new com.blaxk.spawnelytra.bedrock.TempElytraTrackListener(this, this.tempElytraManager), this);
        } catch (final ClassNotFoundException spigot) {
        }

        this.elytra = new SpawnElytra(this, this.zoneService);
        Bukkit.getPluginManager().registerEvents(this.elytra, this);

        this.menuService = new MenuService(this);
        this.editorManager = new EditorManager(this);
        Bukkit.getPluginManager().registerEvents(this.editorManager, this);
        this.visualizer = new Visualizer(this);
        Bukkit.getPluginManager().registerEvents(this.visualizer, this);

        this.playerDataManager.startAutoFlush();

        this.commandHandler = new CommandHandler(this);
        Objects.requireNonNull(this.getCommand("spawnelytra")).setExecutor(this.commandHandler);
        Objects.requireNonNull(this.getCommand("spawnelytra")).setTabCompleter(this.commandHandler);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            final boolean registered = new PlaceholderAPIIntegration(this, this.playerDataManager).register();
            if (!registered) {
                this.getLogger().warning("Failed to register Spawn Elytra placeholders");
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.editorManager != null) {
            // First: give editing players their real inventory back while we can still touch them.
            this.editorManager.stopAll(true);
        }
        if (this.menuService != null) {
            this.menuService.shutdown();
        }
        if (tempElytraManager != null) {
            this.tempElytraManager.restoreAll();
        }

        if (playerDataManager != null) {
            this.playerDataManager.saveAllPlayerData();
        }

        if (versionCheckTask != null) {
            this.versionCheckTask.cancel();
            this.versionCheckTask = null;
        }

        this.cleanupFlightForOnlinePlayers();
        this.zoneService.shutdown();

        if (metrics != null) {
            this.metrics.shutdown();
            this.metrics = null;
        }

        MessageUtil.shutdown();
    }

    /**
     * Resets per-player state (gliding, temporary allow-flight, visualization). Each player is handled on the thread
     * that owns them: inline on Paper and during Folia shutdown, via the player's entity scheduler when a Folia
     * command or task triggers a reload.
     */
    private void cleanupFlightForOnlinePlayers() {
        final SpawnElytra listener = this.elytra;
        if (listener == null) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.runForEntity(this, player, () -> {
                listener.cleanupPlayer(player);
                if (this.visualizer != null) {
                    this.visualizer.stop(player, false);
                }
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (player.isOp() && !this.getConfig().getBoolean("first_install_completed", false)) {
            this.sendFirstInstallWelcome(player);
        }

        if (player.isOp() && this.updateAvailable && latestVersion != null) {
            SchedulerUtil.runAtEntityLater(this, player, 20L, () -> this.sendUpdateNotification(player));
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        this.lastMenuSent.remove(player.getUniqueId().toString());
        BedrockSupport.forget(player.getUniqueId());
        if (this.playerDataManager != null) {
            this.playerDataManager.flushAsync(player.getUniqueId());
        }
    }

    private void sendUpdateNotification(final Player player) {
        this.sendUpdateNotification((CommandSender) player);
    }

    private void sendUpdateNotification(final CommandSender recipient) {
        if (recipient == null || latestVersion == null) {
            return;
        }
        
        final String language = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);

        MessageUtil.sendRaw(recipient, this.getNewVersionMessage(language));
        MessageUtil.sendRaw(recipient, this.getUpdateToVersionMessage(language, this.latestVersion));
        if (recipient instanceof final Player p && BedrockSupport.isBedrockPlayer(p)) {
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

    public void performAutoUpdate(final CommandSender sender) {
        if (latestVersion == null) {
            this.sendAutoUpdateMessage(sender, "update_no_version_available");
            return;
        }
        
        this.sendAutoUpdateMessage(sender, "update_starting", latestVersion);
        
        SchedulerUtil.runAsync(this, () -> {
            try {
                this.getLogger().info("Starting auto-update to version " + latestVersion);
                
                final boolean success = UpdateUtil.downloadAndInstallUpdate(this, latestVersion);
                
                if (success) {
                    this.getLogger().info("Auto-update completed successfully. Please restart the server.");
                    
                    SchedulerUtil.runNow(this, () -> {
                        Bukkit.getOnlinePlayers().stream()
                                .filter(Player::isOp)
                                .forEach(p -> this.sendAutoUpdateMessage(p, "update_success", latestVersion));
                        
                        if (!(sender instanceof Player) || !sender.isOp()) {
                            this.sendAutoUpdateMessage(sender, "update_success", latestVersion);
                        }
                    });
                } else {
                    this.getLogger().warning("Auto-update failed");
                    SchedulerUtil.runNow(this, () -> this.sendAutoUpdateMessage(sender, "update_failed"));
                }
            } catch (final Exception e) {
                this.getLogger().severe("Auto-update failed: " + e.getMessage());
                
                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                SchedulerUtil.runNow(this, () -> this.sendAutoUpdateMessage(sender, "update_error", errorMsg));
            }
        });
    }
    
    private void sendAutoUpdateMessage(final CommandSender sender, final String messageKey, final String... args) {
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

    public SpawnElytra getSpawnElytra() {
        return this.elytra;
    }

    public ZoneService getZoneService() {
        return this.zoneService;
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
        synchronized (this) {
            MessageUtil.loadMessages(this);
            BedrockSupport.reloadSettings(this);
        }
        this.zoneService.reloadGlobals();
        this.registerTierPermissions();
    }

    public synchronized void markFirstInstallCompleted() {
        if (!this.getConfig().getBoolean("first_install_completed", false)) {
            this.getConfig().set("first_install_completed", true);
            this.saveConfig();
        }
    }

    public void applyLanguageSetting(final CommandSender actor, final String langCode) {
        synchronized (this) {
            this.getConfig().set("language", langCode.toLowerCase(Locale.ROOT));
            this.saveConfig();
            MessageUtil.loadMessages(this);
        }
        
        if (actor instanceof final Player p) {
            final String ctx = this.lastMenuSent.get(p.getUniqueId().toString());
            if ("first_install".equals(ctx)) {
                this.markFirstInstallCompleted();
            }
        }
        final Component confirmation = MiniMessage.miniMessage().deserialize(
                "<#91f251>Language set to <#ffd166>" + langCode + "</#ffd166>.");
        if (actor instanceof final Player p) {
            MessageUtil.sendRaw(p, confirmation);
        } else {
            actor.sendMessage(MessageUtil.plain("info_header"));
        }
    }

    public void applyStyleSetting(final CommandSender actor, final String style) {
        final String normalized = ("small_caps".equalsIgnoreCase(style) ? "small_caps" : "classic");
        synchronized (this) {
            this.getConfig().set("messages.style", normalized);
            this.saveConfig();
            MessageUtil.loadMessages(this);
        }
        
        if (actor instanceof final Player p) {
            final String ctx = this.lastMenuSent.get(p.getUniqueId().toString());
            if ("first_install".equals(ctx)) {
                this.markFirstInstallCompleted();
            }
        }
        final Component confirmation = MiniMessage.miniMessage().deserialize(
                "<#91f251>Style set to <#ffd166>" + normalized + "</#ffd166>.");
        if (actor instanceof final Player p) {
            MessageUtil.sendRaw(p, confirmation);
        } else {
            actor.sendMessage(MessageUtil.plain("info_header"));
        }
    }

    public void sendFirstInstallWelcome(final Player player) {
        if (player == null) return;
        
        if (this.getConfig().getBoolean("first_install_completed", false)) {
            return;
        }
        
        final int remaining = this.remainingFirstInstallShows.getAndUpdate(v -> Math.max(0, v - 1));
        if (remaining <= 0) {
            this.markFirstInstallCompleted();
            return;
        }

        final boolean bedrock = BedrockSupport.isBedrockPlayer(player);

        final Component header = MiniMessage.miniMessage().deserialize("<#ffcc33>Welcome to Spawn Elytra!");
        MessageUtil.sendRaw(player, header);

        this.lastMenuSent.put(player.getUniqueId().toString(), "first_install");

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

        if (remaining == 1) {
            this.markFirstInstallCompleted();
        }
    }

    public void sendSettingsMenu(final Player player) {
        if (player == null) return;

        MessageUtil.send(player, "settings_menu_header");

        final String currentLanguage = this.getConfig().getString("language", "en");
        final String currentStyle = this.getConfig().getString("messages.style", "classic");
        final String version = this.getDescription().getVersion();
        final String author = this.getDescription().getAuthors().isEmpty() ? "Unknown" : this.getDescription().getAuthors().getFirst();

        MessageUtil.send(player, "settings_current_language", Placeholder.unparsed("value", DisplayNames.language(currentLanguage)));
        MessageUtil.send(player, "settings_current_style", Placeholder.unparsed("value", this.prettyStyle(currentStyle)));

        final List<String> worlds = this.zoneService.registry().all().stream()
                .filter(com.blaxk.spawnelytra.common.zone.Zone::enabled)
                .map(com.blaxk.spawnelytra.common.zone.Zone::world).distinct().toList();
        final String activeZones = worlds.isEmpty() ? "-" : String.join(", ", worlds);
        MessageUtil.send(player, "settings_active_worlds", Placeholder.unparsed("value", activeZones));

        MessageUtil.sendRaw(player, Component.text(" "));

        this.lastMenuSent.put(player.getUniqueId().toString(), "settings");

        this.sendLanguageAndStyleChoices(player);
    }

    public void sendOptionsMenu(final Player player) {
        if (player == null) return;

        MessageUtil.send(player, "settings_menu_header");

        MessageUtil.sendRaw(player, Component.text(" "));

        this.lastMenuSent.put(player.getUniqueId().toString(), "options");

        this.sendLanguageAndStyleChoices(player);
    }

    private String prettyStyle(final String style) {
        if (style == null) return "-";
        final String lang = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        return this.localizedStyleName(style.toLowerCase(Locale.ROOT), lang);
    }

    private void sendLanguageAndStyleChoices(final Player player) {
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
        final String[] styleNames = {this.localizedStyleName("classic", currentLanguage), this.localizedStyleName("small_caps", currentLanguage) };
        
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

    private void setupMetrics(final Metrics metrics) {
        metrics.addCustomChart(new DrilldownPie("configured_language", () -> {
            String lang = this.getConfig().getString("language", "en");
            if (lang.isEmpty()) {
                lang = "en";
            }
            lang = lang.toLowerCase(Locale.ROOT);

            final Map<String, Integer> inner = new HashMap<>();
            inner.put(lang, 1);

            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            outer.put(lang, inner);
            return outer;
        }));

        metrics.addCustomChart(new DrilldownPie("worlds_configured", () -> {
            final Map<String, Integer> worldCount = new HashMap<>();
            final long worlds = this.zoneService.registry().all().stream().map(com.blaxk.spawnelytra.common.zone.Zone::world).distinct().count();
            worldCount.put(String.valueOf(worlds), 1);
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            outer.put("world_count", worldCount);
            return outer;
        }));

        metrics.addCustomChart(new DrilldownPie("activation_mode", () -> {
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            for (final com.blaxk.spawnelytra.common.zone.Zone zone : this.zoneService.registry().all()) {
                if (zone.enabled()) {
                    final String mode = zone.activationMode().id();
                    outer.computeIfAbsent(mode, k -> new HashMap<>()).merge(mode, 1, Integer::sum);
                }
            }
            return outer;
        }));

        metrics.addCustomChart(new DrilldownPie("boost_direction", () -> {
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            for (final com.blaxk.spawnelytra.common.zone.Zone zone : this.zoneService.registry().all()) {
                if (zone.enabled()) {
                    final String dir = zone.boost().direction().id();
                    outer.computeIfAbsent(dir, k -> new HashMap<>()).merge(dir, 1, Integer::sum);
                }
            }
            return outer;
        }));
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

    private boolean migrateFromOldDataFolderIfPresent() {
        try {
            final boolean preventMigration = this.getConfig().getBoolean("prevent-spawnelytra-from-migrating", false);
            if (preventMigration) {
                this.getLogger().info("Migration prevented by 'prevent-spawnelytra-from-migrating' flag.");
                return false;
            }

            final File pluginsDir = this.getDataFolder().getParentFile();
            if (pluginsDir == null) {
                return false;
            }

            final File oldDir = new File(pluginsDir, "CraftAttackSpawnElytra");
            if (!oldDir.exists() || !oldDir.isDirectory()) {
                return false;
            }

            if (this.isDirectoryEmpty(oldDir)) {
                return false;
            }

            final File migrationNotice = new File(oldDir, MIGRATION_NOTICE_FILENAME);
            if (migrationNotice.exists()) {
                this.getLogger().warning("Legacy migration skipped: " + MIGRATION_NOTICE_FILENAME + " already exists. Delete it to re-migrate.");
                return false;
            }

            final File newDir = this.getDataFolder();
            if (!newDir.exists() && !newDir.mkdirs()) {
                this.getLogger().warning("Failed to create data folder: " + newDir.getAbsolutePath());
                return false;
            }

            boolean migrated = false;

            final File backupTimestampDir = com.blaxk.spawnelytra.util.BackupUtil.backupDirectory(this, oldDir, "CraftAttackSpawnElytra");
            if (backupTimestampDir == null) {
                this.getLogger().warning("Could not backup legacy folder. Aborting migration.");
                return false;
            }

            final File oldConfig = new File(oldDir, "config.yml");
            if (oldConfig.exists()) {
                final File newConfig = new File(newDir, "config.yml");
                if (!newConfig.exists()) {
                    try {
                        java.nio.file.Files.copy(
                                oldConfig.toPath(),
                                newConfig.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        migrated = true;
                    } catch (final java.io.IOException io) {
                        this.getLogger().warning("Failed to copy config.yml from old folder: " + io.getMessage());
                    }
                }
            }

            if (new File(oldDir, "lang").exists()) {
                this.getLogger().info("Skipping legacy lang files; fresh ones will be generated.");
                migrated = true;
            }

            final boolean cleaned = this.deleteDirectoryContents(oldDir);
            if (!cleaned) {
                this.getLogger().warning("Failed to clean legacy folder after backup.");
            }
            try {
                this.writeMigrationNotice(oldDir, newDir, backupTimestampDir);
            } catch (final java.io.IOException e) {
                this.getLogger().warning("Failed to write migration notice file: " + e.getMessage());
            }

            this.getLogger().info("Migrated legacy 'CraftAttackSpawnElytra' data to '" + newDir.getName() + "'.");
            return true;
        } catch (final Exception e) {
            this.getLogger().warning("Migration from legacy data folder failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isDirectoryEmpty(final File dir) {
        if (null == dir || !dir.exists() || !dir.isDirectory()) {
            return true;
        }
        final File[] children = dir.listFiles();
        if (null == children || 0 == children.length) {
            return true;
        }
        
        if (children.length == 1 && MIGRATION_NOTICE_FILENAME.equals(children[0].getName())) {
            return true;
        }
        
        return false;
    }

    private boolean deleteDirectoryContents(final File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        final File[] children = dir.listFiles();
        boolean ok = true;
        if (children != null) {
            for (final File child : children) {
                ok &= this.deleteRecursively(child);
            }
        }
        return ok;
    }

    private boolean deleteRecursively(final File file) {
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (final File c : children) {
                    if (!this.deleteRecursively(c)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private void writeMigrationNotice(final File oldDir, final File newDataFolder, final File backupTimestampDir) throws java.io.IOException {
        if (oldDir == null || newDataFolder == null || backupTimestampDir == null) return;
        final String timestampFolderName = backupTimestampDir.getName();
        final String newFolderName = newDataFolder.getName();

        final String en = "SpawnElytra Migration Notice\r\n\r\n" +
                "All previous data from CraftAttackSpawnElytra has been migrated.\r\n" +
                "This plugin now uses the folder: plugins/" + newFolderName + "\r\n\r\n" +
                "Backup:\r\n" +
                "A full backup of the old CraftAttackSpawnElytra folder was created here:\r\n" +
                "plugins/" + newFolderName + "/backups/" + timestampFolderName + "/CraftAttackSpawnElytra/\r\n\r\n" +
                "If you have a conflicting plugin and want to prevent SpawnElytra from migrating data, add the following to plugins/" + newFolderName + "/config.yml:\r\n" +
                "prevent-spawnelytra-from-migrating: true\r\n" +
                "Note: This key is NOT present by default and must be added manually.";

        final String de = "Hinweis zur SpawnElytra-Migration\r\n\r\n" +
                "Alle bisherigen Daten von CraftAttackSpawnElytra wurden migriert.\r\n" +
                "Das Plugin verwendet jetzt den Ordner: plugins/" + newFolderName + "\r\n\r\n" +
                "Backup:\r\n" +
                "Eine vollständige Sicherung des alten CraftAttackSpawnElytra-Ordners wurde hier erstellt:\r\n" +
                "plugins/" + newFolderName + "/backups/" + timestampFolderName + "/CraftAttackSpawnElytra/\r\n\r\n" +
                "Wenn du ein anderes, in Konflikt stehendes Plugin hast und die Migration durch SpawnElytra verhindern möchtest, füge Folgendes in plugins/" + newFolderName + "/config.yml hinzu:\r\n" +
                "prevent-spawnelytra-from-migrating: true\r\n" +
                "Hinweis: Dieser Schlüssel ist standardmäßig nicht in der config.yml vorhanden und muss manuell hinzugefügt werden.";

        final String pl = "Informacje na temat migracji SpawnElytra\r\n\r\n" +
                "Wszystkie poprzednie dane z pluginu CraftAttackSpawnElytra zostały zmigrowane.\r\n" +
                "Ten plugin używa folderu: plugins/" + newFolderName + "\r\n\r\n" +
                "Kopia zapasowa:\r\n" +
                "Pełna kopia zapasowa starego folderu CraftAttackSpawnElytra została stworzona tutaj:\r\n" +
                "plugins/" + newFolderName + "/backups/" + timestampFolderName + "/CraftAttackSpawnElytra/\r\n\r\n" +
                "Jeśli masz plugin, który jest niekompatybilny i nie chcesz migrować danych SpawnElytra, dodaj poniższą opcję do plugins/" + newFolderName + "/config.yml:\r\n" +
                "prevent-spawnelytra-from-migrating: true\r\n" +
                "Uwaga: Ta opcja domyślnie nie jest obecna w pliku konfiguracyjnym i musi zostać dodana ręcznie.";

        final String content = en + "\r\n\r\n" + de + "\r\n\r\n" + pl + "\r\n";
        java.nio.file.Files.writeString(new File(oldDir, MIGRATION_NOTICE_FILENAME).toPath(), content);
    }

    private void saveLanguageFiles() {
        // Synchronous on purpose: LanguageUpdater and MessageUtil read/write the same files right after this
        // call; the former async version raced them (mkdirs failure on first boot, half-written files).
        final File langDir = new File(this.getDataFolder(), "lang");
        if (!langDir.mkdirs() && !langDir.isDirectory()) {
            this.getLogger().warning("Failed to create language directory");
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
                java.nio.file.Files.write(langFile.toPath(), in.readAllBytes());
            } catch (final IOException e) {
                this.getLogger().warning("Failed to write language file " + lang + ".yml: " + e.getMessage());
            }
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public TempElytraManager getTempElytraManager() {
        return this.tempElytraManager;
    }

    public synchronized void reload() {
        this.reloadConfig();
        MessageUtil.loadMessages(this);
        BedrockSupport.reloadSettings(this);

        if (tempElytraManager != null) {
            this.tempElytraManager.restoreAll();
        }
        if (this.editorManager != null) {
            this.editorManager.stopAll(false);
        }
        this.cleanupFlightForOnlinePlayers();
        this.zoneService.load();
        this.registerTierPermissions();
    }

    private class VersionChecker {
        void start() {
            Main.this.versionCheckTask = SchedulerUtil.runAsyncRepeating(Main.this, this::tick, 20L * 60, 20L * 60 * 60 * 4);
        }

        private void tick() {
            try {
                final String latest = Main.this.fetchLatestVersionNumber();
                if (Main.compareVersions(latest, Main.CURRENT_VERSION) > 0) {
                    Main.this.latestVersion = latest;
                    Main.this.updateAvailable = true;

                    Bukkit.getOnlinePlayers().stream()
                            .filter(Player::isOp)
                            .forEach(p -> SchedulerUtil.runAtEntityNow(Main.this, p, () -> Main.this.sendUpdateNotification(p)));

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

                    Main.this.getLogger().warning(newVersionText + " " + updateToVersionText);
                    Main.this.getLogger().warning("Download: " + Main.this.buildUpdateLink());
                } else {
                    Main.this.updateAvailable = false;
                    Main.this.latestVersion = null;
                }
            } catch (final Exception e) {
                final String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                final String sanitized = Main.this.sanitizeMiniMessageValue(errorMessage);
                Main.this.getLogger().warning(MessageUtil.plain("failed_update_check",
                        Placeholder.unparsed("error_message", sanitized)));
            }
        }
    }

    /** Numeric comparison of dotted versions ("1.10" > "1.9"); non-numeric parts compare as 0. */
    static int compareVersions(final String a, final String b) {
        final String[] pa = a == null ? new String[0] : a.split("[.+-]");
        final String[] pb = b == null ? new String[0] : b.split("[.+-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            final int x = i < pa.length ? Main.parseVersionPart(pa[i]) : 0;
            final int y = i < pb.length ? Main.parseVersionPart(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parseVersionPart(final String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private String sanitizeMiniMessageValue(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace('<', '[').replace('>', ']');
    }

    private String fetchLatestVersionNumber() throws IOException {
        final URL url = new URL("https://api.modrinth.com/v2/project/" + Main.MODRINTH_PROJECT_ID + "/version");
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SpawnElytra/" + Main.CURRENT_VERSION);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try {
            final int status = conn.getResponseCode();
            if (HttpURLConnection.HTTP_OK != status) {
                throw new IOException("HTTP " + status + " " + conn.getResponseMessage());
            }

            try (final InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                final JsonElement root = JsonParser.parseReader(reader);

                return Main.getString(root);
            }
        } finally {
            conn.disconnect();
        }
    }

    static @NotNull String getString(final JsonElement root) throws IOException {
        if (!root.isJsonArray()) {
            throw new IOException("Unexpected response from Modrinth");
        }

        final JsonArray versions = root.getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IOException("No version data available");
        }

        String latestRelease = null;

        for (final JsonElement elem : versions) {
            final JsonObject obj = elem.getAsJsonObject();
            final String type = obj.get("version_type").getAsString();
            if ("release".equalsIgnoreCase(type)) {
                latestRelease = obj.get("version_number").getAsString();
                break;
            }
        }

        if (latestRelease == null) {
            throw new IOException("No release versions found");
        }
        return latestRelease;
    }

}
