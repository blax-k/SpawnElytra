package com.blaxk.spawnelytra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.UpdateUtil;
import com.blaxk.spawnelytra.command.CommandHandler;
import com.blaxk.spawnelytra.config.ConfigUpdater;
import com.blaxk.spawnelytra.config.LanguageUpdater;
import com.blaxk.spawnelytra.listener.SpawnElytra;
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.integration.PlaceholderAPIIntegration;
import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin implements Listener {
    public static Main plugin;
    private static final String CURRENT_VERSION = "1.4";
    private static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private static final String MIGRATION_NOTICE_FILENAME = "MIGRATED_TO_SPAWN_ELYTRA.txt";

private PlayerDataManager playerDataManager;
    private final Map<String, SpawnElytra> worldInstances = new HashMap<>();
    private final Map<String, String> lastMenuSent = new HashMap<>();
    private int remainingFirstInstallShows = 5; 

    private com.blaxk.spawnelytra.setup.SetupManager setupManager;

    private String latestVersion;
    private boolean updateAvailable;
    private SchedulerUtil.TaskHandle versionCheckTask;

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
        ConfigUpdater.updateConfig(this);
        
        if (migrated) {
            this.getConfig().set("first_install_completed", true);
            this.saveConfig();
        }
        
        this.saveLanguageFiles();
        LanguageUpdater.updateLanguages(this);
        MessageUtil.loadMessages(this);
        this.loadWorldConfigurations();
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
        final Metrics metrics = new Metrics(this, pluginId);
        this.setupMetrics(metrics);
    }

    private void registerListenersAndCommands() {
        Bukkit.getPluginManager().registerEvents(this, this);

        this.setupManager = new com.blaxk.spawnelytra.setup.SetupManager(this);
        Bukkit.getPluginManager().registerEvents(this.setupManager, this);

        final CommandHandler commandHandler = new CommandHandler(this);
        Objects.requireNonNull(this.getCommand("spawnelytra")).setExecutor(commandHandler);
        Objects.requireNonNull(this.getCommand("spawnelytra")).setTabCompleter(commandHandler);
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
        if (playerDataManager != null) {
            this.playerDataManager.saveAllPlayerData();
        }

if (versionCheckTask != null) {
    this.versionCheckTask.cancel();
    this.versionCheckTask = null;
        }

        if (setupManager != null) {
            this.setupManager.stopAll();
        }

        for (final SpawnElytra instance : this.worldInstances.values()) {
            if (instance != null) {
                for (final Player player : Bukkit.getOnlinePlayers()) {
                    instance.stopVisualization(player);
                }
            }
        }
        this.worldInstances.clear();

        MessageUtil.shutdown();
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

        this.lastMenuSent.remove(event.getPlayer().getUniqueId().toString());
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
        MessageUtil.sendRaw(recipient, this.getDownloadButtonsMessage(language, this.latestVersion));
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
            default -> "<#5db3ff>A new version of Spawn Elytra is available!";
        };
        return MiniMessage.miniMessage().deserialize(text);
    }
    
    private Component getUpdateToVersionMessage(final String language, final String latestVersion) {
        final String text = switch (language) {
            case "de" -> "<#fdba5e>Bitte aktualisiere auf Version <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(aktuell: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            case "es" -> "<#fdba5e>Por favor, actualiza a la versión <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(actual: <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
            case "fr" -> "<#fdba5e>Veuillez mettre à jour vers la version <#91f251>" + latestVersion + "</#91f251> <#aaa8a8>(actuelle : <#fd5e5e>" + CURRENT_VERSION + "</#fd5e5e>)</#aaa8a8>";
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
        
        final String language = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        
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
            default -> "Unknown error";
        };
        
        final String text = switch (messageKey) {
            case "update_no_version_available" -> switch (language) {
                case "de" -> "<#fd5e5e>Keine neue Version verfügbar zum Aktualisieren.";
                case "es" -> "<#fd5e5e>No hay una nueva versión disponible para actualizar.";
                case "fr" -> "<#fd5e5e>Aucune nouvelle version disponible pour la mise à jour.";
                default -> "<#fd5e5e>No new version available to update.";
            };
            case "update_starting" -> switch (language) {
                case "de" -> "<#91f251>Starte automatisches Update auf Version <#ffd166>" + version + "</#ffd166>...";
                case "es" -> "<#91f251>Iniciando actualización automática a la versión <#ffd166>" + version + "</#ffd166>...";
                case "fr" -> "<#91f251>Démarrage de la mise à jour automatique vers la version <#ffd166>" + version + "</#ffd166>...";
                default -> "<#91f251>Starting automatic update to version <#ffd166>" + version + "</#ffd166>...";
            };
            case "update_success" -> switch (language) {
                case "de" -> "<#91f251>✔ Update erfolgreich heruntergeladen und installiert!\n<#ffd166>⚠ Bitte starte den Server neu, um Version <#91f251>" + version + "</#91f251> zu laden.";
                case "es" -> "<#91f251>✔ ¡Actualización descargada e instalada con éxito!\n<#ffd166>⚠ Por favor, reinicia el servidor para cargar la versión <#91f251>" + version + "</#91f251>.";
                case "fr" -> "<#91f251>✔ Mise à jour téléchargée et installée avec succès !\n<#ffd166>⚠ Veuillez redémarrer le serveur pour charger la version <#91f251>" + version + "</#91f251>.";
                default -> "<#91f251>✔ Update downloaded and installed successfully!\n<#ffd166>⚠ Please restart the server to load version <#91f251>" + version + "</#91f251>.";
            };
            case "update_failed" -> switch (language) {
                case "de" -> "<#fd5e5e>✗ Automatisches Update fehlgeschlagen. Bitte aktualisiere manuell.";
                case "es" -> "<#fd5e5e>✗ La actualización automática falló. Por favor, actualiza manualmente.";
                case "fr" -> "<#fd5e5e>✗ La mise à jour automatique a échoué. Veuillez mettre à jour manuellement.";
                default -> "<#fd5e5e>✗ Automatic update failed. Please update manually.";
            };
            case "update_error" -> switch (language) {
                case "de" -> "<#fd5e5e>✗ Fehler beim Update: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                case "es" -> "<#fd5e5e>✗ Error durante la actualización: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                case "fr" -> "<#fd5e5e>✗ Erreur lors de la mise à jour : <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
                default -> "<#fd5e5e>✗ Update error: <#aaa8a8>" + (errorMsg.isEmpty() ? unknownError : errorMsg);
            };
            default -> "<#aaa8a8>Unknown message: " + messageKey;
        };
        
        return MiniMessage.miniMessage().deserialize(text);
    }

    private Component getSettingsPluginInfoMessage(final String language, final String version, final String author) {
        final String text = switch (language) {
            case "de" -> "<#fdba5e>Plugin-Version: <#91f251>" + version + "</#91f251> | Autor: <#91f251>" + author + "</#91f251>";
            case "es" -> "<#fdba5e>Versión del plugin: <#91f251>" + version + "</#91f251> | Autor: <#91f251>" + author + "</#91f251>";
            case "fr" -> "<#fdba5e>Version du plugin : <#91f251>" + version + "</#91f251> | Auteur : <#91f251>" + author + "</#91f251>";
            default -> "<#fdba5e>Plugin Version: <#91f251>" + version + "</#91f251> | Author: <#91f251>" + author + "</#91f251>";
        };
        return MiniMessage.miniMessage().deserialize(text);
    }

    public SpawnElytra getSpawnElytraInstance() {
        if (this.worldInstances.isEmpty()) {
            return null;
        }
        return this.worldInstances.values().iterator().next();
    }
    
    public void markFirstInstallCompleted() {
        if (!this.getConfig().getBoolean("first_install_completed", false)) {
            this.getConfig().set("first_install_completed", true);
            this.saveConfig();
        }
    }
    
    public SpawnElytra getSpawnElytraInstance(final String worldName) {
        return this.worldInstances.get(worldName);
    }
    
    public Map<String, SpawnElytra> getAllWorldInstances() {
        return new HashMap<>(this.worldInstances);
    }

    public void applyLanguageSetting(final CommandSender actor, final String langCode) {
        this.getConfig().set("language", langCode.toLowerCase(Locale.ROOT));
        this.saveConfig();
        MessageUtil.loadMessages(this);
        
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
        this.getConfig().set("messages.style", normalized);
        this.saveConfig();
        MessageUtil.loadMessages(this);
        
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
        
        if (remainingFirstInstallShows <= 0) {
            this.markFirstInstallCompleted();
            return;
        }

    this.remainingFirstInstallShows--;

        final Component header = MiniMessage.miniMessage().deserialize("<#ffcc33>Welcome to Spawn Elytra!");
        MessageUtil.sendRaw(player, header);
    
    this.lastMenuSent.put(player.getUniqueId().toString(), "first_install");
    
        final Component languagePrompt = MiniMessage.miniMessage().deserialize("<#fdba5e>✎ Choose your preferred language below:");
        MessageUtil.sendRaw(player, languagePrompt);
        
        final Component languageButtons = MiniMessage.miniMessage().deserialize(
                "   <#91f251>[<click:run_command:'/spawnelytra set language en'><hover:show_text:'<#91f251>Set language to English'>English</hover></click>] " +
                        "[<click:run_command:'/spawnelytra set language de'><hover:show_text:'<#91f251>Set language to German'>Deutsch</hover></click>] " +
                        "[<click:run_command:'/spawnelytra set language es'><hover:show_text:'<#91f251>Set language to Spanish'>Español</hover></click>] " +
                        "[<click:run_command:'/spawnelytra set language fr'><hover:show_text:'<#91f251>Set language to French'>Français</hover></click>]"
        );
        MessageUtil.sendRaw(player, languageButtons);
        
        final Component setupPrompt = MiniMessage.miniMessage().deserialize("<#fdba5e>⚐ Set up the spawn area where the Elytra spawn will work:");
        MessageUtil.sendRaw(player, setupPrompt);
        
        final Component setupButton = MiniMessage.miniMessage().deserialize(
                "   <#91f251>[<click:run_command:'/spawnelytra setup'><hover:show_text:'<#91f251>Currently, the Elytra spawn works 100 blocks in every direction from the world spawn point.\nTo define a specific area, you can either use the Setup Help or configure it yourself in config.yml.'>Start Setup</hover></click>]");
        MessageUtil.sendRaw(player, setupButton);
        
        final Component dismiss = MiniMessage.miniMessage().deserialize(
                "<#aaa8a8>[<click:run_command:'/spawnelytra dismiss'><hover:show_text:'<#aaa8a8>Hide this message forever'>Dismiss this message</hover></click>]");
        MessageUtil.sendRaw(player, dismiss);

        
        if (remainingFirstInstallShows == 0) {
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
        
        
        MessageUtil.send(player, "settings_current_language", Placeholder.unparsed("value", this.prettyLanguage(currentLanguage)));
        MessageUtil.send(player, "settings_current_style", Placeholder.unparsed("value", this.prettyStyle(currentStyle)));
        
        
        final String activeWorlds = this.worldInstances.isEmpty() ? "-" : String.join(", ", this.worldInstances.keySet());
        MessageUtil.send(player, "settings_active_worlds", Placeholder.unparsed("value", activeWorlds));
        
        
        final Component pluginInfo = this.getSettingsPluginInfoMessage(this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT), version, author);
        MessageUtil.sendRaw(player, Component.text(" "));
        
        
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

    private String prettyLanguage(final String lang) {
        if (lang == null) return "-";
        return switch (lang.toLowerCase(Locale.ROOT)) {
            case "de" -> "Deutsch";
            case "en" -> "English";
            case "es" -> "Español";
            case "fr" -> "Français";
            default -> lang;
        };
    }
    
    private String prettyStyle(final String style) {
        if (style == null) return "-";
        final String lang = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        return this.localizedStyleName(style.toLowerCase(Locale.ROOT), lang);
    }

    private void sendLanguageAndStyleChoices(final Player player) {
        final String currentLanguage = this.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        final String currentStyle = this.getConfig().getString("messages.style", "classic").toLowerCase(Locale.ROOT);
        
        MessageUtil.send(player, "settings_change_language");
        
        final StringBuilder langBuilder = new StringBuilder("<#91f251>");
        final String[] languages = {"de", "en", "es", "fr"};
        final String[] langNames = {"Deutsch", "English", "Español", "Français"};
        
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

    private void loadWorldConfigurations() {
        final ConfigurationSection worldsSection = this.getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            this.getLogger().warning("No worlds configuration found! Creating default for 'world'...");
            this.createDefaultWorldConfig();
            return;
        }

        for (final String worldName : worldsSection.getKeys(false)) {
            final ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);
            if (worldConfig != null && worldConfig.getBoolean("enabled", true)) {
                if (this.validateWorldConfiguration(worldName, worldConfig)) {
                    final World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        final SpawnElytra instance = new SpawnElytra(this, worldName, worldConfig);
                        this.worldInstances.put(worldName, instance);
                        Bukkit.getPluginManager().registerEvents(instance, this);
                    } else {
                        this.getLogger().warning("World '" + worldName + "' not found, skipping Spawn Elytra configuration");
                    }
                }
            }
        }

        if (this.worldInstances.isEmpty()) {
            this.getLogger().warning("No valid worlds configured for Spawn Elytra!");
        }
    }
    
    private boolean validateWorldConfiguration(final String worldName, final ConfigurationSection worldConfig) {
        boolean valid = true;
        
        final int radius = worldConfig.getInt("radius", 100);
        if (radius <= 0) {
            this.getLogger().warning("Invalid radius (" + radius + ") for world '" + worldName + "'. Must be > 0. Using default: 100");
            worldConfig.set("radius", 100);
            valid = false;
        }
        
        final int boostStrength = worldConfig.getInt("boost.strength", 2);
        if (boostStrength <= 0) {
            this.getLogger().warning("Invalid boost strength (" + boostStrength + ") for world '" + worldName + "'. Must be > 0. Using default: 2");
            worldConfig.set("boost.strength", 2);
            valid = false;
        }
        
        final String activationMode = worldConfig.getString("activation_mode", "double_jump");
        final List<String> validModes = Arrays.asList("double_jump", "auto", "sneak_jump", "f_key");
        if (!validModes.contains(activationMode)) {
            this.getLogger().warning("Invalid activation mode ('" + activationMode + "') for world '" + worldName + "'. Using default: double_jump");
            worldConfig.set("activation_mode", "double_jump");
            valid = false;
        }
        
        final String boostDirection = worldConfig.getString("boost.direction", "forward");
        if (!"forward".equals(boostDirection) && !"upward".equals(boostDirection)) {
            this.getLogger().warning("Invalid boost direction ('" + boostDirection + "') for world '" + worldName + "'. Using default: forward");
            worldConfig.set("boost.direction", "forward");
            valid = false;
        }
        
        try {
            final String soundName = worldConfig.getString("boost.sound", "ENTITY_BAT_TAKEOFF");
            Sound.valueOf(soundName.toUpperCase());
        } catch (final IllegalArgumentException e) {
            this.getLogger().warning("Invalid boost sound for world '" + worldName + "'. Using default: ENTITY_BAT_TAKEOFF");
            worldConfig.set("boost.sound", "ENTITY_BAT_TAKEOFF");
            valid = false;
        }
        
        final double fKeyLaunchStrength = worldConfig.getDouble("f_key.launch_strength", 1.5);
        if (fKeyLaunchStrength <= 0) {
            this.getLogger().warning("Invalid F-key launch strength (" + fKeyLaunchStrength + ") for world '" + worldName + "'. Must be > 0. Using default: 1.5");
            worldConfig.set("f_key.launch_strength", 1.5);
            valid = false;
        }
        
        if (!valid) {
            this.saveConfig();
        }
        
        return true;
    }
    
    private void createDefaultWorldConfig() {
        this.getConfig().set("worlds." + "world" + ".enabled", true);
        this.getConfig().set("worlds." + "world" + ".activation_mode", "double_jump");
        this.getConfig().set("worlds." + "world" + ".radius", 100);
        this.getConfig().set("worlds." + "world" + ".spawn_area.mode", "auto");
        this.getConfig().set("worlds." + "world" + ".spawn_area.area_type", "circular");
        this.getConfig().set("worlds." + "world" + ".spawn_area.x", 0);
        this.getConfig().set("worlds." + "world" + ".spawn_area.y", 64);
        this.getConfig().set("worlds." + "world" + ".spawn_area.z", 0);
        this.getConfig().set("worlds." + "world" + ".spawn_area.x2", 0);
        this.getConfig().set("worlds." + "world" + ".spawn_area.y2", 0);
        this.getConfig().set("worlds." + "world" + ".spawn_area.z2", 0);
        this.getConfig().set("worlds." + "world" + ".boost.enabled", true);
        this.getConfig().set("worlds." + "world" + ".boost.strength", 2);
        this.getConfig().set("worlds." + "world" + ".boost.direction", "forward");
        this.getConfig().set("worlds." + "world" + ".boost.sound", "ENTITY_BAT_TAKEOFF");
        this.getConfig().set("worlds." + "world" + ".f_key.launch_strength", 1.5);
        this.saveConfig();
        this.reloadConfig();
        this.loadWorldConfigurations();
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
            worldCount.put(String.valueOf(this.worldInstances.size()), 1);
            
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            outer.put("world_count", worldCount);
            return outer;
        }));

        metrics.addCustomChart(new DrilldownPie("activation_mode", () -> {
            final Map<String, Integer> modeCounts = new HashMap<>();
            
            final ConfigurationSection worldsSection = this.getConfig().getConfigurationSection("worlds");
            if (worldsSection != null) {
                for (final String worldName : worldsSection.getKeys(false)) {
                    final ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);
                    if (worldConfig != null && worldConfig.getBoolean("enabled", true)) {
                        String mode = worldConfig.getString("activation_mode", "double_jump");
                        modeCounts.put(mode, modeCounts.getOrDefault(mode, 0) + 1);
                    }
                }
            }
            
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            for (final Map.Entry<String, Integer> entry : modeCounts.entrySet()) {
                final Map<String, Integer> inner = new HashMap<>();
                inner.put(entry.getKey(), entry.getValue());
                outer.put(entry.getKey(), inner);
            }
            
            return outer;
        }));

        metrics.addCustomChart(new DrilldownPie("boost_direction", () -> {
            final Map<String, Integer> directionCounts = new HashMap<>();
            
            final ConfigurationSection worldsSection = this.getConfig().getConfigurationSection("worlds");
            if (worldsSection != null) {
                for (final String worldName : worldsSection.getKeys(false)) {
                    final ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);
                    if (worldConfig != null && worldConfig.getBoolean("enabled", true)) {
                        final ConfigurationSection boostSection = worldConfig.getConfigurationSection("boost");
                        if (boostSection != null) {
                            String direction = boostSection.getString("direction", "forward");
                            directionCounts.put(direction, directionCounts.getOrDefault(direction, 0) + 1);
                        }
                    }
                }
            }
            
            final Map<String, Map<String, Integer>> outer = new HashMap<>();
            for (final Map.Entry<String, Integer> entry : directionCounts.entrySet()) {
                final Map<String, Integer> inner = new HashMap<>();
                inner.put(entry.getKey(), entry.getValue());
                outer.put(entry.getKey(), inner);
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
                this.getLogger().info("Migration prevented by config flag 'prevent-spawnelytra-from-migrating'.");
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
                this.getLogger().warning("Found " + MIGRATION_NOTICE_FILENAME + " in legacy folder, but directory contains other files.");
                this.getLogger().warning("Skipping migration. If you need to re-migrate, delete the " + MIGRATION_NOTICE_FILENAME + " file.");
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
                this.getLogger().warning("Could not create backup of legacy folder. Aborting migration to avoid data loss.");
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
                this.getLogger().info("Skipping legacy language files migration: fresh lang files will be generated.");
                migrated = true;
            }

            final boolean cleaned = this.deleteDirectoryContents(oldDir);
            if (!cleaned) {
                this.getLogger().warning("Failed to clean legacy folder 'CraftAttackSpawnElytra' after backup. Some files may remain.");
            }
            try {
                this.writeMigrationNotice(oldDir, newDir, backupTimestampDir);
            } catch (final java.io.IOException e) {
                this.getLogger().warning("Failed to write migration notice file: " + e.getMessage());
            }

            this.getLogger().info("Detected legacy folder 'CraftAttackSpawnElytra'. Backed up all files and migrated essentials to '" + newDir.getName() + "'.");
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

        final String content = en + "\r\n\r\n" + de + "\r\n";
        java.nio.file.Files.writeString(new File(oldDir, MIGRATION_NOTICE_FILENAME).toPath(), content);
    }

    private void saveLanguageFiles() {
        SchedulerUtil.runAsync(this, () -> {
            final File langDir = new File(this.getDataFolder(), "lang");
            if (!langDir.exists() && !langDir.mkdirs()) {
                this.getLogger().warning("Failed to create language directory");
                return;
            }

            final String[] languages = {"en", "de", "es", "fr"};
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
        });
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public com.blaxk.spawnelytra.setup.SetupManager getSetupManager() {
        return this.setupManager;
    }

    public void reload() {
        this.reloadConfig();
        MessageUtil.loadMessages(this);

        if (setupManager != null) {
            this.setupManager.stopAll();
        }

        for (final SpawnElytra instance : this.worldInstances.values()) {
            if (instance != null) {
                for (final Player player : Bukkit.getOnlinePlayers()) {
                    instance.stopVisualization(player);
                }
            }
        }
        this.worldInstances.clear();

        this.loadWorldConfigurations();
    }

    private class VersionChecker {
        void start() {
            Main.this.versionCheckTask = SchedulerUtil.runAsyncRepeating(Main.this, this::tick, 20L * 60, 20L * 60 * 60 * 4);
        }

        private void tick() {
            try {
                final String latest = Main.this.fetchLatestVersionNumber();
                if (!Main.CURRENT_VERSION.equals(latest)) {
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
                        default -> "A new version of Spawn Elytra is available!";
                    };
                    
                    final String updateToVersionText = switch (language) {
                        case "de" -> "Bitte aktualisiere auf Version " + Main.this.latestVersion + " (aktuell: " + Main.CURRENT_VERSION + ")";
                        case "es" -> "Por favor, actualiza a la versión " + Main.this.latestVersion + " (actual: " + Main.CURRENT_VERSION + ")";
                        case "fr" -> "Veuillez mettre à jour vers la version " + Main.this.latestVersion + " (actuelle : " + Main.CURRENT_VERSION + ")";
                        default -> "Please update to version " + Main.this.latestVersion + " (current: " + Main.CURRENT_VERSION + ")";
                    };

                    Main.this.getLogger().warning(newVersionText);
                    Main.this.getLogger().warning(updateToVersionText);
                    Main.this.getLogger().warning("Modrinth: " + Main.this.buildUpdateLink());
                    Main.this.getLogger().warning("GitHub: https://github.com/blax-k/SpawnElytra/releases");
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

    private static @NotNull String getString(final JsonElement root) throws IOException {
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
            throw new IOException("No release versions found. huh?");
        }
        return latestRelease;
    }

}

