package net.ofatech.controlcore.core.data.io;

import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import java.util.Objects;
import java.util.Optional;

public final class ResourceAccess {
    private final ClassLoader classLoader;

    public ResourceAccess(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    }

    public static ResourceAccess fromCurrentThread() {
        return new ResourceAccess(Thread.currentThread().getContextClassLoader());
    }

    public Optional<InputStream> openResource(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath cannot be null");

        InputStream inputStream = classLoader.getResourceAsStream(normalizeResourcePath(resourcePath));
        return Optional.ofNullable(inputStream);
    }

    public Optional<String> readResourceText(String resourcePath) {
        return openResource(resourcePath)
            .map(inputStream -> {
                try (inputStream) {
                    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to read resource: " + resourcePath, exception);
                }
            });
    }

    public String readResourceTextOrThrow(String resourcePath) {
        return readResourceText(resourcePath)
            .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourcePath));
    }

    public Optional<byte[]> readResourceBytes(String resourcePath) {
        return openResource(resourcePath)
            .map(inputStream -> {
                try (inputStream) {
                    return inputStream.readAllBytes();
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to read resource: " + resourcePath, exception);
                }
            });
    }

    private static String normalizeResourcePath(String resourcePath) {
        if (resourcePath.startsWith("/")) {
            return resourcePath.substring(1);
        }

        return resourcePath;
    }
}
