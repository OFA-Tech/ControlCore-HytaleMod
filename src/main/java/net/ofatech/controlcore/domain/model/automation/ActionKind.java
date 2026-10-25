package net.ofatech.controlcore.domain.model.automation;

import java.util.Locale;
import java.util.Optional;

public enum ActionKind {
    MESSAGE_PLAYER(true),
    MESSAGE_ALL(false),
    MESSAGE_ADMINS(false),
    TITLE_PLAYER(true),
    TITLE_ALL(false),
    COMMAND_SERVER(false),
    COMMAND_PLAYER(true),
    PROFILE(false),
    SET_SETTING(false),
    MOD_ACTION(false);

    private final boolean needsPlayer;

    ActionKind(boolean needsPlayer) {
        this.needsPlayer = needsPlayer;
    }

    public boolean needsPlayer() {
        return needsPlayer;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<ActionKind> of(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ActionKind kind : values()) {
            if (kind.name().equals(wanted)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
