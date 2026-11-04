package net.ofatech.controlcore.fakes;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.server.PendingRestore;
import net.ofatech.controlcore.domain.ports.config.PendingRestoreStore;

public final class FakePendingRestoreStore implements PendingRestoreStore {

    public volatile Optional<PendingRestore> restore = Optional.empty();
    public volatile boolean failClear;

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
}
