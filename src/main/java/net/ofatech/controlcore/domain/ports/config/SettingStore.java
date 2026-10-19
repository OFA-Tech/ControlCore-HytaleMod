package net.ofatech.controlcore.domain.ports.config;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;

/** Persistent server-wide values of registered settings, as text. Player values live in {@code PlayerData}. */
public interface SettingStore {

    Optional<String> get(SettingId id);

    void put(SettingId id, String value);

    void remove(SettingId id);

    /** @return every stored value, including ones whose setting is not registered (yet) */
    Map<SettingId, String> all();

    /** Writes pending changes to storage. */
    void flush();
}
