package net.ofatech.controlcore.domain.ports.system;

import java.util.Optional;

public interface Translations {

    Optional<String> lookup(String language, String key);
}
