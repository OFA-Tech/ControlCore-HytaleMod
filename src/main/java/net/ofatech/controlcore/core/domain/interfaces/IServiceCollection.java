package net.ofatech.controlcore.core.domain.interfaces;

/**
 * Defines a collection for registering services in the dependency injection container.
 */
public interface IServiceCollection {
    /**
     * Registers a transient service.
     * A new instance is created every time the service is requested.
     *
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addTransient(Class<T> serviceType, Class<? extends T> implementationType);

    /**
     * Registers a transient service with a factory function.
     *
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addTransient(Class<T> serviceType, ServiceFactory<T> factory);

    /**
     * Registers a scoped service.
     * A single instance is created per scope (application lifetime in this implementation).
     *
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addScoped(Class<T> serviceType, Class<? extends T> implementationType);

    /**
     * Registers a scoped service with a factory function.
     *
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addScoped(Class<T> serviceType, ServiceFactory<T> factory);

    /**
     * Registers a singleton service.
     * A single instance is created for the entire application lifetime.
     *
     * @param serviceType The service interface/abstract class type
     * @param implementationType The concrete implementation type
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addSingleton(Class<T> serviceType, Class<? extends T> implementationType);

    /**
     * Registers a singleton service with a factory function.
     *
     * @param serviceType The service interface/abstract class type
     * @param factory The factory function to create instances
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addSingleton(Class<T> serviceType, ServiceFactory<T> factory);

    /**
     * Registers a singleton service with an existing instance.
     *
     * @param serviceType The service interface/abstract class type
     * @param instance The singleton instance
     * @param <T> The service type
     * @return This collection for method chaining
     */
    <T> IServiceCollection addSingleton(Class<T> serviceType, T instance);

    /**
     * Builds a service provider from this collection.
     *
     * @return A service provider
     */
    IServiceProvider buildServiceProvider();
}

