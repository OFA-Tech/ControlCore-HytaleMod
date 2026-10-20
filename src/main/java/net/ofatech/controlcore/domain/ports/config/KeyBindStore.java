package net.ofatech.controlcore.domain.ports.config;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;

/** Server-wide key bindings set by admins, as text. Players' own bindings live in {@code PlayerData}. */
public interface KeyBindStore {

    Optional<String> get(SettingId action);

    void put(SettingId action, String keys);

    void remove(SettingId action);

    /** Writes pending changes to storage. */
    void flush();
}
