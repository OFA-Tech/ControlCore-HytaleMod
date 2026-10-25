package net.ofatech.controlcore.domain.model.keys;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum GameActionMode {
    SERVER_SETTING("server"),
    KEEP("keep"),
    REPLACE("replace");

    private final String id;

    GameActionMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<GameActionMode> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(mode -> mode.id.equals(wanted)).findFirst();
    }
}
