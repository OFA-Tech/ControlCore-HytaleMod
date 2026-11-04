package net.ofatech.controlcore.domain.model.server;

import java.time.Instant;
import net.ofatech.controlcore.domain.guard.Guard;

public record PendingRestore(String name, String by, Instant at) {

    public PendingRestore {
        Guard.notNull(name, "name");
        Guard.notNull(at, "at");
        by = by == null ? "" : by;
    }
}
