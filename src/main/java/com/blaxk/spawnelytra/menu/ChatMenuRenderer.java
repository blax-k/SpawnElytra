/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.menu;

import com.blaxk.spawnelytra.common.screen.Action;
import com.blaxk.spawnelytra.common.screen.Button;
import com.blaxk.spawnelytra.common.screen.Element;
import com.blaxk.spawnelytra.common.screen.Field;
import com.blaxk.spawnelytra.common.screen.Screen;
import com.blaxk.spawnelytra.common.text.Msg;
import com.blaxk.spawnelytra.integration.BedrockSupport;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Chat fallback for screens (spec §7.3): clickable {@code run_command}/{@code suggest_command} buttons,
 * number fields as {@code [--] [-] value [+] [++]}, options cycle on click. Bedrock players (no clickable chat)
 * see the command next to each button instead.
 */
final class ChatMenuRenderer {
    private static final TextColor BUTTON = TextColor.color(0x91f251);
    private static final TextColor MUTED = TextColor.color(0xaaa8a8);

    void render(final CommandSender sender, final Screen screen) {
        final boolean bedrock = sender instanceof final Player p && BedrockSupport.isBedrockPlayer(p);
        send(sender, MessageUtil.component("menu_chat_title", Placeholder.component("title", Texts.render(screen.title()))));
        for (final Element element : screen.body()) {
            switch (element) {
                case final Element.Text t -> send(sender, Texts.render(t.text()));
                case final Element.Heading h -> send(sender, MessageUtil.component("menu_chat_title",
                        Placeholder.component("title", Texts.render(h.text()))));
                case final Element.Row row -> {
                    TextComponent.Builder line = Component.text().append(Texts.render(row.text()));
                    for (final Button b : row.buttons()) {
                        if (b.mode().visible(false)) {
                            line.append(Component.text(" ")).append(this.button(b, bedrock));
                        }
                    }
                    send(sender, line.build());
                }
                case final Field field -> {
                    final TextComponent.Builder line = Component.text().append(MessageUtil.component("menu_chat_field_line",
                            Placeholder.component("label", Texts.render(field.label())),
                            Placeholder.component("value", this.valueOf(field))));
                    for (final Button b : field.chatControls()) {
                        line.append(Component.text(" ")).append(this.button(b, bedrock));
                    }
                    send(sender, line.build());
                }
            }
        }
        final List<Button> footer = screen.buttons(false);
        if (!footer.isEmpty()) {
            final TextComponent.Builder line = Component.text();
            boolean first = true;
            for (final Button b : footer) {
                if (b.action() == null || b.action().command() == null) {
                    continue;
                }
                if (!first) {
                    line.append(Component.text(" "));
                }
                first = false;
                line.append(this.button(b, bedrock));
            }
            if (!first) {
                send(sender, line.build());
            }
        }
    }

    private Component valueOf(final Field field) {
        return switch (field) {
            case final Field.Bool b -> Texts.render(Msg.of(b.checked() ? "state_on" : "state_off"));
            case final Field.Number n -> Component.text(n.value());
            case final Field.Choice c -> Texts.render(c.selectedLabel());
            case final Field.TextInput t -> Component.text(t.text() == null ? "" : t.text());
            case final Field.ReadOnly r -> Texts.render(r.display());
        };
    }

    private Component button(final Button button, final boolean bedrock) {
        final Action action = button.action();
        final String command = action == null ? null : action.command();
        Component label = Component.text("[", BUTTON).append(Texts.render(button.label()).colorIfAbsent(BUTTON))
                .append(Component.text("]", BUTTON));
        if (command == null) {
            return label;
        }
        if (bedrock) {
            return label.append(Component.text(" " + command.trim(), MUTED));
        }
        label = label.clickEvent(action.suggest() ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command));
        final Component tooltip = button.tooltip() != null ? Texts.render(button.tooltip()) : Component.text(command.trim(), MUTED);
        return label.hoverEvent(HoverEvent.showText(tooltip));
    }

    private static void send(final CommandSender sender, final Component component) {
        if (sender instanceof final Player p) {
            MessageUtil.sendRaw(p, component);
        } else {
            MessageUtil.sendRaw(sender, component);
        }
    }
}
