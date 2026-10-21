package net.ofatech.controlcore.domain.result;

import java.util.Objects;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * An expected failure.
 *
 * <pre>{@code
 * Error.notFound("Homes.Unknown", "There is no home " + name + ".")                       // fixed text
 * Error.notFound("Homes.Unknown", Text.key("homes.errors.unknown", "There is no home {name}.").with("name", name))
 * }</pre>
 *
 * @param code stable machine-readable code, for example {@code "Greeting.PlayerNotFound"}
 * @param message what to tell the player; a {@link Text#key translation key} is shown in their language
 * @param type failure category
 */
public record Error(String code, Text message, ErrorType type) {

    public Error {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(type, "type");
    }

    public Error(String code, String description, ErrorType type) {
        this(code, Text.of(Objects.requireNonNull(description, "description")), type);
    }

    /** @return the message in English */
    public String description() {
        return message.fallbackText();
    }

    public static Error failure(String code, String description) {
        return new Error(code, description, ErrorType.FAILURE);
    }

    public static Error failure(String code, Text message) {
        return new Error(code, message, ErrorType.FAILURE);
    }

    public static Error validation(String code, String description) {
        return new Error(code, description, ErrorType.VALIDATION);
    }

    public static Error validation(String code, Text message) {
        return new Error(code, message, ErrorType.VALIDATION);
    }

    public static Error notFound(String code, String description) {
        return new Error(code, description, ErrorType.NOT_FOUND);
    }

    public static Error notFound(String code, Text message) {
        return new Error(code, message, ErrorType.NOT_FOUND);
    }

    public static Error conflict(String code, String description) {
        return new Error(code, description, ErrorType.CONFLICT);
    }

    public static Error conflict(String code, Text message) {
        return new Error(code, message, ErrorType.CONFLICT);
    }

    public static Error forbidden(String code, String description) {
        return new Error(code, description, ErrorType.FORBIDDEN);
    }

    public static Error forbidden(String code, Text message) {
        return new Error(code, message, ErrorType.FORBIDDEN);
    }

    public static Error unavailable(String code, String description) {
        return new Error(code, description, ErrorType.UNAVAILABLE);
    }

    public static Error unavailable(String code, Text message) {
        return new Error(code, message, ErrorType.UNAVAILABLE);
    }
}
