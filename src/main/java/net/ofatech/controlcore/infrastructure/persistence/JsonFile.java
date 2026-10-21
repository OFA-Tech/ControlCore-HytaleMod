package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.system.Log;

/** One JSON file of a store: read once at start, replaced as a whole (through a temporary file) on save. */
final class JsonFile {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Log log;
    private final String contents;

    /** @param contents what the file holds, for log messages, for example {@code "settings"} */
    JsonFile(Path file, Log log, String contents) {
        this.file = file;
        this.log = log;
        this.contents = contents;
    }

    Path path() {
        return file;
    }

    /** @return the file's contents, or empty when it does not exist or cannot be read (logged) */
    <T> Optional<T> read(Type type) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return Optional.ofNullable(GSON.fromJson(reader, type));
        } catch (IOException | JsonParseException e) {
            log.error("Could not read " + contents + " from " + file + "; starting empty", e);
            return Optional.empty();
        }
    }

    /** @return whether {@code value} was written (failures are logged) */
    boolean write(Object value, Type type) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(value, type, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.error("Could not save " + contents + " to " + file, e);
            return false;
        }
    }
}
