/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.util;

import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.config.YamlConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import com.blaxk.spawnelytra.common.text.Msg;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of the Paper plugin's MessageUtil: same defaults, lookup order, styles and toggles. */
public enum MessageUtil {
    ;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Map<String, String> DEFAULT_MESSAGES = new HashMap<>();
    private static final Map<Character, String> SMALL_CAPS_MAP;
    private static final Pattern UPPERCASE_PLACEHOLDER_PATTERN = Pattern.compile("<([A-Za-z0-9_-]*[A-Z][A-Za-z0-9_-]*)>");

    private static final Map<String, String> messages = new HashMap<>();
    private static final Map<String, Boolean> messageToggles = new HashMap<>();

    static {
        final Map<Character, String> smallCaps = new HashMap<>();
        smallCaps.put('a', "ᴀ");
        smallCaps.put('b', "ʙ");
        smallCaps.put('c', "ᴄ");
        smallCaps.put('d', "ᴅ");
        smallCaps.put('e', "ᴇ");
        smallCaps.put('f', "ꜰ");
        smallCaps.put('g', "ɢ");
        smallCaps.put('h', "ʜ");
        smallCaps.put('i', "ɪ");
        smallCaps.put('j', "ᴊ");
        smallCaps.put('k', "ᴋ");
        smallCaps.put('l', "ʟ");
        smallCaps.put('m', "ᴍ");
        smallCaps.put('n', "ɴ");
        smallCaps.put('o', "ᴏ");
        smallCaps.put('p', "ᴘ");
        smallCaps.put('q', "ǫ");
        smallCaps.put('r', "ʀ");
        smallCaps.put('s', "ꜱ");
        smallCaps.put('t', "ᴛ");
        smallCaps.put('u', "ᴜ");
        smallCaps.put('v', "ᴠ");
        smallCaps.put('w', "ᴡ");
        smallCaps.put('x', "x");
        smallCaps.put('y', "ʏ");
        smallCaps.put('z', "ᴢ");
        smallCaps.put('ä', "ä");
        smallCaps.put('ö', "ö");
        smallCaps.put('ü', "ü");
        smallCaps.put('ß', "ꜱꜱ");
        SMALL_CAPS_MAP = Collections.unmodifiableMap(smallCaps);

        DEFAULT_MESSAGES.put("press_to_boost", "<#91f251>Press <bold><#74ea31><key:key.swapOffhand></bold> <#91f251>to boost yourself");
        DEFAULT_MESSAGES.put("press_to_boost_remaining", "<#91f251>Press <bold><#74ea31><key:key.swapOffhand></bold> <#91f251>to boost <#ffd166>(<remaining> remaining)");
        DEFAULT_MESSAGES.put("press_to_boost_bedrock", "<#91f251>Press <bold><#74ea31>Sneak</bold> <#91f251>to boost yourself");
        DEFAULT_MESSAGES.put("press_to_boost_remaining_bedrock", "<#91f251>Press <bold><#74ea31>Sneak</bold> <#91f251>to boost <#ffd166>(<remaining> remaining)");
        DEFAULT_MESSAGES.put("temp_elytra_lore", "<#aaa8a8>Temporary");
        DEFAULT_MESSAGES.put("boost_activated", "<#74ea31><bold>Boost activated!</bold>");
        DEFAULT_MESSAGES.put("boost_activated_remaining", "<#74ea31><bold>Boost activated!</bold> <#ffd166>(<remaining> remaining)");

        DEFAULT_MESSAGES.put("failed_update_check", "<#fd5e5e>Failed to check for updates: <error_message>");
        DEFAULT_MESSAGES.put("creative_mode_elytra_disabled", "<#ffeea2>Elytra flight disabled in Creative mode.");
        DEFAULT_MESSAGES.put("no_permission", "<#fd5e5e>You don't have permission to use this command.");
        DEFAULT_MESSAGES.put("command_player_only", "<#fd5e5e>This command can only be used by players.");
        DEFAULT_MESSAGES.put("reload_success", "<#91f251>SpawnElytra configuration reloaded.");
        DEFAULT_MESSAGES.put("spawnelytra_not_available", "<#fd5e5e>SpawnElytra instance not available.");
        DEFAULT_MESSAGES.put("help_header", "<#ffcc33>Spawn Elytra Help");
        DEFAULT_MESSAGES.put("help_reload", "<#fdba5e>/spawnelytra reload <#aaa8a8>- Reload the plugin configuration");
        DEFAULT_MESSAGES.put("help_info", "<#fdba5e>/spawnelytra info <#aaa8a8>- Show plugin information");
        DEFAULT_MESSAGES.put("help_update", "<#fdba5e>/spawnelytra update <#aaa8a8>- Automatically download and install the latest version");
        DEFAULT_MESSAGES.put("help_visualize", "<#fdba5e>/spawnelytra visualize <#aaa8a8>- Visualize the elytra area with particles");
        DEFAULT_MESSAGES.put("help_settings", "<#fdba5e>/spawnelytra settings <#aaa8a8>- Open the settings menu");
        DEFAULT_MESSAGES.put("info_header", "<#ffcc33>Spawn Elytra Config");
        DEFAULT_MESSAGES.put("info_version", "<#fdba5e>Version: <#91f251><value></#91f251>");

        DEFAULT_MESSAGES.put("info_website", "<#fdba5e>Website: <#5db3ff><value></#5db3ff>");
        DEFAULT_MESSAGES.put("info_world", "<#fdba5e>World: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_radius", "<#fdba5e>Radius: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_strength", "<#fdba5e>Boost Strength: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_boost_enabled", "<#fdba5e>Boost Enabled: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_activation_mode", "<#fdba5e>Activation Mode: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_offhand_key", "<#fdba5e>   Offhand Key: <bold><#fdba5e><key:key.swapOffhand></#fdba5e></bold>");
        DEFAULT_MESSAGES.put("info_f_key_launch_strength", "<#fdba5e>   F-Key Launch Strength: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_spawn_mode", "<#fdba5e>Spawn Mode: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("info_language", "<#fdba5e>Language: <#91f251><value></#91f251>");
        DEFAULT_MESSAGES.put("visualize_start", "<#91f251>Visualizing spawn area for <#ffd166><seconds></#ffd166> seconds...");
        DEFAULT_MESSAGES.put("visualize_end", "<#fdba5e>Area visualization ended.");
        DEFAULT_MESSAGES.put("visualize_stop", "<#fdba5e>Area visualization stopped.");
        DEFAULT_MESSAGES.put("visualize_no_area", "<#fd5e5e>No valid spawn area configured!");

        DEFAULT_MESSAGES.put("setup_started", "<#91f251>Setup Help enabled. Go to position <#ffd166>1</#ffd166> and run <#5db3ff>/se set pos1</#5db3ff>.");
        DEFAULT_MESSAGES.put("setup_already_running", "<#ffd166>Setup Help is already active.");
        DEFAULT_MESSAGES.put("setup_cancelled", "<#fdba5e>Setup Help exited.");
        DEFAULT_MESSAGES.put("setup_not_running", "<#fd5e5e>Setup Help is not active.");
        DEFAULT_MESSAGES.put("setup_pos1_set", "<#91f251>Position 1 set! Now go to position <#ffd166>2</#ffd166> and run <#5db3ff>/se set pos2</#5db3ff>.");
        DEFAULT_MESSAGES.put("setup_pos2_set", "<#91f251>Position 2 set! Previewing area...");
        DEFAULT_MESSAGES.put("setup_action_pos1", "<#5db3ff>Setup: Go to pos1 and run /se set pos1");
        DEFAULT_MESSAGES.put("setup_action_pos2", "<#5db3ff>Setup: Go to pos2 and run /se set pos2");
        DEFAULT_MESSAGES.put("setup_world_mismatch", "<#fd5e5e>Both positions must be in the same world.");
        DEFAULT_MESSAGES.put("setup_options_header", "<#ffcc33>Setup Options");
        DEFAULT_MESSAGES.put("setup_activation_mode_set", "<#91f251>Activation mode: <#ffd166><value></#ffd166>");
        DEFAULT_MESSAGES.put("setup_invalid_mode", "<#fd5e5e>Invalid activation mode.");
        DEFAULT_MESSAGES.put("setup_toggle_boost_label", "Boost activated hint");
        DEFAULT_MESSAGES.put("setup_toggle_press_label", "\"Press F\" hint");
        DEFAULT_MESSAGES.put("setup_toggled_boost_activated", "<#91f251>Boost activated hint: <#ffd166><value></#ffd166>");
        DEFAULT_MESSAGES.put("setup_toggled_press_to_boost", "<#91f251>\"Press F\" hint: <#ffd166><value></#ffd166>");
        DEFAULT_MESSAGES.put("setup_missing_positions", "<#fd5e5e>Please set both positions first.");
        DEFAULT_MESSAGES.put("setup_saved", "<#91f251>Setup saved and applied.");
        DEFAULT_MESSAGES.put("help_setup", "<#fdba5e>/spawnelytra setup <#aaa8a8>- Interactive Setup Help (pos1/pos2, options)");
    }

    private static String canonicalizeLanguageCode(final String input) {
        if (input == null) {
            return "en";
        }
        String s = input.trim().toLowerCase(Locale.ROOT);

        if (s.matches("^[a-z]{2}[-_][a-z]{2}$")) {
            s = s.substring(0, 2);
        }

        switch (s) {
            case "deutsch":
            case "german":
            case "de_de":
            case "de-at":
            case "de_at":
                return "de";
            case "français":
            case "francais":
            case "french":
            case "fr_fr":
                return "fr";
            case "español":
            case "espanol":
            case "spanish":
            case "es_es":
            case "es-mx":
                return "es";
            case "العربية":
            case "arabic":
            case "ar_sa":
            case "ar_eg":
                return "ar";
            case "english":
            case "en_us":
            case "en-gb":
                return "en";
            case "polski":
            case "polish":
            case "pl_pl":
                return "pl";
            default:
                if (s.length() == 2 && ("en".equals(s) || "de".equals(s) || "es".equals(s) || "fr".equals(s) || "ar".equals(s) || "pl".equals(s))) {
                    return s;
                }
                return "en";
        }
    }

    public static void loadMessages(final Main plugin) {
        final YamlConfiguration config = plugin.getConfig();
        final String rawLanguage = config.getString("language", "en");
        final String language = canonicalizeLanguageCode(rawLanguage);
        final String rawStyle = config.getString("messages.style", "classic");
        final String style = (rawStyle == null ? "classic" : rawStyle).toLowerCase(Locale.ROOT);

        messages.clear();

        DEFAULT_MESSAGES.forEach((key, value) -> messages.put(key, normalizePlaceholders(value)));

        final Map<String, String> englishMessages = loadLanguageMessages(plugin, "en");
        englishMessages.forEach((key, value) -> messages.put(key, normalizePlaceholders(value)));

        final Map<String, String> languageMessages = loadLanguageMessages(plugin, language);
        languageMessages.forEach((key, value) -> messages.put(key, normalizePlaceholders(value)));

        if ("small_caps".equals(style) && ("en".equals(language) || "de".equals(language))) {
            for (final Map.Entry<String, String> entry : new HashMap<>(messages).entrySet()) {
                final String current = entry.getValue();
                if (current != null) {
                    messages.put(entry.getKey(), toSmallCapsPreservingTags(current));
                }
            }
        }

        messageToggles.clear();
        messageToggles.put("press_to_boost", config.getBoolean("messages.show_press_to_boost", true));
        messageToggles.put("press_to_boost_bedrock", config.getBoolean("messages.show_press_to_boost", true));
        messageToggles.put("press_to_boost_remaining_bedrock", config.getBoolean("messages.show_press_to_boost", true));
        messageToggles.put("boost_activated", config.getBoolean("messages.show_boost_activated", true));
        messageToggles.put("creative_mode_elytra_disabled", config.getBoolean("messages.show_creative_disabled", false));
    }

    private static Map<String, String> loadLanguageMessages(final Main plugin, final String language) {
        final Map<String, String> loaded = new HashMap<>();

        final File langDir = new File(plugin.getDataFolder(), "lang");
        final File langFile = new File(langDir, language + ".yml");
        YamlConfiguration langConfig = null;

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile, plugin.getLogger());
        } else {
            try (final InputStream defaultLangStream = plugin.getResource("lang/" + language + ".yml")) {
                if (defaultLangStream != null) {
                    langConfig = YamlConfiguration.loadConfiguration(defaultLangStream);
                }
            } catch (final Exception ignored) {
            }
        }

