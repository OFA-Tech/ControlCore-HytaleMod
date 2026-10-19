package net.ofatech.controlcore.domain.ports.platform;

import java.util.Optional;

/** Capabilities offered by other installed mods. Available from {@code start()} on. */
public interface Capabilities {

    <T> Optional<T> find(Class<T> capability);
}
