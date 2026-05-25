package net.ofatech.controlcore.core.data.filesystem;

import net.ofatech.controlcore.core.domain.interfaces.IFileSystemAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class LocalFileSystemAccess implements IFileSystemAccess {
    @Override
    public boolean exists(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        return Files.exists(path);
    }

    @Override
    public boolean isFile(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        return Files.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        return Files.isDirectory(path);
    }

    @Override
    public void createDirectories(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directories: " + path, exception);
        }
    }

    @Override
    public String readText(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read text file: " + path, exception);
        }
    }

    @Override
    public Optional<String> tryReadText(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }

        return Optional.of(readText(path));
    }

    @Override
    public void writeText(Path path, String content) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(content, "content cannot be null");

        try {
            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write text file: " + path, exception);
        }
    }

    @Override
    public byte[] readBytes(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bytes file: " + path, exception);
        }
    }

    @Override
    public void writeBytes(Path path, byte[] content) {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(content, "content cannot be null");

        try {
            Path parent = path.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(path, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write bytes file: " + path, exception);
        }
    }

    @Override
    public List<Path> listFiles(Path directory) {
        Objects.requireNonNull(directory, "directory cannot be null");

        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list files: " + directory, exception);
        }
    }

    @Override
    public void delete(Path path) {
        Objects.requireNonNull(path, "path cannot be null");

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete path: " + path, exception);
        }
    }
}