        if (langConfig == null && !"en".equals(language)) {
            return loadLanguageMessages(plugin, "en");
        }

        if (langConfig == null) {
            return loaded;
        }

        for (final String key : langConfig.getKeys(false)) {
            final String value = langConfig.getString(key);
            if (value != null) {
                loaded.put(key, normalizePlaceholders(value));
            }
        }
        return loaded;
    }

    private static String normalizePlaceholders(final String input) {
        if (input == null) {
            return "";
        }
        final Matcher matcher = UPPERCASE_PLACEHOLDER_PATTERN.matcher(input);
        final StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            final String placeholder = matcher.group(1);
            final String normalized = normalizePlaceholderName(placeholder);
            matcher.appendReplacement(buffer, "<" + normalized + ">");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String normalizePlaceholderName(final String name) {
        final StringBuilder builder = new StringBuilder(name.length());
        final char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0 && '_' != chars[i - 1] && '-' != chars[i - 1]) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    public static String toSmallCapsPreservingTags(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        final StringBuilder out = new StringBuilder(input.length());
        boolean inTag = false;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            final char ch = input.charAt(i);
            if ('<' == ch) {
                inTag = true;
                depth++;
                out.append('<');
                continue;
            }
            if ('>' == ch && inTag) {
                out.append('>');
                depth--;
                if (depth <= 0) {
                    inTag = false;
                    depth = 0;
                }
                continue;
            }
            if (inTag) {
                out.append(ch);
            } else {
                final char lower = Character.toLowerCase(ch);
                final String mapped = SMALL_CAPS_MAP.get(lower);
                if (mapped != null) {
                    out.append(mapped);
                } else {
                    out.append(ch);
                }
            }
        }
        return out.toString();
    }

    public static Component component(final String key, final TagResolver... resolvers) {
        final String raw = messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
        return MM.deserialize(raw, resolvers);
    }

    /**
     * Renders a core {@link Msg}: lang key with placeholders (String = unparsed, nested Msg =
     * rendered component), or a literal text.
     */
    public static Component render(final Msg msg) {
        if (msg == null) {
            return Component.empty();
        }
        if (msg.isLiteral()) {
            return Component.text(msg.literalText() == null ? "" : msg.literalText());
        }
        final List<TagResolver> resolvers = new ArrayList<>();
        for (final Map.Entry<String, Object> arg : msg.args().entrySet()) {
            final String name = arg.getKey().toLowerCase(Locale.ROOT);
            if (arg.getValue() instanceof final Msg nested) {
                resolvers.add(Placeholder.component(name, render(nested)));
            } else {
                resolvers.add(Placeholder.unparsed(name, String.valueOf(arg.getValue())));
            }
        }
        return component(msg.key(), resolvers.toArray(new TagResolver[0]));
    }

    public static String plain(final Msg msg) {
        return PLAIN.serialize(render(msg));
    }

    public static void send(final ServerPlayer player, final Msg msg) {
        if (player != null) {
            sendRaw(player, render(msg));
        }
    }

    public static void send(final CommandSourceStack sender, final Msg msg) {
        if (sender != null) {
            sendRaw(sender, render(msg));
        }
    }

    public static void sendActionBar(final ServerPlayer player, final Msg msg) {
        if (player != null && msg != null) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Texts.toNative(render(msg))));
        }
    }

    /** The raw (MiniMessage) text of a key after language/style processing. */
    public static String rawMessage(final String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
    }

    public static Component deserialize(final String miniMessage, final TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    public static boolean hasKey(final String key) {
        return messages.containsKey(key) || DEFAULT_MESSAGES.containsKey(key);
    }

    public static String plain(final String key, final TagResolver... resolvers) {
        return PLAIN.serialize(component(key, resolvers));
    }

    public static String plain(final Component component) {
        return PLAIN.serialize(component);
    }

    public static void send(final ServerPlayer player, final String key, final TagResolver... resolvers) {
        if (player == null) {
            return;
        }
        sendRaw(player, component(key, resolvers));
    }

    public static void send(final CommandSourceStack sender, final String key, final TagResolver... resolvers) {
        if (sender == null) {
            return;
        }
        sendRaw(sender, component(key, resolvers));
    }

    public static void sendActionBar(final ServerPlayer player, final String key, final TagResolver... resolvers) {
        if (!messageToggles.getOrDefault(key, true)) {
            return;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(Texts.toNative(component(key, resolvers))));
    }

    public static void sendRaw(final ServerPlayer player, final Component component) {
        player.sendSystemMessage(Texts.toNative(component));
    }

    public static void sendRaw(final CommandSourceStack sender, final Component component) {
        sender.sendSystemMessage(Texts.toNative(component));
    }
}
