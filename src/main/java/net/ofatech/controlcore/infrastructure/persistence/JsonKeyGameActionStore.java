package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.keys.GameActionMode;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.KeyGameActionStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonKeyGameActionStore implements KeyGameActionStore {

    private final JsonSettingStore file;

    public JsonKeyGameActionStore(Path file, Log log) {
        this.file = new JsonSettingStore(file, log);
    }

    @Override
    public Optional<GameActionMode> get(SettingId action) {
        return file.get(action).flatMap(GameActionMode::parse);
    }

    @Override
    public void put(SettingId action, GameActionMode mode) {
        file.put(action, mode.id());
    }

    @Override
    public void remove(SettingId action) {
        file.remove(action);
    }

    @Override
    public void flush() {
        file.flush();
    }
}
