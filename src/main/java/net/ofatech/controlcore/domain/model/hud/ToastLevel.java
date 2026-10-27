package net.ofatech.controlcore.domain.model.hud;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** How important a toast or warning is; decides its colour. */
public enum ToastLevel {
    /** Plain information. */
    INFO,
    /** Something worked. */
    SUCCESS,
    /** Something needs attention. */
    WARNING,
    /** Something went wrong. */
    ERROR;

    /**
     * Returns the lower-case id used in settings and rule values.
     *
     * @return the id, for example {@code "warning"}
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds a level by its id, ignoring case.
     *
     * @param text the id, for example {@code "success"}
     * @return the level, or empty when the text is not a level
     */
    public static Optional<ToastLevel> of(String text) {
        return Arrays.stream(values())
                .filter(level -> level.id().equalsIgnoreCase(text == null ? "" : text.strip()))
                .findFirst();
    }
}
