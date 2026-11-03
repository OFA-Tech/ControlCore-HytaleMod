package net.ofatech.controlcore.domain.model.keys;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Whether pressing a key action's keys still does what those keys do in the game. Read and changed with
 * {@link net.ofatech.controlcore.api.KeymapsApi#gameActionMode(net.ofatech.controlcore.domain.model.settings.SettingId)}.
 *
 * @since API 2.4.0
 */
public enum GameActionMode {
    /** Follow the server setting {@code controlcore:keys-keep-game-action}. */
    SERVER_SETTING("server"),
    /** The game action happens too. */
    KEEP("keep"),
    /** Only the key action runs; the game action does not happen. */
    REPLACE("replace");

    private final String id;

    GameActionMode(String id) {
        this.id = id;
    }

    /**
     * Returns the mode's id, as typed in commands.
     *
     * @return {@code server}, {@code keep} or {@code replace}
     */
    public String id() {
        return id;
    }

    /**
     * Finds a mode by its id.
     *
     * @param text the id, for example {@code keep}; case and surrounding spaces do not matter
     * @return the mode, or empty if {@code text} is {@code null} or no mode has that id
     */
    public static Optional<GameActionMode> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(mode -> mode.id.equals(wanted)).findFirst();
    }
}
