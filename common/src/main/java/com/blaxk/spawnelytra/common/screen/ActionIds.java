/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.screen;

import java.util.Set;

/**
 * All action ids (custom click action ids of dialogs) and their chat command equivalents.
 * Payload keys: {@code zone}, {@code key}, {@code value}, {@code context} ({@code overview|editor}), {@code player}.
 */
public enum ActionIds {
    ;

    public static final String NAMESPACE = "spawnelytra";
    public static final String COMMAND = "/se";

    /** Open the overview. Chat: {@code /se}. */
    public static final String OVERVIEW = "spawnelytra:overview";
    /** Open zone settings {zone}. Chat: {@code /se zone settings <zone>}. */
    public static final String ZONE_SETTINGS = "spawnelytra:zone_settings";
    /** Enter the in-world editor {zone}. Chat: {@code /se zone edit <zone>}. */
    public static final String ZONE_EDIT = "spawnelytra:zone_edit";
    /** Teleport {zone}. Chat: {@code /se zone tp <zone>}. */
    public static final String ZONE_TP = "spawnelytra:zone_tp";
    /** Enable {zone}. Chat: {@code /se zone enable <zone>}. */
    public static final String ZONE_ENABLE = "spawnelytra:zone_enable";
    /** Disable {zone}. Chat: {@code /se zone disable <zone>}. */
    public static final String ZONE_DISABLE = "spawnelytra:zone_disable";
    /** Ask for delete confirmation {zone}. Chat: {@code /se zone delete <zone>}. */
    public static final String ZONE_DELETE = "spawnelytra:zone_delete";
    /** Confirmed delete {zone}. Chat: {@code /se zone delete <zone> confirm}. */
    public static final String ZONE_DELETE_CONFIRM = "spawnelytra:zone_delete_confirm";
    /** Open the "new zone" form. Chat: suggest {@code /se zone create }. */
    public static final String ZONE_CREATE_FORM = "spawnelytra:zone_create_form";
    /** Form submit: inputs {@code name}, {@code shape}. Chat: {@code /se zone create <name> <shape>}. */
    public static final String ZONE_CREATE = "spawnelytra:zone_create";
    /** Set one zone key {zone, key, value}. Chat: {@code /se zone set <zone> <key> <value>}. */
    public static final String ZONE_SET = "spawnelytra:zone_set";
    /**
     * Form submit of the zone settings dialog {zone, context}: all field ids (= zone keys) as inputs.
     * Context {@code overview} saves to config immediately, {@code editor} updates the draft only.
     */
    public static final String ZONE_SETTINGS_SAVE = "spawnelytra:zone_settings_save";
    /** Open global settings. Chat: {@code /se global}. */
    public static final String GLOBAL_SETTINGS = "spawnelytra:global_settings";
    /** Set one global key {key, value}. Chat: {@code /se global set <key> <value>}. */
    public static final String GLOBAL_SET = "spawnelytra:global_set";
    /** Form submit of the global settings dialog: all field ids (= global keys) as inputs. */
    public static final String GLOBAL_SETTINGS_SAVE = "spawnelytra:global_settings_save";
    /** The 1.5 language/style menu. Chat: {@code /se settings}. */
    public static final String LANGUAGE_STYLE = "spawnelytra:language_style";
    /** Show stats {player?}. Chat: {@code /se stats [player]}. */
    public static final String STATS = "spawnelytra:stats";
    /** Close the dialog / do nothing (chat: no command). */
    public static final String CLOSE = "spawnelytra:close";

    private static final Set<String> KNOWN = Set.of(ActionIds.OVERVIEW, ActionIds.ZONE_SETTINGS, ActionIds.ZONE_EDIT,
            ActionIds.ZONE_TP, ActionIds.ZONE_ENABLE, ActionIds.ZONE_DISABLE, ActionIds.ZONE_DELETE,
            ActionIds.ZONE_DELETE_CONFIRM, ActionIds.ZONE_CREATE_FORM, ActionIds.ZONE_CREATE, ActionIds.ZONE_SET,
            ActionIds.ZONE_SETTINGS_SAVE, ActionIds.GLOBAL_SETTINGS, ActionIds.GLOBAL_SET, ActionIds.GLOBAL_SETTINGS_SAVE,
            ActionIds.LANGUAGE_STYLE, ActionIds.STATS, ActionIds.CLOSE);

    private static final Set<String> FORMS = Set.of(ActionIds.ZONE_CREATE, ActionIds.ZONE_SETTINGS_SAVE, ActionIds.GLOBAL_SETTINGS_SAVE);

    public static boolean isKnown(final String id) {
        return id != null && ActionIds.KNOWN.contains(id);
    }

    /** Dialog submits that carry the form's input values. */
    public static boolean isFormSubmit(final String id) {
        return ActionIds.FORMS.contains(id);
    }

    /** {@code true} if the action needs {@code spawnelytra.admin} (everything except close and own stats). */
    public static boolean requiresAdmin(final Action action) {
        if (ActionIds.CLOSE.equals(action.id())) {
            return false;
        }
        if (ActionIds.STATS.equals(action.id())) {
            return false; // platform checks spawnelytra.stats / spawnelytra.stats.others
        }
        return true;
    }

    /** Chat command for an action ({@code null} for close / form submits). */
    static String toCommand(final Action a) {
        final String zone = a.get("zone");
        return switch (a.id()) {
            case OVERVIEW -> ActionIds.COMMAND;
            case ZONE_SETTINGS -> ActionIds.COMMAND + " zone settings " + zone;
            case ZONE_EDIT -> ActionIds.COMMAND + " zone edit " + zone;
            case ZONE_TP -> ActionIds.COMMAND + " zone tp " + zone;
            case ZONE_ENABLE -> ActionIds.COMMAND + " zone enable " + zone;
            case ZONE_DISABLE -> ActionIds.COMMAND + " zone disable " + zone;
            case ZONE_DELETE -> ActionIds.COMMAND + " zone delete " + zone;
            case ZONE_DELETE_CONFIRM -> ActionIds.COMMAND + " zone delete " + zone + " confirm";
            case ZONE_CREATE_FORM -> ActionIds.COMMAND + " zone create ";
            case ZONE_CREATE -> ActionIds.COMMAND + " zone create " + a.get("name") + (a.get("shape") == null ? "" : " " + a.get("shape"));
            case ZONE_SET -> ActionIds.COMMAND + " zone set " + zone + " " + a.get("key") + (a.get("value") == null ? " " : " " + a.get("value"));
            case GLOBAL_SETTINGS -> ActionIds.COMMAND + " global";
            case GLOBAL_SET -> ActionIds.COMMAND + " global set " + a.get("key") + (a.get("value") == null ? " " : " " + a.get("value"));
            case LANGUAGE_STYLE -> ActionIds.COMMAND + " settings";
            case STATS -> ActionIds.COMMAND + " stats" + (a.get("player") == null ? "" : " " + a.get("player"));
            default -> null;
        };
    }
}
