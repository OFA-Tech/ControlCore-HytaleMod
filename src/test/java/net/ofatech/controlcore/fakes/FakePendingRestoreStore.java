package net.ofatech.controlcore.fakes;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.ports.config.PendingRestoreStore;

public final class FakePendingRestoreStore implements PendingRestoreStore {

    public volatile Optional<PendingRestore> restore = Optional.empty();
    public volatile boolean failClear;
    public volatile Path copies = Path.of("build", "tmp", "cc-pending-" + UUID.randomUUID());

    @Override
    public Optional<PendingRestore> load() {
        return restore;
    }

    @Override
    public boolean save(PendingRestore planned) {
        restore = Optional.of(planned);
        return true;
    }

    @Override
    public boolean clear() {
        if (failClear) {
            return false;
        }
        restore = Optional.empty();
        return true;
    }

    @Override
    public Path copies() {
        return copies;
    }
}
