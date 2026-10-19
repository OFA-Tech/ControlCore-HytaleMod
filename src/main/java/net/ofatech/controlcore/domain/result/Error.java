package net.ofatech.controlcore.domain.result;

import java.util.Objects;

/**
 * An expected failure.
 *
 * @param code stable machine-readable code, for example {@code "Greeting.PlayerNotFound"}
 * @param description human-readable message
 * @param type failure category
 */
public record Error(String code, String description, ErrorType type) {

    public Error {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(type, "type");
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
