/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.menu;

import com.blaxk.spawnelytra.common.screen.Action;
import com.blaxk.spawnelytra.common.screen.ActionIds;
import com.blaxk.spawnelytra.common.screen.Screen;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.util.Compat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//? if >=1.21.6 {
import com.blaxk.spawnelytra.common.screen.Button;
import com.blaxk.spawnelytra.common.screen.Element;
import com.blaxk.spawnelytra.common.screen.Field;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Texts;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
//?}

/**
 * Vanilla dialog renderer (Minecraft 1.21.6+), the Fabric counterpart of the Paper dialog bridge
 * (same layout, same payload format). The dialog is sent inline (direct holder), no data pack
 * needed. Each button is a {@code dynamic/custom} action with an id in the {@code spawnelytra:}
 * namespace; its additions carry the action payload, the field layout and a per-player random
 * token. Clicks with unknown tokens (forged or from an old dialog) are ignored; the action itself
 * is re-validated (permission, zone existence) by {@link MenuService#handleDialogAction}.
 * On nodes without dialogs {@link #SUPPORTED} is {@code false}.
 */
public final class DialogRenderer {
    //? if >=1.21.6 {
    public static final boolean SUPPORTED = true;
    //?} else {
    /*public static final boolean SUPPORTED = false;
    *///?}

    private static final String TOKEN = "se_token";
    private static final String PAYLOAD = "se_payload";
    private static final String FIELDS = "se_fields";
    private static final int MAX_TOKENS = 16;
    private static final int MUTED = 0xaaa8a8;

    private final Main plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Deque<String>> tokens = new ConcurrentHashMap<>();

    DialogRenderer(final Main plugin) {
        this.plugin = plugin;
    }

    void forget(final UUID player) {
        this.tokens.remove(player);
    }

    // ---- rendering ---------------------------------------------------------------------------------------

