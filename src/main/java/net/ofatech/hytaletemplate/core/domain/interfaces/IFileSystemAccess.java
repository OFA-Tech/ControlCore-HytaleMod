package net.ofatech.hytaletemplate.core.domain.interfaces;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface IFileSystemAccess {
    boolean exists(Path path);

    boolean isFile(Path path);

    boolean isDirectory(Path path);

    void createDirectories(Path path);

    String readText(Path path);

    Optional<String> tryReadText(Path path);

    void writeText(Path path, String content);

    byte[] readBytes(Path path);

    void writeBytes(Path path, byte[] content);

    List<Path> listFiles(Path directory);

    void delete(Path path);
}
