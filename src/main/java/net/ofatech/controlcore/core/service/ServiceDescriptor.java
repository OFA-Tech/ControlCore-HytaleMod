package net.ofatech.controlcore.core.service;

import net.ofatech.controlcore.core.domain.enums.ServiceLifetime;
import net.ofatech.controlcore.core.domain.interfaces.ServiceFactory;

import java.util.Objects;

/**
 * Describes a service registration in the dependency injection container.
 * Contains the service type, implementation, and lifetime.
 */
public class ServiceDescriptor {
    private final Class<?> serviceType;
    private final ServiceFactory<?> factory;
    private final ServiceLifetime lifetime;

    private ServiceDescriptor(Class<?> serviceType, ServiceFactory<?> factory, ServiceLifetime lifetime) {
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType cannot be null");
        this.factory = Objects.requireNonNull(factory, "factory cannot be null");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime cannot be null");
    }

    /**
     * Creates a service descriptor with a factory function.
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @param lifetime The lifetime of the service
     * @return A new ServiceDescriptor
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor of(Class<T> serviceType, ServiceFactory<T> factory, ServiceLifetime lifetime) {
        return new ServiceDescriptor(serviceType, factory, lifetime);
    }

    /**
     * Creates a transient service descriptor with a concrete implementation.
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @return A new ServiceDescriptor with TRANSIENT lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createTransient(Class<T> serviceType, Class<? extends T> implementationType) {
        return of(serviceType, provider -> createInstance(implementationType), ServiceLifetime.TRANSIENT);
    }

    /**
     * Creates a transient service descriptor using a factory function.
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @return A new ServiceDescriptor with TRANSIENT lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createTransient(Class<T> serviceType, ServiceFactory<T> factory) {
        return of(serviceType, factory, ServiceLifetime.TRANSIENT);
    }

    /**
     * Creates a scoped service descriptor with a concrete implementation.
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @return A new ServiceDescriptor with SCOPED lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createScoped(Class<T> serviceType, Class<? extends T> implementationType) {
        return of(serviceType, provider -> createInstance(implementationType), ServiceLifetime.SCOPED);
    }

    /**
     * Creates a scoped service descriptor using a factory function.
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @return A new ServiceDescriptor with SCOPED lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createScoped(Class<T> serviceType, ServiceFactory<T> factory) {
        return of(serviceType, factory, ServiceLifetime.SCOPED);
    }

    /**
     * Creates a singleton service descriptor with a concrete implementation.
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @return A new ServiceDescriptor with SINGLETON lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createSingleton(Class<T> serviceType, Class<? extends T> implementationType) {
        return of(serviceType, provider -> createInstance(implementationType), ServiceLifetime.SINGLETON);
    }

    /**
     * Creates a singleton service descriptor using a factory function.
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @return A new ServiceDescriptor with SINGLETON lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createSingleton(Class<T> serviceType, ServiceFactory<T> factory) {
        return of(serviceType, factory, ServiceLifetime.SINGLETON);
    }

    /**
     * Creates a singleton service descriptor with an existing instance.
     * @param serviceType The service interface/abstract class type
     * @param instance The singleton instance
     * @return A new ServiceDescriptor with SINGLETON lifetime
     * @param <T> The service type
     */
    public static <T> ServiceDescriptor createSingleton(Class<T> serviceType, T instance) {
        return of(serviceType, provider -> instance, ServiceLifetime.SINGLETON);
    }

    private static <T> T createInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + type.getName(), e);
        }
    }

    public Class<?> getServiceType() {
        return serviceType;
    }

    public ServiceFactory<?> getFactory() {
        return factory;
    }

    public ServiceLifetime getLifetime() {
        return lifetime;
    }
}


