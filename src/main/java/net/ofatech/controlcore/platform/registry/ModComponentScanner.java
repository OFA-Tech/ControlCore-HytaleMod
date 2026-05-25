package net.ofatech.controlcore.platform.registry;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModComponentScanner {

    private ModComponentScanner() {
    }

    public static Set<Class<?>> scan(String basePackage, ClassLoader classLoader) {
        Set<Class<?>> classes = new HashSet<>();
        String path = basePackage.replace('.', '/');

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    classes.addAll(scanDirectory(basePackage, url));
                } else if ("jar".equals(protocol)) {
                    classes.addAll(scanJar(basePackage, path, url, classLoader));
                }
            }
        } catch (IOException e) {
            // Keep discovery failures isolated to avoid breaking plugin startup.
        }

        return classes;
    }

    private static Set<Class<?>> scanDirectory(String basePackage, URL url) {
        Set<Class<?>> classes = new HashSet<>();
        try {
            Path directory = Paths.get(url.toURI());
            if (!Files.exists(directory)) {
                return classes;
            }

            Files.walk(directory)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = toClassName(basePackage, directory, path);
                        if (className != null) {
                            tryAddClass(className, Thread.currentThread().getContextClassLoader(), classes);
                        }
                    });
        } catch (IOException | URISyntaxException e) {
            // Ignore scan errors to keep startup resilient.
        }

        return classes;
    }

    private static Set<Class<?>> scanJar(String basePackage, String path, URL url, ClassLoader classLoader) {
        Set<Class<?>> classes = new HashSet<>();
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(path) && name.endsWith(".class") && !name.contains("$")) {
                        String className = name.replace('/', '.').substring(0, name.length() - ".class".length());
                        tryAddClass(className, classLoader, classes);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore scan errors to keep startup resilient.
        }

        return classes;
    }

    private static String toClassName(String basePackage, Path baseDir, Path classFile) {
        Path relativePath = baseDir.relativize(classFile);
        String className = relativePath.toString().replace('\\', '.').replace('/', '.');
        if (!className.endsWith(".class") || className.contains("$")) {
            return null;
        }

        return basePackage + "." + className.substring(0, className.length() - ".class".length());
    }

    private static void tryAddClass(String className, ClassLoader classLoader, Set<Class<?>> classes) {
        try {
            classes.add(Class.forName(className, false, classLoader));
        } catch (ClassNotFoundException e) {
            // Ignore missing classes.
        }
    }
}
