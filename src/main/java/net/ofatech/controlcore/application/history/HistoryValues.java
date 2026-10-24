package net.ofatech.controlcore.application.history;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;

public final class HistoryValues {

    private static final String SEPARATOR = "\u001f";

    public record Bind(String keys, String command, boolean runAsServer) {}

    public record Rule(String view, String edit) {}

    private HistoryValues() {}

    public static String bind(Optional<CustomKeyBind> bind) {
        return bind.map(found -> found.keys() + SEPARATOR + found.command() + SEPARATOR + found.runAsServer())
                .orElse("");
    }

    public static Optional<Bind> bind(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = value.split(SEPARATOR, -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(new Bind(parts[0], parts[1], Boolean.parseBoolean(parts[2])));
    }

    public static String rule(SettingPermission rule) {
        return rule.isInherit()
                ? ""
                : rule.view().orElse("") + SEPARATOR + rule.edit().orElse("");
    }

    public static Rule rule(String value) {
        if (value == null || value.isEmpty()) {
            return new Rule("", "");
        }
        String[] parts = value.split(SEPARATOR, -1);
        return new Rule(parts[0], parts.length > 1 ? parts[1] : "");
    }
}
