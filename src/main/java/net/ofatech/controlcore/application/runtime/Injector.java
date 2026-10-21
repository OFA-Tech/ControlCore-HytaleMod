package net.ofatech.controlcore.application.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Injector {

    private final ComponentIndex index;
    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final Deque<Class<?>> path = new ArrayDeque<>();

    public Injector(ComponentIndex index) {
        this.index = index;
    }

    public synchronized <T> void bind(Class<T> type, T instance) {
        Objects.requireNonNull(instance, () -> "Binding for " + type.getName() + " is null");
        if (instances.putIfAbsent(type, instance) != null) {
            throw new InjectionException(type.getName() + " is already bound");
        }
    }

    public synchronized <T> T get(Class<T> type) {
        Object existing = instances.get(type);
        if (existing != null) {
            return type.cast(existing);
        }
        if (path.contains(type)) {
            throw new InjectionException("Dependency cycle: " + describe(type));
        }
        path.addLast(type);
        try {
            T instance = isAbstract(type) ? fromIndex(type) : construct(type);
            instances.put(type, instance);
            return instance;
        } finally {
            path.removeLast();
        }
    }

    private <T> T fromIndex(Class<T> type) {
        List<Class<? extends T>> providers = index.providers(type);
        if (providers.isEmpty()) {
            throw new InjectionException("Nothing provides " + type.getName() + " (" + describe(null)
                    + "). Bind it in the platform or annotate one implementation with @AutoService("
                    + type.getSimpleName() + ".class)");
        }
        if (providers.size() > 1) {
            throw new InjectionException("Several classes provide " + type.getName() + ": "
                    + providers.stream().map(Class::getName).collect(Collectors.joining(", ")));
        }
        return get(providers.getFirst());
    }

    private <T> T construct(Class<T> type) {
        if (!index.owns(type)) {
            throw new InjectionException("Cannot create " + type.getName() + " (" + describe(null)
                    + "): only classes of this mod are created automatically; bind it instead");
        }
        Constructor<?>[] constructors = type.getConstructors();
        if (constructors.length != 1) {
            throw new InjectionException(
                    type.getName() + " needs exactly one public constructor, found " + constructors.length);
        }
        Constructor<?> constructor = constructors[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = get(parameterTypes[i]);
        }
        try {
            return type.cast(constructor.newInstance(arguments));
        } catch (InvocationTargetException e) {
            throw new InjectionException("Constructor of " + type.getName() + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new InjectionException("Cannot create " + type.getName(), e);
        }
    }

    private static boolean isAbstract(Class<?> type) {
        return type.isInterface() || Modifier.isAbstract(type.getModifiers());
    }

    private String describe(Class<?> next) {
        StringBuilder text = new StringBuilder();
        for (Class<?> step : path) {
            if (!text.isEmpty()) {
                text.append(" -> ");
            }
            text.append(step.getSimpleName());
        }
        if (next != null) {
            text.append(" -> ").append(next.getSimpleName());
        }
        return text.toString();
    }
}
