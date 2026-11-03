package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.ofatech.controlcore.domain.ports.system.Log;

final class JsonFile {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Log log;
    private final String contents;
    private volatile boolean readOnly;
    private volatile boolean toldReadOnly;

    JsonFile(Path file, Log log, String contents) {
        this.file = file;
        this.log = log;
        this.contents = contents;
    }

    Path path() {
        return file;
    }

    <T> Optional<T> read(Type type) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (CharacterCodingException e) {
            return broken(e);
        } catch (IOException e) {
            readOnly = true;
            log.error(
                    "Could not open " + contents + " in " + file + "; it is left untouched and changes to it are not"
                            + " saved until the server restarts",
                    e);
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(GSON.fromJson(text, type));
        } catch (JsonParseException e) {
            return broken(e);
        }
    }

    private <T> Optional<T> broken(Exception e) {
        log.error("Could not read " + contents + " from " + file + "; " + moveAside(), e);
        return Optional.empty();
    }

    private String moveAside() {
        Path broken = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file, broken);
            return "moved it to " + broken + " and starting empty";
        } catch (IOException e) {
            readOnly = true;
            return "could not move it aside (" + e.getMessage() + "); starting empty, and changes to it are not saved"
                    + " until the file is fixed and the server restarted";
        }
    }

    boolean write(Object value, Type type) {
        if (readOnly) {
            if (!toldReadOnly) {
                toldReadOnly = true;
                log.warn(file + " was not saved: it could not be read at start; fix the file and restart");
            }
            return false;
        }
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
