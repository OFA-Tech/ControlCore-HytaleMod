package net.ofatech.controlcore.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.ports.config.PendingRestoreStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonPendingRestoreStore implements PendingRestoreStore {

    private final JsonFile file;
    private final Path copies;
    private final Log log;

    public JsonPendingRestoreStore(Path file, Path copies, Log log) {
        this.file = new JsonFile(file, log, "planned backup restore");
        this.copies = copies;
        this.log = log;
    }

    @Override
    public Path copies() {
        return copies;
    }

    @Override
    public synchronized Optional<PendingRestore> load() {
        if (!Files.exists(file.path())) {
            return Optional.empty();
        }
        return file.<FileForm>read(FileForm.class).flatMap(FileForm::toRestore);
    }

    @Override
    public synchronized boolean save(PendingRestore restore) {
        FileForm form = new FileForm();
        form.name = restore.name();
        form.by = restore.by();
        form.at = restore.at().toString();
        form.copy = restore.copy();
        form.seen = restore.seen().toString();
        return file.write(form, FileForm.class);
    }

    @Override
    public synchronized boolean clear() {
        try {
            Files.deleteIfExists(file.path());
            return true;
        } catch (IOException e) {
            log.error("Could not delete " + file.path(), e);
            return false;
        }
    }

    private static final class FileForm {
        private String name;
        private String by;
        private String at;
        private String copy;
        private String seen;

        Optional<PendingRestore> toRestore() {
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PendingRestore(name, by, instant(at), copy, instant(seen)));
        }

        private static Instant instant(String text) {
            try {
                return text == null ? Instant.EPOCH : Instant.parse(text);
            } catch (DateTimeParseException e) {
                return Instant.EPOCH;
            }
        }
    }
}
