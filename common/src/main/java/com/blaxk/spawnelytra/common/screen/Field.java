/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import com.blaxk.spawnelytra.common.config.ConfigNumbers;
import com.blaxk.spawnelytra.common.text.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * An editable value. {@link #id()} is the zone key ({@code ZoneKeyTable}) / global key ({@code GlobalKeyTable}) /
 * form input name, and is used as the dialog input key. {@link #target()} says where an immediate change goes in
 * chat mode ({@link #setAction(String)}); dialog mode collects all inputs and submits them with the screen's
 * form button instead.
 *
 * <p>Dialog mapping: {@link Bool} → boolean input, {@link Number} → number range input (min/max/step, initial value),
 * {@link TextInput} → text input (max length), {@link Choice} → single option input, {@link ReadOnly} → plain text.
 * Chat mapping: see {@link #chatControls()}.</p>
 */
public sealed interface Field extends Element permits Field.Bool, Field.Number, Field.TextInput, Field.Choice, Field.ReadOnly {

    /** Where chat-mode changes are applied. */
    enum Scope {
        /** {@code /se zone set <zone> <key> <value>} */
        ZONE,
        /** {@code /se global set <key> <value>} */
        GLOBAL,
        /** Form-only input (no immediate command; e.g. the new-zone form). */
        FORM
    }

    /** @param zone zone name for {@link Scope#ZONE}, else {@code null} */
    record Target(Scope scope, String zone) {
        public static Target zone(final String zone) {
            return new Target(Scope.ZONE, zone);
        }

        public static final Target GLOBAL = new Target(Scope.GLOBAL, null);
        public static final Target FORM = new Target(Scope.FORM, null);
    }

    String id();

    Msg label();

    Target target();

    /** The current value in command syntax (what {@code /se zone set} would accept). */
    String value();

    /** The chat command/dialog action that sets this field to {@code value}; {@code null} for form-only fields. */
    default Action setAction(final String value) {
        return switch (this.target().scope()) {
            case ZONE -> Action.of(ActionIds.ZONE_SET, "zone", this.target().zone(), "key", this.id(), "value", value);
            case GLOBAL -> Action.of(ActionIds.GLOBAL_SET, "key", this.id(), "value", value);
            case FORM -> null;
        };
    }

    /**
     * Chat controls rendered after {@code label: value}: Bool → {@code [toggle]}; Number →
     * {@code [--] [-] [+] [++]} (absolute target values, clamped; disabled ends omitted); Choice → {@code [next]}
     * (cycles); TextInput → {@code [edit]} (suggest_command); ReadOnly → none.
     */
    default List<Button> chatControls() {
        final List<Button> out = new ArrayList<>();
        if (this.target().scope() == Scope.FORM) {
            return out;
        }
        switch (this) {
            case final Bool b -> out.add(new Button(Msg.of(b.checked() ? "state_on" : "state_off"), Msg.of("toggle_hover"),
                    this.setAction("toggle"), Button.Mode.CHAT_ONLY));
            case final Number n -> {
                final double[] deltas = {-n.largeStep(), -n.step(), n.step(), n.largeStep()};
                final String[] labels = {"menu_button_minus_large", "menu_button_minus", "menu_button_plus", "menu_button_plus_large"};
                for (int i = 0; i < deltas.length; i++) {
                    final double target = Math.max(n.min(), Math.min(n.max(), n.current() + deltas[i]));
                    if (Math.abs(target - n.current()) < 1e-9) {
                        continue;
                    }
                    final String v = n.integer() ? String.valueOf((long) Math.rint(target)) : ConfigNumbers.format(target);
                    out.add(new Button(Msg.of(labels[i]), Msg.of("menu_hover_set_value", "value", v), this.setAction(v), Button.Mode.CHAT_ONLY));
                }
            }
            case final Choice c -> out.add(new Button(Msg.of("menu_button_cycle"), Msg.of("menu_hover_cycle"),
                    this.setAction("next"), Button.Mode.CHAT_ONLY));
            case final TextInput t -> {
                final Action a = this.setAction(null);
                out.add(new Button(Msg.of("menu_button_edit_text"), Msg.of("menu_hover_edit_text"),
                        a == null ? null : a.suggesting(), Button.Mode.CHAT_ONLY));
            }
            case final ReadOnly r -> {
            }
        }
        return out;
    }

    record Bool(String id, Msg label, boolean checked, Target target) implements Field {
        @Override
        public String value() {
            return String.valueOf(this.checked);
        }
    }

    /**
     * @param integer integer-only (INT keys)
     */
    record Number(String id, Msg label, double current, double min, double max, double step, double largeStep,
                  boolean integer, Target target) implements Field {
        @Override
        public String value() {
            return this.integer ? String.valueOf((long) Math.rint(this.current)) : ConfigNumbers.format(this.current);
        }

        /** Alias of {@link #current()} for rendering code. */
        public double valueAsDouble() {
            return this.current;
        }
    }

    record TextInput(String id, Msg label, String text, int maxLength, Target target) implements Field {
        @Override
        public String value() {
            return this.text;
        }
    }

    /** One option of a {@link Choice}: the value (command syntax) and its display label. */
    record Option(String value, Msg label) {
    }

    record Choice(String id, Msg label, List<Option> options, String selected, Target target) implements Field {
        public Choice {
            options = List.copyOf(options);
        }

        @Override
        public String value() {
            return this.selected;
        }

        /** Display label of the selected option. */
        public Msg selectedLabel() {
            for (final Option o : this.options) {
                if (o.value().equals(this.selected)) {
                    return o.label();
                }
            }
            return Msg.literal(this.selected);
        }
    }

    record ReadOnly(String id, Msg label, Msg display) implements Field {
        @Override
        public Target target() {
            return Target.FORM;
        }

        @Override
        public String value() {
            return "";
        }
    }
}
