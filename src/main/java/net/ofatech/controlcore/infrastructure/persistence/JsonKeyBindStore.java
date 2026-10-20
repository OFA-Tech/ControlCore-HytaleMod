package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.KeyBindStore;
import net.ofatech.controlcore.domain.ports.system.Log;

/** {@link KeyBindStore} in one JSON object, {@code "mod:action": "CTRL + F"}; same format as the settings file. */
public final class JsonKeyBindStore implements KeyBindStore {

    private final JsonSettingStore file;

    public JsonKeyBindStore(Path file, Log log) {
        this.file = new JsonSettingStore(file, log);
    }

    @Override
    public Optional<String> get(SettingId action) {
        return file.get(action);
    }

    @Override
    public void put(SettingId action, String keys) {
        file.put(action, keys);
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
