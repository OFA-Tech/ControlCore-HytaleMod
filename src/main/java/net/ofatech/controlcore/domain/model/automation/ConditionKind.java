package net.ofatech.controlcore.domain.model.automation;

import java.util.Locale;
import java.util.Optional;

public enum ConditionKind {
    HAS_PERMISSION,
    LACKS_PERMISSION,
    IN_WORLD,
    ONLINE_BETWEEN,
    TIME_BETWEEN;

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<ConditionKind> of(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ConditionKind kind : values()) {
            if (kind.name().equals(wanted)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
