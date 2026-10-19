package net.ofatech.controlcore.domain.guard;

public final class Guard {

    private Guard() {}

    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new DomainException(name + " must not be null");
        }
        return value;
    }

    public static String notBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(name + " must not be blank");
        }
        return value;
    }
}
