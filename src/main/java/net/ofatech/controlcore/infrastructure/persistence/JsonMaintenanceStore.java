package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.server.MaintenanceState;
import net.ofatech.controlcore.domain.ports.config.MaintenanceStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonMaintenanceStore implements MaintenanceStore {

    private final JsonFile file;

    public JsonMaintenanceStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "maintenance mode");
    }

    @Override
    public synchronized MaintenanceState load() {
        return file.<FileForm>read(FileForm.class).map(FileForm::toState).orElse(MaintenanceState.OFF);
    }

    @Override
    public synchronized boolean save(MaintenanceState state) {
        return file.write(FileForm.of(state), FileForm.class);
    }

    private static final class FileForm {
        private boolean on;
        private String message;
        private String motd;
        private String since;
        private String until;
        private String savedMotd;

        static FileForm of(MaintenanceState state) {
            FileForm form = new FileForm();
            form.on = state.on();
            form.message = state.message();
            form.motd = state.motd();
            form.since = state.since().map(Instant::toString).orElse(null);
            form.until = state.until().map(Instant::toString).orElse(null);
            form.savedMotd = state.savedMotd().orElse(null);
            return form;
        }

        MaintenanceState toState() {
            return new MaintenanceState(
                    on, message, motd, instant(since), instant(until), Optional.ofNullable(savedMotd));
        }

        private static Optional<Instant> instant(String text) {
            try {
                return text == null || text.isBlank() ? Optional.empty() : Optional.of(Instant.parse(text));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
    }
}
