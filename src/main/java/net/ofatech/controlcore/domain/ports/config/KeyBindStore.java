package net.ofatech.controlcore.domain.ports.config;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;

public interface KeyBindStore {

    Optional<String> get(SettingId action);

    void put(SettingId action, String keys);

    void remove(SettingId action);

    void flush();
}