    boolean show(final ServerPlayer player, final Screen screen) {
        //? if >=1.21.6 {
        final String token = this.issueToken(player.getUUID());
        final List<Field> fields = screen.fields();
        final String fieldLayout = encodeFields(fields);

        final List<DialogBody> body = new ArrayList<>();
        final List<Input> inputs = new ArrayList<>();
        final List<ActionButton> rowButtons = new ArrayList<>();
        int rowColumns = 0;
        int index = 0;
        for (final Element element : screen.body()) {
            switch (element) {
                case final Element.Text t -> body.add(new PlainMessage(nat(MessageUtil.render(t.text())), 300));
                case final Element.Heading h -> body.add(new PlainMessage(nat(MessageUtil.render(h.text())), 300));
                case final Element.Row row -> {
                    body.add(new PlainMessage(nat(MessageUtil.render(row.text())), 300));
                    final List<Button> visible = row.buttons().stream().filter(b -> b.mode().visible(true)).toList();
                    rowColumns = Math.max(rowColumns, visible.size());
                    boolean first = true;
                    for (final Button b : visible) {
                        // The first button of a row carries the row id so the grid stays readable.
                        final net.kyori.adventure.text.Component label = first
                                ? net.kyori.adventure.text.Component.text(row.id() + " · ").append(MessageUtil.render(b.label()))
                                : MessageUtil.render(b.label());
                        rowButtons.add(this.button(b, label, token, fieldLayout, 120));
                        first = false;
                    }
                }
                case final Field.ReadOnly r -> body.add(new PlainMessage(nat(MessageUtil.render(r.label())
                        .append(net.kyori.adventure.text.Component.text(": ", net.kyori.adventure.text.format.TextColor.color(MUTED)))
                        .append(MessageUtil.render(r.display()))), 300));
                case final Field f -> {
                    inputs.add(new Input("f" + index, this.input(f)));
                    index++;
                }
            }
        }

        final List<ActionButton> footer = new ArrayList<>();
        for (final Button b : screen.buttons(true)) {
            footer.add(this.button(b, MessageUtil.render(b.label()), token, fieldLayout, 150));
        }
        final ActionButton exit = screen.exit() == null ? null
                : this.button(screen.exit(), MessageUtil.render(screen.exit().label()), token, fieldLayout, 150);

        final CommonDialogData base = new CommonDialogData(nat(MessageUtil.render(screen.title())), Optional.empty(), true, false,
                DialogAction.CLOSE, body, inputs);

        final Dialog dialog;
        switch (screen.id()) {
            case DELETE_CONFIRM -> {
                final ActionButton yes = footer.isEmpty() ? exit : footer.get(0);
                final ActionButton no = footer.size() > 1 ? footer.get(1) : exit;
                dialog = new ConfirmationDialog(base, yes, no);
            }
            case STATS -> dialog = new NoticeDialog(base, exit != null ? exit
                    : new ActionButton(new CommonButtonData(Component.translatable("gui.ok"), 150), Optional.empty()));
            default -> {
                // Grid: one row per zone (every row has the same number of buttons), then the footer buttons.
                final List<ActionButton> all = new ArrayList<>(rowButtons);
                all.addAll(footer);
                if (all.isEmpty() && exit != null) {
                    all.add(exit);
                }
                final int columns = rowColumns > 0 ? rowColumns : Math.min(3, Math.max(1, footer.size()));
                dialog = new MultiActionDialog(base, all, Optional.empty(), columns);
            }
        }
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    //? if >=1.21.6 {
    private static Component nat(final net.kyori.adventure.text.Component component) {
        return Texts.toNative(component);
    }

    private InputControl input(final Field field) {
        final Component label = nat(MessageUtil.render(field.label()));
        return switch (field) {
            case final Field.Bool b -> new BooleanInput(label, b.checked(), "true", "false");
            case final Field.Number n -> {
                final float min = (float) n.min();
                final float max = (float) n.max();
                final float step = (float) (n.step() > 0 ? n.step() : (n.integer() ? 1 : 0.1));
                final float initial = (float) Math.max(n.min(), Math.min(n.max(), n.current()));
                // Vanilla requires (max - min) to be a multiple of the step for a usable slider; very wide ranges
                // (priority, y) fall back to a text input so any value can still be typed.
                if (!Float.isFinite(min) || !Float.isFinite(max) || (max - min) / step > 1000) {
                    yield new TextInput(200, label, true, n.value(), 16, Optional.empty());
                }
                yield new NumberRangeInput(300, label, "options.generic_value",
                        new NumberRangeInput.RangeInfo(min, max, Optional.of(initial), Optional.of(step)));
            }
            case final Field.Choice c -> {
                final List<SingleOptionInput.Entry> options = new ArrayList<>();
                boolean any = false;
                for (final Field.Option o : c.options()) {
                    final boolean selected = o.value().equals(c.selected());
                    any |= selected;
                    options.add(new SingleOptionInput.Entry(o.value(), Optional.of(nat(MessageUtil.render(o.label()))), selected));
                }
                if (!any && !options.isEmpty()) {
                    final SingleOptionInput.Entry first = options.get(0);
                    options.set(0, new SingleOptionInput.Entry(first.id(), first.display(), true));
                }
                yield new SingleOptionInput(300, options, label, true);
            }
            case final Field.TextInput t -> new TextInput(300, label, true, t.text() == null ? "" : t.text(),
                    Math.max(1, t.maxLength()), Optional.empty());
            case final Field.ReadOnly r -> new TextInput(200, label, true, "", 32, Optional.empty());
        };
    }

    private ActionButton button(final Button button, final net.kyori.adventure.text.Component label, final String token,
                                final String fieldLayout, final int width) {
        final Action action = button.action();
        final Optional<Component> tooltip = button.tooltip() == null ? Optional.empty()
                : Optional.of(nat(MessageUtil.render(button.tooltip())));
        final CommonButtonData data = new CommonButtonData(nat(label), tooltip, width);
        if (action == null || ActionIds.CLOSE.equals(action.id())) {
            return new ActionButton(data, Optional.empty());
        }
        final CompoundTag additions = new CompoundTag();
        additions.putString(TOKEN, token);
        additions.putString(PAYLOAD, encodePayload(action.payload()));
        additions.putString(FIELDS, ActionIds.isFormSubmit(action.id()) ? fieldLayout : "");
        return new ActionButton(data, Optional.of(new CustomAll(Compat.parseId(action.id()), Optional.of(additions))));
    }

    private static String encodeFields(final List<Field> fields) {
        final StringBuilder sb = new StringBuilder();
        for (final Field f : fields) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            final String type = switch (f) {
                case final Field.Bool b -> "b";
                case final Field.Number n -> n.integer() ? "i" : "d";
                case final Field.Choice c -> "c";
                default -> "t";
            };
            sb.append(type).append(':').append(f.id());
        }
        return sb.toString();
    }
    //?}

