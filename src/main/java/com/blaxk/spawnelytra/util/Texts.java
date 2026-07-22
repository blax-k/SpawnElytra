/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.util;

import com.blaxk.spawnelytra.common.text.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders core {@link Msg} values through the lang files. Argument strings are always inserted literally. */
public enum Texts {
    ;

    public static Component render(final Msg msg) {
        if (msg == null) {
            return Component.empty();
        }
        if (msg.isLiteral()) {
            return Component.text(msg.literalText());
        }
        return MessageUtil.component(msg.key(), resolvers(msg));
    }

    public static TagResolver[] resolvers(final Msg msg) {
        final List<TagResolver> out = new ArrayList<>(msg.args().size());
        for (final Map.Entry<String, Object> e : msg.args().entrySet()) {
            if (e.getValue() instanceof final Msg nested) {
                out.add(Placeholder.component(e.getKey(), render(nested)));
            } else {
                out.add(Placeholder.unparsed(e.getKey(), String.valueOf(e.getValue())));
            }
        }
        return out.toArray(new TagResolver[0]);
    }

    public static String plain(final Msg msg) {
        return PlainTextComponentSerializer.plainText().serialize(render(msg));
    }

    /** Renders a lang key with literal string arguments given as name/value pairs. */
    public static Component of(final String key, final Object... nameValuePairs) {
        return render(Msg.of(key, nameValuePairs));
    }

    public static void send(final CommandSender sender, final Msg msg) {
        if (sender instanceof final Player p) {
            MessageUtil.sendRaw(p, render(msg));
        } else {
            MessageUtil.sendRaw(sender, render(msg));
        }
    }

    public static void actionBar(final Player player, final Msg msg) {
        MessageUtil.sendActionBarRaw(player, render(msg));
    }

    /** Item names/lore: never italic. */
    public static Component flat(final Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
