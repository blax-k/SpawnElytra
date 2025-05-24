package at.blaxk.spawnelytra;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Main extends JavaPlugin {
    public static Main plugin;
    public static int radius;
    public static int strength;
    public static String world;
    public static boolean ignoreyLevel;
    public static int spawnx;
    public static int spawny;
    public static int spawnz;
    public static boolean disableInCreative;
    private static final String CURRENT_VERSION = "1.2.1";
    private static final String MODRINTH_PROJECT_ID = "Egw2R8Fj";
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        playerDataManager = new PlayerDataManager(this);
        playerDataManager.initialize();
        MessageUtils.initialize(this);
        ConfigUpdater.updateConfig(this);
        saveLanguageFiles();
        MessageUtils.loadMessages(this);
        loadConfig();
        int pluginId = 25081;
        Metrics metrics = new Metrics(this, pluginId);
        Bukkit.getPluginManager().registerEvents(new SpawnElytra(this), this);

        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("spawnelytra").setExecutor(commandHandler);
        getCommand("spawnelytra").setTabCompleter(commandHandler);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boolean registered = new SpawnElytraExpansion(this, playerDataManager).register();
            if (!registered) {
                getLogger().warning("Failed to register SpawnElytra placeholders");
            }
        }

        new VersionChecker().runTaskTimerAsynchronously(this, 20L * 30, 20L * 60 * 60); // Check after 30 seconds and every hour

        getLogger().info("Spawn Elytra Plugin v" + CURRENT_VERSION + " enabled");
        getLogger().info("Plugin by Blaxk_");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }

        getLogger().info("Spawn Elytra Plugin disabled");
    }

    private void loadConfig() {
        radius = getConfig().getInt("radius");
        strength = getConfig().getInt("strength");
        world = getConfig().getString("world");
        disableInCreative = getConfig().getBoolean("disable_in_creative", true);

        if (getConfig().getString("mode", "auto").equalsIgnoreCase("advanced")) {
            if (getConfig().contains("spawn.x")) {
                // New format
                spawnx = getConfig().getInt("spawn.x");
                spawny = getConfig().getInt("spawn.y");
                spawnz = getConfig().getInt("spawn.z");
            } else {
                // Old format for backward compatibility
                spawnx = getConfig().getInt("spawnx");
                spawny = getConfig().getInt("spawny");
                spawnz = getConfig().getInt("spawnz");
            }
        }
    }

    private void saveLanguageFiles() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        String[] languages = {"en", "de", "es", "fr", "hi", "zh", "ar"};

        for (String lang : languages) {
            File langFile = new File(langDir, lang + ".yml");

            if (!langFile.exists()) {
                try {
                    String content = getDefaultLanguageContent(lang);

                    java.nio.file.Files.writeString(langFile.toPath(), content);
                    getLogger().info("Created language file: " + lang + ".yml");
                } catch (IOException e) {
                    getLogger().warning("Failed to create language file " + lang + ".yml: " + e.getMessage());
                }
            }
        }
    }

    private String getDefaultLanguageContent(String lang) {
        switch (lang) {
            case "en":
                return "# English language file\n" +
                        "press_to_boost: \"&6Press {key} &6to boost yourself\"\n" +
                        "boost_activated: \"&a&lBoost activated!\"\n" +
                        "new_version_available: \"&bA new version of Spawn Elytra is available!\"\n" +
                        "update_to_version: \"&ePlease update to version &a{latestVersion} &7(current: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bDownload link:\"\n" +
                        "failed_update_check: \"&cFailed to check for updates: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eElytra flight disabled in Creative mode.\"";
            case "de":
                return "# German language file\n" +
                        "press_to_boost: \"&6Drücke {key} &6um dich zu boosten\"\n" +
                        "boost_activated: \"&a&lBoost aktiviert!\"\n" +
                        "new_version_available: \"&bEine neue Version von Spawn Elytra ist verfügbar!\"\n" +
                        "update_to_version: \"&eBitte aktualisiere auf Version &a{latestVersion} &7(aktuell: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bDownload-Link:\"\n" +
                        "failed_update_check: \"&cFehler beim Überprüfen auf Updates: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eElytra-Flug im Kreativmodus deaktiviert.\"";
            case "es":
                return "# Spanish language file\n" +
                        "press_to_boost: \"&6Presiona {key} &6para impulsarte.\"\n" +
                        "boost_activated: \"&a&l¡Impulso activado!\"\n" +
                        "new_version_available: \"&b¡Una nueva versión de Spawn Elytra está disponible!\"\n" +
                        "update_to_version: \"&ePor favor, actualiza a la versión &a{latestVersion} &7(actual: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bEnlace de descarga:\"\n" +
                        "failed_update_check: \"&cError al comprobar actualizaciones: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eVuelo con elytra desactivado en modo Creativo.\"";
            case "fr":
                return "# French language file\n" +
                        "press_to_boost: \"&6Appuyez sur {key} &6pour vous propulser.\"\n" +
                        "boost_activated: \"&a&lPropulsion activée !\"\n" +
                        "new_version_available: \"&bUne nouvelle version de Spawn Elytra est disponible !\"\n" +
                        "update_to_version: \"&eVeuillez mettre à jour vers la version &a{latestVersion} &7(actuelle : &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bLien de téléchargement :\"\n" +
                        "failed_update_check: \"&cÉchec de la vérification des mises à jour : {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eVol en élytre désactivé en mode Créatif.\"";
            case "hi":
                return "# Hindi language file\n" +
                        "press_to_boost: \"&6दबाएं {key} &6खुद को बूस्ट करने के लिए।\"\n" +
                        "boost_activated: \"&a&lबूस्ट सक्रिय!\"\n" +
                        "new_version_available: \"&bस्पॉन एलिट्रा का एक नया संस्करण उपलब्ध है!\"\n" +
                        "update_to_version: \"&eकृपया संस्करण &a{latestVersion} &7पर अपडेट करें (वर्तमान: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bडाउनलोड लिंक:\"\n" +
                        "failed_update_check: \"&cअपडेट की जांच करने में विफल: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eक्रिएटिव मोड में एलिट्रा उड़ान अक्षम।\"";
            case "zh":
                return "# Chinese language file\n" +
                        "press_to_boost: \"&6按下 {key} &6来加速自己。\"\n" +
                        "boost_activated: \"&a&l加速已激活！\"\n" +
                        "new_version_available: \"&bSpawn Elytra 插件有新版本可用！\"\n" +
                        "update_to_version: \"&e请更新到版本 &a{latestVersion} &7(当前版本: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&b下载链接:\"\n" +
                        "failed_update_check: \"&c检查更新失败: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&e创造模式下已禁用鞘翅飞行。\"";
            case "ar":
                return "# Arabic language file\n" +
                        "press_to_boost: \"&6اضغط {key} &6للانطلاق.\"\n" +
                        "boost_activated: \"&a&lتم تنشيط الدفع!\"\n" +
                        "new_version_available: \"&bيتوفر إصدار جديد من إضافة سبون إليترا!\"\n" +
                        "update_to_version: \"&eيرجى التحديث إلى الإصدار &a{latestVersion} &7(الإصدار الحالي: &c{currentVersion}&7)\"\n" +
                        "download_link: \"&bرابط التحميل:\"\n" +
                        "failed_update_check: \"&cفشل التحقق من التحديثات: {errorMessage}\"\n" +
                        "creative_mode_elytra_disabled: \"&eتم تعطيل طيران الإليترا في وضع الإبداع.\"";
            default:
                return getDefaultLanguageContent("en");
        }
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public void reload() {
        reloadConfig();
        loadConfig();
        MessageUtils.loadMessages(this);
        getLogger().info("Spawn Elytra configuration reloaded");
    }

    private class VersionChecker extends BukkitRunnable {
        @Override
        public void run() {
            try {
                String latestVersion = getLatestVersion();
                if (!CURRENT_VERSION.equals(latestVersion)) {
                    String updateLink = "https://modrinth.com/plugin/spawn-elytra/version/" + latestVersion;
                    Bukkit.getOnlinePlayers().stream()
                            .filter(Player::isOp)
                            .forEach(player -> {
                                player.sendMessage(ChatColor.YELLOW + MessageUtils.getMessage("new_version_available"));
                                player.sendMessage(ChatColor.YELLOW + MessageUtils.getMessage("update_to_version")
                                        .replace("{latestVersion}", latestVersion)
                                        .replace("{currentVersion}", CURRENT_VERSION));
                                player.sendMessage(ChatColor.GREEN + MessageUtils.getMessage("download_link"));
                                player.sendMessage(ChatColor.GREEN + updateLink);
                            });
                    getLogger().warning(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', MessageUtils.getMessage("new_version_available"))));
                    getLogger().warning(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', MessageUtils.getMessage("update_to_version")
                            .replace("{latestVersion}", latestVersion)
                            .replace("{currentVersion}", CURRENT_VERSION))));
                    getLogger().warning(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', MessageUtils.getMessage("download_link"))) + " " + updateLink);
                }
            } catch (Exception e) {
                getLogger().warning(MessageUtils.getMessage("failed_update_check").replace("{errorMessage}", e.getMessage()));
            }
        }
    }

    private String getLatestVersion() throws Exception {
        URL url = new URL("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();

        String jsonResponse = content.toString();
        String version = jsonResponse.substring(jsonResponse.indexOf("version_number") + 16);
        return version.substring(1, version.indexOf(",") - 1);
    }
}