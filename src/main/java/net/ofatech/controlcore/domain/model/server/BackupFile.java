package net.ofatech.controlcore.domain.model.server;

import java.time.Instant;
import net.ofatech.controlcore.domain.guard.Guard;

public record BackupFile(String name, Instant at, long size, boolean archived) {

    public BackupFile {
        Guard.notNull(name, "name");
        Guard.notNull(at, "at");
    }
}
