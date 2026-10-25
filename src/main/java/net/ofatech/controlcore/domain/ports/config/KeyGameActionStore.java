package net.ofatech.controlcore.domain.ports.config;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingId;

public interface KeyGameActionStore {

    Optional<GameActionMode> get(SettingId action);

    void put(SettingId action, GameActionMode mode);

    void remove(SettingId action);

    void flush();
}
