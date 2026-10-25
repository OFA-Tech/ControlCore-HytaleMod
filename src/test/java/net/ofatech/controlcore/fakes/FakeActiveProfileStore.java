package net.ofatech.controlcore.fakes;

import java.util.Optional;
import net.ofatech.controlcore.domain.ports.config.ActiveProfileStore;

public final class FakeActiveProfileStore implements ActiveProfileStore {

    public Optional<String> active = Optional.empty();

    @Override
    public Optional<String> get() {
        return active;
    }

    @Override
    public void set(Optional<String> profile) {
        active = profile;
    }
}
