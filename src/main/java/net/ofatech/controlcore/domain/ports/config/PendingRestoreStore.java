package net.ofatech.controlcore.domain.ports.config;

import java.nio.file.Path;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.server.PendingRestore;

public interface PendingRestoreStore {

    Optional<PendingRestore> load();

    boolean save(PendingRestore restore);

    boolean clear();

    Path copies();
}
