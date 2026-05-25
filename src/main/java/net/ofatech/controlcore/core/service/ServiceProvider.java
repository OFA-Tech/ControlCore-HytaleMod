package net.ofatech.controlcore.core.service;

import net.ofatech.controlcore.core.domain.enums.ServiceLifetime;
import net.ofatech.controlcore.core.domain.interfaces.IServiceProvider;

import java.util.*;

/**
 * Default implementation of IServiceProvider.
 * Resolves and provides services based on their descriptors and lifetimes.
 */
public class ServiceProvider implements IServiceProvider {
    private final Map<Class<?>, ServiceDescriptor> descriptors;
    private final Map<Class<?>, Object> singletonCache = new HashMap<>();
    private final Map<Class<?>, Object> scopedCache = new HashMap<>();

    ServiceProvider(Map<Class<?>, ServiceDescriptor> descriptors) {
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors cannot be null");
    }

    @Override
    public <T> Optional<T> getService(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType cannot be null");

        ServiceDescriptor descriptor = descriptors.get(serviceType);
        if (descriptor == null) {
            return Optional.empty();
        }

        return Optional.of(resolveService(serviceType, descriptor));
    }

    @Override
    public <T> T getRequiredService(Class<T> serviceType) {
        return getService(serviceType)
            .orElseThrow(() -> new IllegalArgumentException("Service not registered: " + serviceType.getName()));
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveService(Class<T> serviceType, ServiceDescriptor descriptor) {
        ServiceLifetime lifetime = descriptor.getLifetime();

        return switch (lifetime) {
            case SINGLETON -> resolveSingleton(serviceType, descriptor);
            case SCOPED -> resolveScoped(serviceType, descriptor);
            case TRANSIENT -> resolveTransient(serviceType, descriptor);
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveSingleton(Class<T> serviceType, ServiceDescriptor descriptor) {
        Object cached = singletonCache.get(serviceType);
        if (cached != null) {
            return (T) cached;
        }

        T instance = (T) descriptor.getFactory().create(this);
        singletonCache.put(serviceType, instance);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveScoped(Class<T> serviceType, ServiceDescriptor descriptor) {
        Object cached = scopedCache.get(serviceType);
        if (cached != null) {
            return (T) cached;
        }

        T instance = (T) descriptor.getFactory().create(this);
        scopedCache.put(serviceType, instance);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveTransient(Class<T> serviceType, ServiceDescriptor descriptor) {
        return (T) descriptor.getFactory().create(this);
    }
}

