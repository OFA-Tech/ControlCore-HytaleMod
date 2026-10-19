package net.ofatech.controlcore.application.runtime;

public final class InjectionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InjectionException(String message) {
        super(message);
    }

    public InjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
