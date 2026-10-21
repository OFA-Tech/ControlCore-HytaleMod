package net.ofatech.controlcore.domain.guard;

/** Argument checks that throw a {@link DomainException} when an invariant is broken. */
public final class Guard {

    private Guard() {}

    /**
     * Checks that a value is not null.
     *
     * @param value the value to check
     * @param name the argument name used in the exception message, for example {@code "key"}
     * @param <T> value type
     * @return {@code value}, so the check can be used inline
     * @throws DomainException if {@code value} is null
     */
    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new DomainException(name + " must not be null");
        }
        return value;
    }

    /**
     * Checks that a string is not null, empty or only whitespace.
     *
     * @param value the string to check; it is returned as is, not stripped
     * @param name the argument name used in the exception message, for example {@code "key"}
     * @return {@code value}, so the check can be used inline
     * @throws DomainException if {@code value} is null or blank
     */
    public static String notBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(name + " must not be blank");
        }
        return value;
    }
}
