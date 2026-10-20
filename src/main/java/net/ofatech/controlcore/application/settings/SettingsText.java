package net.ofatech.controlcore.application.settings;

import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Translator;

/** Chat text for the settings commands, in the reader's language. */
public final class SettingsText {

    private SettingsText() {}

    /**
     * @param title the list's title, already translated
     * @param emptyText shown when there is nothing to list, already translated
     */
    static String list(Translator tr, String title, List<SettingSnapshot> snapshots, String emptyText) {
        if (snapshots.isEmpty()) {
            return emptyText;
        }
        StringBuilder text =
                new StringBuilder(tr.t("controlcore.settings.list.title", "title", title, "count", snapshots.size()));
        String category = null;
        for (SettingSnapshot snapshot : snapshots) {
            if (!snapshot.definition().category().equals(category)) {
                category = snapshot.definition().category();
                text.append("\n[")
                        .append(tr.translate(snapshot.definition().categoryText()))
                        .append(']');
            }
            text.append("\n  ").append(line(tr, snapshot));
        }
        return text.toString();
    }

    /** @return for example {@code controlcore:welcome-message = Hi (default)} */
    public static String line(Translator tr, SettingSnapshot snapshot) {
        return tr.t(
                snapshot.overridden() ? "controlcore.settings.line" : "controlcore.settings.line_default",
                "id",
                snapshot.id(),
                "value",
                value(tr, snapshot.definition(), snapshot.value()));
    }

    static String details(Translator tr, SettingSnapshot snapshot) {
        SettingDefinition<?> definition = snapshot.definition();
        StringBuilder text = new StringBuilder(tr.translate(definition.nameText()))
                .append(" (")
                .append(definition.id())
                .append(")");
        String description = tr.translate(definition.descriptionText());
        if (!description.isBlank()) {
            text.append("\n  ").append(description);
        }
        String value = value(tr, definition, snapshot.value());
        text.append("\n  ")
                .append(tr.t(
                        snapshot.overridden()
                                ? "controlcore.settings.details.value"
                                : "controlcore.settings.details.value_default",
                        "value",
                        value));
        text.append("\n  ")
                .append(tr.t(
                        "controlcore.settings.details.default",
                        "value",
                        value(tr, definition, definition.formattedDefault())));
        text.append("\n  ")
                .append(tr.t(
                        "controlcore.settings.details.type",
                        "type",
                        tr.translate(definition.type().describeText())));
        text.append("\n  ")
                .append(tr.t(
                        "controlcore.settings.details.scope",
                        "scope",
                        tr.t("controlcore.settings.scope."
                                + definition.scope().name().toLowerCase(Locale.ROOT))));
        return text.toString();
    }

    /** @return for example {@code controlcore:welcome-message is now Hi.} */
    public static String changed(Translator tr, SettingSnapshot snapshot) {
        return tr.t(
                snapshot.overridden() ? "controlcore.settings.changed" : "controlcore.settings.changed_default",
                "id",
                snapshot.id(),
                "value",
                value(tr, snapshot.definition(), snapshot.value()));
    }

    /** @return {@code value} as players read it: {@code (empty)}, or a choice's option in their language */
    public static String value(Translator tr, SettingDefinition<?> definition, String value) {
        if (value.isEmpty()) {
            return tr.t("controlcore.settings.empty");
        }
        return definition.type() instanceof SettingType.ChoiceType
                ? tr.translate(definition.labels().option(value))
                : value;
    }
}
