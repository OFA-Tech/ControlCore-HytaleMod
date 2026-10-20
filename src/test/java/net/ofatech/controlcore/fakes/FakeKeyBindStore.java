package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;

public final class FakeKeyBindStore implements KeyBindStore {

    public final Map<SettingId, String> values = new ConcurrentHashMap<>();
    public int flushes;

    @Override
    public Optional<String> get(SettingId action) {
        return Optional.ofNullable(values.get(action));
    }

    @Override
    public void put(SettingId action, String keys) {
        values.put(action, keys);
    }

    @Override
    public void remove(SettingId action) {
        values.remove(action);
    }

    @Override
    public void flush() {
        flushes++;
    }
}
