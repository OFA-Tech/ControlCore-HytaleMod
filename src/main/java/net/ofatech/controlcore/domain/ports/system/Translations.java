package net.ofatech.controlcore.domain.ports.system;

import java.util.Optional;

/** The language files the game has loaded, from every mod. */
public interface Translations {

    /**
     * @param language a language code such as {@code pt-BR}
     * @param key a full translation key such as {@code controlcore.panel.title}
     * @return the text for that key in exactly that language, with placeholders still in it
     */
    Optional<String> lookup(String language, String key);
}
