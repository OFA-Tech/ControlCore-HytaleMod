package net.ofatech.controlcore.domain.model.server;

import java.time.Instant;
import java.util.Optional;
import net.ofatech.controlcore.domain.guard.Guard;

public record MaintenanceState(
        boolean on,
        String message,
        String motd,
        Optional<Instant> since,
        Optional<Instant> until,
        Optional<String> savedMotd) {

    public static final MaintenanceState OFF =
            new MaintenanceState(false, "", "", Optional.empty(), Optional.empty(), Optional.empty());

    public MaintenanceState {
        message = message == null ? "" : message;
        motd = motd == null ? "" : motd;
        Guard.notNull(since, "since");
        Guard.notNull(until, "until");
        Guard.notNull(savedMotd, "savedMotd");
    }

    public MaintenanceState withoutSavedMotd() {
        return new MaintenanceState(on, message, motd, since, until, Optional.empty());
    }
}
