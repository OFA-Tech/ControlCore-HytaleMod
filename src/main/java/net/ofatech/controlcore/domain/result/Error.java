package net.ofatech.controlcore.domain.result;

import java.util.Objects;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * An expected failure.
 *
 * @param code stable machine-readable code, for example {@code "Greeting.PlayerNotFound"}
 * @param description human-readable message (English)
 * @param type failure category
 * @param text the message as a translation key, shown in the player's language (since API 1.5.0)
 */
public record Error(String code, String description, ErrorType type, Optional<Text> text) {

    public Error {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
    }

    public Error(String code, String description, ErrorType type) {
        this(code, description, type, Optional.empty());
    }

    /**
     * @param key translation key of the message; {@link #description()} is its fallback
     * @param nameValuePairs placeholder names and values, alternating
     * @return a copy shown in the player's language
     * @since API 1.5.0
     */
    public Error localized(String key, Object... nameValuePairs) {
        return new Error(
                code, description, type, Optional.of(Text.key(key, description).with(nameValuePairs)));
    }

    /**
     * @return a copy shown in the player's language as {@code message}
     * @since API 1.5.0
     */
    public Error withText(Text message) {
        return new Error(code, description, type, Optional.of(message));
    }

    public static Error failure(String code, String description) {
        return new Error(code, description, ErrorType.FAILURE);
    }

    public static Error validation(String code, String description) {
        return new Error(code, description, ErrorType.VALIDATION);
    }

    public static Error notFound(String code, String description) {
        return new Error(code, description, ErrorType.NOT_FOUND);
    }

    public static Error conflict(String code, String description) {
        return new Error(code, description, ErrorType.CONFLICT);
    }

    public static Error forbidden(String code, String description) {
        return new Error(code, description, ErrorType.FORBIDDEN);
    }

    public static Error unavailable(String code, String description) {
        return new Error(code, description, ErrorType.UNAVAILABLE);
    }
}
