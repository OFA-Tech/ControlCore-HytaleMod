package net.ofatech.controlcore.application.settings;

import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.domain.model.settings.SettingAccess;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Translator;

public final class SettingsText {

    private SettingsText() {}

    static String list(Translator tr, String title, List<SettingSnapshot> snapshots, String emptyText) {
        if (snapshots.isEmpty()) {
            return emptyText;
        }
        StringBuilder text =
                new StringBuilder(tr.t("controlcore.settings.list.title", "title", title, "count", snapshots.size()));
        String category = null;
        for (SettingSnapshot snapshot : snapshots) {
            if (!snapshot.definition().heading().equals(category)) {
                category = snapshot.definition().heading();
                text.append("\n[").append(snapshot.definition().heading(tr)).append(']');
            }
            text.append("\n  ").append(line(tr, snapshot));
        }
        return text.toString();
    }

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
        if (snapshot.access() != SettingAccess.EDITABLE) {
            text.append("\n  ")
                    .append(tr.t(
                            snapshot.access() == SettingAccess.READ_ONLY
                                    ? "controlcore.settings.details.read_only"
                                    : "controlcore.settings.applies_after_restart"));
        }
        text.append("\n  ")
                .append(tr.t(
                        "controlcore.settings.details.scope",
                        "scope",
                        tr.t("controlcore.settings.scope."
                                + definition.scope().name().toLowerCase(Locale.ROOT))));
        return text.toString();
    }

    public static String changed(Translator tr, SettingSnapshot snapshot) {
        String text = tr.t(
                snapshot.overridden() ? "controlcore.settings.changed" : "controlcore.settings.changed_default",
                "id",
                snapshot.id(),
                "value",
                value(tr, snapshot.definition(), snapshot.value()));
        return snapshot.access() == SettingAccess.APPLIES_AFTER_RESTART
                ? text + " " + tr.t("controlcore.settings.applies_after_restart")
                : text;
    }

    public static String value(Translator tr, SettingDefinition<?> definition, String value) {
        if (value.isEmpty()) {
            return tr.t("controlcore.settings.empty");
        }
        return definition.type() instanceof SettingType.ChoiceType ? tr.translate(definition.optionText(value)) : value;
    }
}
