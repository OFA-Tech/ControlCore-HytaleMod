package net.ofatech.controlcore.core.service;

import net.ofatech.controlcore.core.domain.interfaces.IServiceCollection;
import net.ofatech.controlcore.core.domain.interfaces.IServiceProvider;
import net.ofatech.controlcore.core.domain.interfaces.ServiceFactory;

import java.util.*;

/**
 * Default implementation of IServiceCollection.
 * Maintains a registry of service descriptors and builds a ServiceProvider.
 */
public class ServiceCollection implements IServiceCollection {
    private final Map<Class<?>, ServiceDescriptor> descriptors = new LinkedHashMap<>();

    @Override
    public <T> IServiceCollection addTransient(Class<T> serviceType, Class<? extends T> implementationType) {
        descriptors.put(serviceType, ServiceDescriptor.createTransient(serviceType, implementationType));
        return this;
    }

    @Override
    public <T> IServiceCollection addTransient(Class<T> serviceType, ServiceFactory<T> factory) {
        descriptors.put(serviceType, ServiceDescriptor.createTransient(serviceType, factory));
        return this;
    }

    @Override
    public <T> IServiceCollection addScoped(Class<T> serviceType, Class<? extends T> implementationType) {
        descriptors.put(serviceType, ServiceDescriptor.createScoped(serviceType, implementationType));
        return this;
    }

    @Override
    public <T> IServiceCollection addScoped(Class<T> serviceType, ServiceFactory<T> factory) {
        descriptors.put(serviceType, ServiceDescriptor.createScoped(serviceType, factory));
        return this;
    }

    @Override
    public <T> IServiceCollection addSingleton(Class<T> serviceType, Class<? extends T> implementationType) {
        descriptors.put(serviceType, ServiceDescriptor.createSingleton(serviceType, implementationType));
        return this;
    }

    @Override
    public <T> IServiceCollection addSingleton(Class<T> serviceType, ServiceFactory<T> factory) {
        descriptors.put(serviceType, ServiceDescriptor.createSingleton(serviceType, factory));
        return this;
    }

    @Override
    public <T> IServiceCollection addSingleton(Class<T> serviceType, T instance) {
        descriptors.put(serviceType, ServiceDescriptor.createSingleton(serviceType, instance));
        return this;
    }

    @Override
    public IServiceProvider buildServiceProvider() {
        return new ServiceProvider(new LinkedHashMap<>(descriptors));
    }
}


