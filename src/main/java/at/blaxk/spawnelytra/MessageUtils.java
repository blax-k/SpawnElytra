package at.blaxk.spawnelytra;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MessageUtils {
    private static Map<String, String> messages = new HashMap<>();
    private static Map<String, Boolean> messageToggles = new HashMap<>();
    private static Pattern legacyColorPattern = Pattern.compile("&[0-9a-fk-orA-FK-OR]");
    private static Plugin plugin;

    public static void initialize(Plugin plugin) {
        MessageUtils.plugin = plugin;
    }

    public static void loadMessages(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        FileConfiguration config = plugin.getConfig();
        String language = config.getString("language", "en").toLowerCase();

        boolean useCustomMessages = config.getBoolean("messages.use_custom_messages", true);

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File langFile = new File(langDir, language + ".yml");
        FileConfiguration langConfig = null;

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        } else {
            InputStream defaultLangStream = plugin.getResource("lang/" + language + ".yml");
            if (defaultLangStream != null) {
                langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultLangStream));
            } else {
                File enLangFile = new File(langDir, "en.yml");
                if (enLangFile.exists()) {
                    langConfig = YamlConfiguration.loadConfiguration(enLangFile);
                } else {
                    InputStream enLangStream = plugin.getResource("lang/en.yml");
                    if (enLangStream != null) {
                        langConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enLangStream));
                    } else {
                        plugin.getLogger().warning("No language files found, using hardcoded defaults");
                    }
                }
            }
        }

        if (langConfig != null) {
            for (String key : langConfig.getKeys(false)) {
                messages.put(key, langConfig.getString(key));
            }
        } else {
            loadDefaultMessages(language);
        }

        if (useCustomMessages) {
            if (config.isSet("messages.press_to_boost")) {
                messages.put("press_to_boost", config.getString("messages.press_to_boost"));
            }

            if (config.isSet("messages.boost_activated")) {
                messages.put("boost_activated", config.getString("messages.boost_activated"));
            }
        } else {
            plugin.getLogger().info("Using language file messages (custom messages disabled)");
        }

        boolean showPressToBoost = config.getBoolean("messages.show_press_to_boost", true);
        boolean showBoostActivated = config.getBoolean("messages.show_boost_activated", true);

        plugin.getLogger().info("Press to boost message enabled: " + showPressToBoost);
        plugin.getLogger().info("Boost activated message enabled: " + showBoostActivated);
        plugin.getLogger().info("Using custom messages: " + useCustomMessages);

        messageToggles.put("press_to_boost", showPressToBoost);
        messageToggles.put("boost_activated", showBoostActivated);
    }

    private static void loadDefaultMessages(String language) {
        switch (language) {
            case "en":
                messages.put("press_to_boost", "&6Press {key} &6to boost yourself");
                messages.put("boost_activated", "&a&lBoost activated!");
                messages.put("new_version_available", "&bA new version of the spawn elytra plugin is available!");
                messages.put("update_to_version", "&ePlease update to version &a{latestVersion} &7(current: &c{currentVersion}&7)");
                messages.put("download_link", "&bDownload link:");
                messages.put("failed_update_check", "&cFailed to check for updates: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eElytra flight disabled in Creative mode.");
                break;
            case "de":
                messages.put("press_to_boost", "&6Drücke {key} &6um dich zu boosten");
                messages.put("boost_activated", "&a&lBoost aktiviert!");
                messages.put("new_version_available", "&bEine neue Version des Spawn Elytra Plugins ist verfügbar!");
                messages.put("update_to_version", "&eBitte aktualisiere auf Version &a{latestVersion} &7(Aktuelle Version: &c{currentVersion}&7)");
                messages.put("download_link", "&bDownload-Link:");
                messages.put("failed_update_check", "&cFehler beim Überprüfen auf Updates: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eElytra-Flug im Kreativmodus deaktiviert.");
                break;
            case "es":
                messages.put("press_to_boost", "&6Presiona {key} &6para impulsarte.");
                messages.put("boost_activated", "&a&l¡Impulso activado!");
                messages.put("new_version_available", "&b¡Una nueva versión de Spawn Elytra está disponible!");
                messages.put("update_to_version", "&ePor favor, actualiza a la versión &a{latestVersion} &7(actual: &c{currentVersion}&7)");
                messages.put("download_link", "&bEnlace de descarga:");
                messages.put("failed_update_check", "&cError al comprobar actualizaciones: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eVuelo con elytra desactivado en modo Creativo.");
                break;
            case "fr":
                messages.put("press_to_boost", "&6Appuyez sur {key} &6pour vous propulser.");
                messages.put("boost_activated", "&a&lPropulsion activée !");
                messages.put("new_version_available", "&bUne nouvelle version de Spawn Elytra est disponible !");
                messages.put("update_to_version", "&eVeuillez mettre à jour vers la version &a{latestVersion} &7(actuelle : &c{currentVersion}&7)");
                messages.put("download_link", "&bLien de téléchargement :");
                messages.put("failed_update_check", "&cÉchec de la vérification des mises à jour : {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eVol en élytre désactivé en mode Créatif.");
                break;
            case "hi":
                messages.put("press_to_boost", "&6दबाएं {key} &6खुद को बूस्ट करने के लिए।");
                messages.put("boost_activated", "&a&lबूस्ट सक्रिय!");
                messages.put("new_version_available", "&bस्पॉन एलिट्रा का एक नया संस्करण उपलब्ध है!");
                messages.put("update_to_version", "&eकृपया संस्करण &a{latestVersion} &7पर अपडेट करें (वर्तमान: &c{currentVersion}&7)");
                messages.put("download_link", "&bडाउनलोड लिंक:");
                messages.put("failed_update_check", "&cअपडेट की जांच करने में विफल: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eक्रिएटिव मोड में एलिट्रा उड़ान अक्षम।");
                break;
            case "zh":
                messages.put("press_to_boost", "&6按下 {key} &6来加速自己。");
                messages.put("boost_activated", "&a&l加速已激活！");
                messages.put("new_version_available", "&bSpawn Elytra 插件有新版本可用！");
                messages.put("update_to_version", "&e请更新到版本 &a{latestVersion} &7(当前版本: &c{currentVersion}&7)");
                messages.put("download_link", "&b下载链接:");
                messages.put("failed_update_check", "&c检查更新失败: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&e创造模式下已禁用鞘翅飞行。");
                break;
            case "ar":
                messages.put("press_to_boost", "&6اضغط {key} &6للانطلاق.");
                messages.put("boost_activated", "&a&lتم تنشيط الدفع!");
                messages.put("new_version_available", "&bيتوفر إصدار جديد من إضافة سبون إليترا!");
                messages.put("update_to_version", "&eيرجى التحديث إلى الإصدار &a{latestVersion} &7(الإصدار الحالي: &c{currentVersion}&7)");
                messages.put("download_link", "&bرابط التحميل:");
                messages.put("failed_update_check", "&cفشل التحقق من التحديثات: {errorMessage}");
                messages.put("creative_mode_elytra_disabled", "&eتم تعطيل طيران الإليترا في وضع الإبداع.");
                break;
            default:
                // Fall back English
                loadDefaultMessages("en");
                break;
        }
    }

    public static String getMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getOrDefault(key, key));
    }

    public static void sendMessageWithFallback(Player player, String key) {
        String message = getMessage(key);
        player.sendMessage(message);
    }

    public static void sendActionBarWithFallback(Player player, String key) {
        if (!messageToggles.getOrDefault(key, true)) {
            return;
        }

        String message = messages.getOrDefault(key, key);
        message = message.replace("{key}", "F");
        message = ChatColor.translateAlternateColorCodes('&', message);

        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(message));
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    public static void sendActionBar(Player player, String key) {
        sendActionBarWithFallback(player, key);
    }

    public static void sendMessage(Player player, String key) {
        sendMessageWithFallback(player, key);
    }

    public static boolean isMessageEnabled(String key) {
        return messageToggles.getOrDefault(key, true);
    }
}