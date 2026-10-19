package net.ofatech.controlcore.application.settings;

import java.util.List;
import java.util.Locale;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingSnapshot;

/** Chat text for the settings commands. */
final class SettingsText {

    private SettingsText() {}

    static String list(String title, List<SettingSnapshot> snapshots, String emptyText) {
        if (snapshots.isEmpty()) {
            return emptyText;
        }
        StringBuilder text =
                new StringBuilder(title).append(" (").append(snapshots.size()).append("):");
        String category = null;
        for (SettingSnapshot snapshot : snapshots) {
            if (!snapshot.definition().category().equals(category)) {
                category = snapshot.definition().category();
                text.append("\n[").append(category).append(']');
            }
            text.append("\n  ").append(line(snapshot));
        }
        return text.toString();
    }

    static String line(SettingSnapshot snapshot) {
        return snapshot.id() + " = " + shown(snapshot.value()) + (snapshot.overridden() ? "" : " (default)");
    }

    static String details(SettingSnapshot snapshot) {
        SettingDefinition<?> definition = snapshot.definition();
        StringBuilder text = new StringBuilder(definition.displayName())
                .append(" (")
                .append(definition.id())
                .append(")");
        if (!definition.description().isBlank()) {
            text.append("\n  ").append(definition.description());
        }
        text.append("\n  Value: ").append(shown(snapshot.value())).append(snapshot.overridden() ? "" : " (default)");
        text.append("\n  Default: ").append(shown(definition.formattedDefault()));
        text.append("\n  Type: ").append(definition.type().describe());
        text.append("\n  Scope: ").append(definition.scope().name().toLowerCase(Locale.ROOT));
        return text.toString();
    }

    static String changed(SettingSnapshot snapshot) {
        return snapshot.id() + " is now " + shown(snapshot.value()) + (snapshot.overridden() ? "" : " (default)") + ".";
    }

    static String shown(String value) {
        return value.isEmpty() ? "(empty)" : value;
    }
}
