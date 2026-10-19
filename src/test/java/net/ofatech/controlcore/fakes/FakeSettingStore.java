package net.ofatech.controlcore.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.SettingStore;

public final class FakeSettingStore implements SettingStore {

    public final Map<SettingId, String> values = new ConcurrentHashMap<>();
    public int flushes;

    @Override
    public Optional<String> get(SettingId id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public void put(SettingId id, String value) {
        values.put(id, value);
    }

    @Override
    public void remove(SettingId id) {
        values.remove(id);
    }

    @Override
    public Map<SettingId, String> all() {
        return Map.copyOf(values);
    }

    @Override
    public void flush() {
        flushes++;
    }
}
