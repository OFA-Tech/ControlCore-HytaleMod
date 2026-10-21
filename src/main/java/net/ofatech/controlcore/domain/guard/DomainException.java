package net.ofatech.controlcore.domain.guard;

/**
 * Thrown for programmer errors (broken invariants, such as a null or blank argument), never for expected
 * failures; those are returned as a {@link net.ofatech.controlcore.domain.result.Result}.
 */
public final class DomainException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what was wrong, for example {@code "key must not be blank"}
     */
    public DomainException(String message) {
        super(message);
    }
}