    // ---- click handling ----------------------------------------------------------------------------------

    /** A {@code spawnelytra:*} custom click action from a dialog (server thread). */
    void onCustomClick(final ServerPlayer player, final String actionId, final Tag payloadTag) {
        if (!(payloadTag instanceof final CompoundTag payload)) {
            return;
        }
        final String token = text(payload, TOKEN);
        if (token == null || !this.consumeToken(player.getUUID(), token)) {
            return; // stale or forged
        }
        if (!ActionIds.isKnown(actionId)) {
            return;
        }
        final Map<String, String> actionPayload = decodePayload(text(payload, PAYLOAD));
        final Map<String, String> inputs = new HashMap<>();
        final String layout = text(payload, FIELDS);
        if (layout != null && !layout.isEmpty()) {
            final String[] entries = layout.split(",");
            for (int i = 0; i < entries.length; i++) {
                final int colon = entries[i].indexOf(':');
                if (colon < 0) {
                    continue;
                }
                final String type = entries[i].substring(0, colon);
                final String fieldId = entries[i].substring(colon + 1);
                final String value = readInput(payload, "f" + i, type);
                if (value != null) {
                    inputs.put(fieldId, value);
                }
            }
        }
        this.plugin.getMenuService().handleDialogAction(player, new Action(actionId, actionPayload, false), inputs);
    }

    private static String text(final CompoundTag tag, final String key) {
        final Tag value = tag.get(key);
        return value == null ? null : Compat.stringOf(value);
    }

    private static String readInput(final CompoundTag payload, final String key, final String type) {
        final Tag tag = payload.get(key);
        if (tag == null) {
            return null;
        }
        try {
            return switch (type) {
                case "b" -> {
                    if (tag instanceof final net.minecraft.nbt.NumericTag numeric) {
                        yield String.valueOf(Compat.intOf(numeric) != 0);
                    }
                    yield Compat.stringOf(tag);
                }
                case "i", "d" -> {
                    if (tag instanceof final net.minecraft.nbt.NumericTag numeric) {
                        final float f = Compat.floatOf(numeric);
                        yield "i".equals(type) ? String.valueOf(Math.round(f)) : Float.toString(f);
                    }
                    // wide-range numbers are rendered as text inputs
                    yield Compat.stringOf(tag).trim().replace(',', '.');
                }
                default -> Compat.stringOf(tag);
            };
        } catch (final RuntimeException wrongType) {
            return null;
        }
    }

    // ---- tokens / encoding ---------------------------------------------------------------------------------

    private String issueToken(final UUID player) {
        final byte[] bytes = new byte[12];
        this.random.nextBytes(bytes);
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        final String token = sb.toString();
        final Deque<String> deque = this.tokens.computeIfAbsent(player, id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(token);
            while (deque.size() > MAX_TOKENS) {
                deque.removeFirst();
            }
        }
        return token;
    }

    /** A token is valid for every button of the dialog it was issued with; one click invalidates older dialogs. */
    private boolean consumeToken(final UUID player, final String token) {
        final Deque<String> deque = this.tokens.get(player);
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            if (!deque.contains(token)) {
                return false;
            }
            while (!deque.isEmpty() && !deque.peekFirst().equals(token)) {
                deque.removeFirst();
            }
            deque.removeFirst();
            return true;
        }
    }

    private static String encodePayload(final Map<String, String> payload) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> e : payload.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> decodePayload(final String raw) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (final String pair : raw.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }
}
