package net.ofatech.controlcore.application.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ComponentIndex {

    private static final String PREFIX = "META-INF/services/";

    private final ClassLoader loader;
    private final Map<Class<?>, List<Class<?>>> cache = new ConcurrentHashMap<>();

    public ComponentIndex(ClassLoader loader) {
        this.loader = loader;
    }

    public boolean owns(Class<?> type) {
        return type.getClassLoader() == loader;
    }

    @SuppressWarnings("unchecked")
    public <T> List<Class<? extends T>> providers(Class<T> type) {
        List<Class<?>> found = cache.computeIfAbsent(type, this::scan);
        List<Class<? extends T>> result = new ArrayList<>(found.size());
        for (Class<?> provider : found) {
            result.add((Class<? extends T>) provider);
        }
        return result;
    }

    private List<Class<?>> scan(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        try {
            Enumeration<URL> files = loader.getResources(PREFIX + type.getName());
            while (files.hasMoreElements()) {
                names.addAll(read(files.nextElement()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the component index for " + type.getName(), e);
        }
        List<Class<?>> providers = new ArrayList<>();
        for (String name : names) {
            Class<?> provider = load(name);
            if (provider == null || !owns(provider)) {
                continue;
            }
            if (!type.isAssignableFrom(provider)) {
                throw new InjectionException(name + " is listed as " + type.getName() + " but does not implement it");
            }
            providers.add(provider);
        }
        providers.sort(Comparator.comparing(Class::getName));
        return List.copyOf(providers);
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static List<String> read(URL file) throws IOException {
        List<String> names = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                String name = (comment >= 0 ? line.substring(0, comment) : line).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
