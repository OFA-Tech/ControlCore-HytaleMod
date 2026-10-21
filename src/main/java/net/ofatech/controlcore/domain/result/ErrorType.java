package net.ofatech.controlcore.domain.result;

/** Category of an expected failure; see the matching factory methods on {@link Error}. */
public enum ErrorType {
    /** A general failure that fits no other category. */
    FAILURE,
    /** The input is invalid, for example a value out of range. */
    VALIDATION,
    /** Something that was asked for does not exist. */
    NOT_FOUND,
    /** The operation clashes with the current state, for example a duplicate. */
    CONFLICT,
    /** The caller is not allowed to do this. */
    FORBIDDEN,
    /** Something needed is not available right now. */
    UNAVAILABLE
}
