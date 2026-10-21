package net.ofatech.controlcore.domain.ports.config;

import java.util.Map;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;

public interface SettingStore {

    Optional<String> get(SettingId id);

    void put(SettingId id, String value);

    void remove(SettingId id);

    Map<SettingId, String> all();

    void flush();
}
