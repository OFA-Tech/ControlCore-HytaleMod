package net.ofatech.controlcore.domain.result;

import java.util.Objects;
import net.ofatech.controlcore.domain.model.text.Text;

/**
 * An expected failure, carried by a {@link Result.Failure}.
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

    /**
     * Creates an error.
     *
     * @param code stable machine-readable code, for example {@code "Greeting.PlayerNotFound"}
     * @param message what to tell the player
     * @param type failure category
     * @throws NullPointerException if any argument is null
     */
    public Error {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Creates an error with a fixed, untranslated message.
     *
     * @param code stable machine-readable code, for example {@code "Greeting.PlayerNotFound"}
     * @param description what to tell the player, used as is (see {@link Text#of(String)})
     * @param type failure category
     * @throws NullPointerException if any argument is null
     */
    public Error(String code, String description, ErrorType type) {
        this(code, Text.of(Objects.requireNonNull(description, "description")), type);
    }

    /**
     * Returns the message as English text, with placeholders filled in. Use a {@link
     * net.ofatech.controlcore.domain.model.text.Translator} to get it in a player's language.
     *
     * @return the message in English
     */
    public String description() {
        return message.fallbackText();
    }

    /**
     * Creates a general {@link ErrorType#FAILURE} error with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error failure(String code, String description) {
        return new Error(code, description, ErrorType.FAILURE);
    }

    /**
     * Creates a general {@link ErrorType#FAILURE} error.
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error failure(String code, Text message) {
        return new Error(code, message, ErrorType.FAILURE);
    }

    /**
     * Creates a {@link ErrorType#VALIDATION} error (invalid input) with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error validation(String code, String description) {
        return new Error(code, description, ErrorType.VALIDATION);
    }

    /**
     * Creates a {@link ErrorType#VALIDATION} error (invalid input).
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error validation(String code, Text message) {
        return new Error(code, message, ErrorType.VALIDATION);
    }

    /**
     * Creates a {@link ErrorType#NOT_FOUND} error with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error notFound(String code, String description) {
        return new Error(code, description, ErrorType.NOT_FOUND);
    }

    /**
     * Creates a {@link ErrorType#NOT_FOUND} error.
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error notFound(String code, Text message) {
        return new Error(code, message, ErrorType.NOT_FOUND);
    }

    /**
     * Creates a {@link ErrorType#CONFLICT} error with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error conflict(String code, String description) {
        return new Error(code, description, ErrorType.CONFLICT);
    }

    /**
     * Creates a {@link ErrorType#CONFLICT} error.
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error conflict(String code, Text message) {
        return new Error(code, message, ErrorType.CONFLICT);
    }

    /**
     * Creates a {@link ErrorType#FORBIDDEN} error with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error forbidden(String code, String description) {
        return new Error(code, description, ErrorType.FORBIDDEN);
    }

    /**
     * Creates a {@link ErrorType#FORBIDDEN} error.
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error forbidden(String code, Text message) {
        return new Error(code, message, ErrorType.FORBIDDEN);
    }

    /**
     * Creates a {@link ErrorType#UNAVAILABLE} error with a fixed message.
     *
     * @param code stable machine-readable code
     * @param description what to tell the player, not translated
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error unavailable(String code, String description) {
        return new Error(code, description, ErrorType.UNAVAILABLE);
    }

    /**
     * Creates a {@link ErrorType#UNAVAILABLE} error.
     *
     * @param code stable machine-readable code
     * @param message what to tell the player
     * @return the error
     * @throws NullPointerException if any argument is null
     */
    public static Error unavailable(String code, Text message) {
        return new Error(code, message, ErrorType.UNAVAILABLE);
    }
}
