package net.ofatech.controlcore.domain.guard;

/** Thrown for programmer errors (broken invariants), never for expected failures. */
public final class DomainException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }
}
