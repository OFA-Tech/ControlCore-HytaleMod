package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.KeyGameActionStore;

public final class FakeKeyGameActionStore implements KeyGameActionStore {

    public final Map<SettingId, GameActionMode> values = new ConcurrentHashMap<>();

    @Override
    public Optional<GameActionMode> get(SettingId action) {
        return Optional.ofNullable(values.get(action));
    }

    @Override
    public void put(SettingId action, GameActionMode mode) {
        values.put(action, mode);
    }

    @Override
    public void remove(SettingId action) {
        values.remove(action);
    }

    @Override
    public void flush() {}
}
