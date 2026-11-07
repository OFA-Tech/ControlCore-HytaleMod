package net.ofatech.controlcore.domain.model.server;

import java.time.Instant;
import net.ofatech.controlcore.domain.guard.Guard;

public record PendingRestore(String name, String by, Instant at, String copy, Instant seen) {

    public PendingRestore {
        Guard.notNull(name, "name");
        Guard.notNull(at, "at");
        by = by == null ? "" : by;
        copy = copy == null ? "" : copy;
        seen = seen == null || seen.isBefore(at) ? at : seen;
    }

    public PendingRestore(String name, String by, Instant at, String copy) {
        this(name, by, at, copy, at);
    }

    public PendingRestore(String name, String by, Instant at) {
        this(name, by, at, "");
    }

    public PendingRestore seenAt(Instant now) {
        return new PendingRestore(name, by, at, copy, now);
    }
}
