/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.dialog;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.common.screen.Action;
import com.blaxk.spawnelytra.common.screen.ActionIds;
import com.blaxk.spawnelytra.common.screen.Button;
import com.blaxk.spawnelytra.common.screen.Element;
import com.blaxk.spawnelytra.common.screen.Field;
import com.blaxk.spawnelytra.common.screen.Screen;
import com.blaxk.spawnelytra.menu.DialogBridge;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.Texts;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper Dialog API renderer (Paper 1.21.6+). Each button is a {@code dynamic/custom} click action with an id in the
 * {@code spawnelytra:} namespace; its additions carry the action payload, the field layout and a per-player random
 * token. Clicks with unknown tokens (forged or from an old session) are ignored; the action itself is re-validated
 * (permission, zone existence) by {@link com.blaxk.spawnelytra.menu.MenuService#handleDialogAction}.
 */
public final class PaperDialogBridge implements DialogBridge, Listener {
    private static final String TOKEN = "se_token";
    private static final String PAYLOAD = "se_payload";
    private static final String FIELDS = "se_fields";
    private static final int MAX_TOKENS = 16;
    private static final TextColor MUTED = TextColor.color(0xaaa8a8);

    private final Main plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Deque<String>> tokens = new ConcurrentHashMap<>();

    public PaperDialogBridge(final Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        HandlerList.unregisterAll(this);
        this.tokens.clear();
    }

    // ---- rendering --------------------------------------------------------------------------------------------------

    @Override
    public boolean show(final Player player, final Screen screen) {
        final String token = this.issueToken(player.getUniqueId());
        final List<Field> fields = screen.fields();
        final String fieldLayout = encodeFields(fields);

        final List<DialogBody> body = new ArrayList<>();
        final List<DialogInput> inputs = new ArrayList<>();
        final List<ActionButton> rowButtons = new ArrayList<>();
        int rowColumns = 0;
        int index = 0;
        for (final Element element : screen.body()) {
            switch (element) {
                case final Element.Text t -> body.add(DialogBody.plainMessage(Texts.render(t.text()), 300));
                case final Element.Heading h -> body.add(DialogBody.plainMessage(Texts.render(h.text()), 300));
                case final Element.Row row -> {
                    body.add(DialogBody.plainMessage(Texts.render(row.text()), 300));
                    final List<Button> visible = row.buttons().stream().filter(b -> b.mode().visible(true)).toList();
                    rowColumns = Math.max(rowColumns, visible.size());
                    boolean first = true;
                    for (final Button b : visible) {
                        // The first button of a row carries the row id so the grid stays readable.
                        final Component label = first
                                ? Component.text(row.id() + " · ").append(Texts.render(b.label()))
                                : Texts.render(b.label());
                        rowButtons.add(this.button(b, label, token, fieldLayout, 120));
                        first = false;
                    }
                }
                case final Field.ReadOnly r -> body.add(DialogBody.plainMessage(
                        Texts.render(r.label()).append(Component.text(": ", MUTED)).append(Texts.render(r.display())), 300));
                case final Field f -> {
                    inputs.add(this.input(f, "f" + index));
                    index++;
                }
            }
        }

        final List<ActionButton> footer = new ArrayList<>();
        for (final Button b : screen.buttons(true)) {
            footer.add(this.button(b, Texts.render(b.label()), token, fieldLayout, 150));
        }
        final ActionButton exit = screen.exit() == null ? null
                : this.button(screen.exit(), Texts.render(screen.exit().label()), token, fieldLayout, 150);

        final DialogBase base = DialogBase.builder(Texts.render(screen.title()))
                .canCloseWithEscape(true)
                .body(body)
                .inputs(inputs)
                .build();

        final DialogType type;
        switch (screen.id()) {
            case DELETE_CONFIRM -> {
                final ActionButton yes = footer.isEmpty() ? exit : footer.getFirst();
                final ActionButton no = footer.size() > 1 ? footer.get(1) : exit;
                type = DialogType.confirmation(yes, no);
            }
            case STATS -> type = exit != null ? DialogType.notice(exit) : DialogType.notice();
            default -> {
                // Grid: one row per zone (every row has the same number of buttons), then the footer buttons.
                final List<ActionButton> all = new ArrayList<>(rowButtons);
                all.addAll(footer);
                final int columns = rowColumns > 0 ? rowColumns : Math.min(3, Math.max(1, footer.size()));
                type = DialogType.multiAction(all, null, columns);
            }
        }
        final Dialog dialog = Dialog.create(builder -> builder.empty().base(base).type(type));
        player.showDialog(dialog);
        return true;
    }

    private DialogInput input(final Field field, final String key) {
        final Component label = Texts.render(field.label());
        return switch (field) {
            case final Field.Bool b -> DialogInput.bool(key, label).initial(b.checked()).build();
            case final Field.Number n -> {
                final float min = (float) n.min();
                final float max = (float) n.max();
                float step = (float) (n.step() > 0 ? n.step() : (n.integer() ? 1 : 0.1));
                final float initial = (float) Math.max(n.min(), Math.min(n.max(), n.current()));
                // Vanilla requires (max - min) to be a multiple of the step for a usable slider; very wide ranges
                // (priority, y) fall back to a text input so any value can still be typed.
                if (!Float.isFinite(min) || !Float.isFinite(max) || (max - min) / step > 1000) {
                    yield DialogInput.text(key, label).initial(n.value()).maxLength(16).build();
                }
                yield DialogInput.numberRange(key, label, min, max).step(step).initial(initial).width(300).build();
            }
            case final Field.Choice c -> {
                final List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
                boolean any = false;
                for (final Field.Option o : c.options()) {
                    final boolean selected = o.value().equals(c.selected());
                    any |= selected;
                    options.add(SingleOptionDialogInput.OptionEntry.create(o.value(), Texts.render(o.label()), selected));
                }
                if (!any && !options.isEmpty()) {
                    final SingleOptionDialogInput.OptionEntry first = options.getFirst();
                    options.set(0, SingleOptionDialogInput.OptionEntry.create(first.id(), first.display(), true));
                }
                yield DialogInput.singleOption(key, label, options).width(300).build();
            }
            case final Field.TextInput t -> DialogInput.text(key, label).initial(t.text() == null ? "" : t.text())
                    .maxLength(Math.max(1, t.maxLength())).width(300).build();
            case final Field.ReadOnly r -> DialogInput.text(key, label).initial("").build();
        };
    }

    private ActionButton button(final Button button, final Component label, final String token, final String fieldLayout, final int width) {
        final Action action = button.action();
        final ActionButton.Builder builder = ActionButton.builder(label).width(width);
        if (button.tooltip() != null) {
            builder.tooltip(Texts.render(button.tooltip()));
        }
        if (action != null && !ActionIds.CLOSE.equals(action.id())) {
            final String snbt = "{" + TOKEN + ":\"" + token + "\"," + PAYLOAD + ":\"" + encodePayload(action.payload())
                    + "\"," + FIELDS + ":\"" + (ActionIds.isFormSubmit(action.id()) ? fieldLayout : "") + "\"}";
            builder.action(DialogAction.customClick(Key.key(action.id()), BinaryTagHolder.binaryTagHolder(snbt)));
        }
        return builder.build();
    }

    // ---- click handling ----------------------------------------------------------------------------------------

    @EventHandler
    public void onCustomClick(final PlayerCustomClickEvent event) {
        final Key id = event.getIdentifier();
        if (!ActionIds.NAMESPACE.equals(id.namespace())) {
            return;
        }
        if (!(event.getCommonConnection() instanceof final PlayerGameConnection connection)) {
            return;
        }
        final Player player = connection.getPlayer();
        final DialogResponseView view = event.getDialogResponseView();
        if (player == null || view == null) {
            return;
        }
        final String token = view.getText(TOKEN);
        if (token == null || !this.consumeToken(player.getUniqueId(), token)) {
            return; // stale or forged
        }
        final String actionId = id.asString();
        if (!ActionIds.isKnown(actionId)) {
            return;
        }
        final Map<String, String> payload = decodePayload(view.getText(PAYLOAD));
        final Map<String, String> inputs = new HashMap<>();
        final String layout = view.getText(FIELDS);
        if (layout != null && !layout.isEmpty()) {
            final String[] entries = layout.split(",");
            for (int i = 0; i < entries.length; i++) {
                final int colon = entries[i].indexOf(':');
                if (colon < 0) {
                    continue;
                }
                final String type = entries[i].substring(0, colon);
                final String fieldId = entries[i].substring(colon + 1);
                final String value = readInput(view, "f" + i, type);
                if (value != null) {
                    inputs.put(fieldId, value);
                }
            }
        }
        final Action action = new Action(actionId, payload, false);
        // The event may fire off the player's thread (network thread); hop to the player's owner thread.
        SchedulerUtil.runForEntity(this.plugin, player, () -> {
            if (player.isOnline()) {
                this.plugin.getMenuService().handleDialogAction(player, action, inputs);
            }
        });
    }

    private static String readInput(final DialogResponseView view, final String key, final String type) {
        try {
            return switch (type) {
                case "b" -> {
                    final Boolean b = view.getBoolean(key);
                    yield b == null ? null : b.toString();
                }
                case "i", "d" -> {
                    final Float f = view.getFloat(key);
                    if (f == null) {
                        // wide-range numbers are rendered as text inputs
                        final String text = view.getText(key);
                        yield text == null ? null : text.trim().replace(',', '.');
                    }
                    yield "i".equals(type) ? String.valueOf(Math.round(f)) : Float.toString(f);
                }
                default -> view.getText(key);
            };
        } catch (final RuntimeException wrongType) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.tokens.remove(event.getPlayer().getUniqueId());
    }

    // ---- tokens / encoding ----------------------------------------------------------------------------------------

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
            // drop every token issued before this one (older dialogs are stale now)
            while (!deque.isEmpty() && !deque.peekFirst().equals(token)) {
                deque.removeFirst();
            }
            deque.removeFirst();
            return true;
        }
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
