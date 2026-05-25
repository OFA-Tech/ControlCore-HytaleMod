package net.ofatech.controlcore.core.domain.interfaces;

import java.util.Optional;

/**
 * Provides services registered in the dependency injection container.
 */
public interface IServiceProvider {
    /**
     * Gets a service instance of the specified type.
     * @param serviceType The type of service to retrieve
     * @return An Optional containing the service instance, or empty if not found
     * @param <T> The type of service
     */
    <T> Optional<T> getService(Class<T> serviceType);

    /**
     * Gets a required service instance of the specified type.
     * @param serviceType The type of service to retrieve
     * @return The service instance
     * @throws IllegalArgumentException if the service is not found
     * @param <T> The type of service
     */
    <T> T getRequiredService(Class<T> serviceType);
}

