package net.ofatech.controlcore.domain.ports.platform;

import java.util.Optional;

public interface Capabilities {

    <T> Optional<T> find(Class<T> capability);
}
