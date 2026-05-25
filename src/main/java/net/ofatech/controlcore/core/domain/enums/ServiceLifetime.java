package net.ofatech.controlcore.core.domain.enums;

/**
 * Defines the lifetime of a service in the dependency injection container.
 */
public enum ServiceLifetime {
    /**
     * A new instance is created every time the service is requested.
     */
    TRANSIENT,

    /**
     * A single instance is created per scope.
     * In this implementation, we use a single scope for the entire application.
     */
    SCOPED,

    /**
     * A single instance is created for the entire application lifetime.
     */
    SINGLETON
}

