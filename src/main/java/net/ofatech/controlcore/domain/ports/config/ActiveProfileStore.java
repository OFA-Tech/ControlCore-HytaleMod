package net.ofatech.controlcore.domain.ports.config;

import java.util.Optional;

public interface ActiveProfileStore {

    Optional<String> get();

    void set(Optional<String> profile);
}
