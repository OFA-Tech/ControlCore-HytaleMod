package net.ofatech.controlcore.domain.ports.mods;

import java.util.Optional;

public interface ModLookup {

    boolean isLoaded(String modId);

    Optional<ClassLoader> classLoader(String modId);
}
