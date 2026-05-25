package net.ofatech.controlcore.core.domain.interfaces;

/**
 * Factory function for creating service instances.
 * Can be used to lazily construct instances or provide custom initialization logic.
 *
 * @param <T> The type of service to create
 */
@FunctionalInterface
public interface ServiceFactory<T> {
    /**
     * Creates and returns a new instance of the service.
     * @param provider The service provider to allow for nested dependency resolution
     * @return A new instance of the service
     */
    T create(IServiceProvider provider);
}

