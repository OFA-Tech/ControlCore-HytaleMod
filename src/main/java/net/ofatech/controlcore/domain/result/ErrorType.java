package net.ofatech.controlcore.domain.result;

/** Category of an expected failure. */
public enum ErrorType {
    FAILURE,
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    UNAVAILABLE
}
