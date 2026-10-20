package net.ofatech.controlcore.infrastructure.platforms.hytale.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.model.settings.SettingScope;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.settings.SettingsEditor;
import net.ofatech.controlcore.domain.result.Error;
import net.ofatech.controlcore.domain.result.Result;

/**
 * One list of settings in the control panel (server settings, or the viewer's preferences), without any Hytale
 * types: which rows to show and what each action does. {@link ControlPanelPage} draws it.
 */
final class SettingsSection {

    /** How a setting is edited. */
    enum Kind {
        /** Text field and Save button (integer, decimal, text). */
        TEXT,
        /** Check box, saved when clicked. */
        TOGGLE,
        /** Dropdown, saved when an option is picked. */
        CHOICE,
        /** Value shown as text; not editable. */
        INFO
    }

    /** One element of the list, in display order. */
    sealed interface Entry permits Heading, Row {}

    /** A category heading or an explanatory line. */
    record Heading(String text) implements Entry {}

    /** An editable setting. */
    record Row(Kind kind, SettingSnapshot snapshot) implements Entry {

        SettingId id() {
            return snapshot.id();
        }
    }

    private final SettingsEditor settings;
    private final SettingScope scope;
    private final Optional<PlayerId> player;
    private final String emptyText;
    private final boolean keys;

    SettingsSection(SettingsEditor settings, SettingScope scope, Optional<PlayerId> player, String emptyText) {
        this(settings, scope, player, emptyText, false);
    }

    private SettingsSection(
            SettingsEditor settings, SettingScope scope, Optional<PlayerId> player, String emptyText, boolean keys) {
        this.settings = settings;
        this.scope = scope;
        this.player = scope == SettingScope.PLAYER ? player : Optional.empty();
        this.emptyText = emptyText;
        this.keys = keys;
    }

    /**
     * A section of key bindings: the rows are key actions, the values key combinations. {@code PLAYER}: the viewer's
     * own keys; {@code SERVER}: the server's keys for everyone.
     */
    static SettingsSection keymaps(
            SettingsEditor keymaps, SettingScope scope, Optional<PlayerId> player, String emptyText) {
        return new SettingsSection(keymaps, scope, player, emptyText, true);
    }

    /** @return the hint shown in an empty text field */
    String placeholder(SettingSnapshot snapshot) {
        return keys
                ? "keys, for example ctrl+f or alt+3 | mouse_middle; none unbinds"
                : snapshot.definition().type().describe();
    }

    /** @return the description line under a row */
    String description(SettingSnapshot snapshot) {
        if (!keys) {
            return describe(snapshot);
        }
        SettingDefinition<?> definition = snapshot.definition();
        String details = (scope == SettingScope.SERVER ? "mod keys " : "server keys ") + definition.formattedDefault()
                + " · " + definition.id();
        return definition.description().isBlank() ? details : definition.description() + " (" + details + ")";
    }

    SettingScope scope() {
        return scope;
    }

    /** @return headings and rows, grouped by category */
    List<Entry> entries() {
        List<SettingSnapshot> snapshots = settings.snapshot(scope, player);
        List<Entry> entries = new ArrayList<>();
        if (snapshots.isEmpty()) {
            entries.add(new Heading(emptyText));
            return entries;
        }
        String category = null;
        for (SettingSnapshot snapshot : snapshots) {
            if (!snapshot.definition().category().equals(category)) {
                category = snapshot.definition().category();
                entries.add(new Heading(category));
            }
            entries.add(new Row(kindOf(snapshot), snapshot));
        }
        return entries;
    }

    /** Saves text typed into a field or picked from a dropdown. */
    Result<SettingSnapshot> save(String idText, String value) {
        return id(idText).flatMap(id -> settings.write(id, player, value == null ? "" : value));
    }

    /** Saves the state of a check box. */
    Result<SettingSnapshot> check(String idText, Boolean checked) {
        if (checked == null) {
            return Result.failure(Error.validation("ControlPanel.MissingValue", "The check box sent no value."));
        }
        return save(idText, checked.toString());
    }

    /** Returns a setting to its default. */
    Result<SettingSnapshot> reset(String idText) {
        return id(idText).flatMap(id -> settings.reset(id, player));
    }

    /** @return the setting's current value as this section shows it */
    Result<SettingSnapshot> current(SettingId id) {
        return settings.read(id, player);
    }

    /** Listens to changes of the settings this section edits (and others; filter with {@link #concerns}). */
    Subscription listen(Consumer<? super SettingChanged> listener) {
        return settings.onAnyChange(listener);
    }

    /** @return whether a change affects a row of this section (for preferences: the viewer's own) */
    boolean concerns(SettingChanged change) {
        if (keys && scope == SettingScope.PLAYER && change.scope() == SettingScope.SERVER) {
            return true; // the server's keys apply to players who did not choose their own
        }
        return change.scope() == scope
                && (scope == SettingScope.SERVER || change.player().equals(player));
    }

    static Kind kindOf(SettingSnapshot snapshot) {
        if (snapshot.access() == SettingAccess.READ_ONLY) {
            return Kind.INFO;
        }
        return switch (snapshot.definition().type()) {
            case SettingType.BooleanType _ -> Kind.TOGGLE;
            case SettingType.ChoiceType _ -> Kind.CHOICE;
            default -> Kind.TEXT;
        };
    }

    /** @return the description line under a row: description, type and default */
    static String describe(SettingSnapshot snapshot) {
        SettingDefinition<?> definition = snapshot.definition();
        String details = snapshot.access() == SettingAccess.READ_ONLY
                ? "read-only · " + definition.id()
                : definition.type().describe() + ", default " + shown(definition.formattedDefault()) + " · "
                        + definition.id();
        return definition.description().isBlank() ? details : definition.description() + " (" + details + ")";
    }

    /** @return the status line after a successful change */
    static String saved(SettingSnapshot snapshot) {
        return "Saved: " + snapshot.definition().displayName() + " = " + shown(snapshot.value())
                + (snapshot.overridden() ? "" : " (default)")
                + (snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART ? ". Applies after a restart." : "");
    }

    /** @return the value shown in a read-only row */
    static String shownValue(SettingSnapshot snapshot) {
        return shown(snapshot.value());
    }

    /** @return the status line after a change made outside this panel */
    static String changedElsewhere(SettingSnapshot snapshot) {
        return "Changed elsewhere: " + snapshot.definition().displayName() + " = " + shown(snapshot.value())
                + (snapshot.overridden() ? "" : " (default)");
    }

    static List<String> options(SettingSnapshot snapshot) {
        return snapshot.definition().type() instanceof SettingType.ChoiceType choice ? choice.options() : List.of();
    }

    private static String shown(String value) {
        return value.isEmpty() ? "(empty)" : value;
    }

    private static Result<SettingId> id(String text) {
        return SettingId.parse(text);
    }
}
