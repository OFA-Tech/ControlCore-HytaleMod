package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.SettingStore;
import net.ofatech.controlcore.domain.ports.system.Log;

/** {@link SettingStore} kept in memory and written to one JSON object ({@code "namespace:key": "value"}). */
public final class JsonSettingStore implements SettingStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final JsonFile file;
    private final Log log;
    private final Map<SettingId, String> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public JsonSettingStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "settings");
        this.log = log;
        load();
    }

    @Override
    public Optional<String> get(SettingId id) {
        return Optional.ofNullable(values.get(id));
    }

    @Override
    public void put(SettingId id, String value) {
        values.put(id, value);
        dirty = true;
    }

    @Override
    public void remove(SettingId id) {
        if (values.remove(id) != null) {
            dirty = true;
        }
    }

    @Override
    public Map<SettingId, String> all() {
        return Map.copyOf(values);
    }

    @Override
    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        Map<String, String> snapshot = new TreeMap<>();
        values.forEach((id, value) -> snapshot.put(id.toString(), value));
        if (!file.write(snapshot, FILE_TYPE)) {
            dirty = true;
        }
    }

    private void load() {
        file.<Map<String, String>>read(FILE_TYPE)
                .ifPresent(stored -> stored.forEach((key, value) -> SettingId.parse(key)
                        .onSuccess(id -> {
                            if (value != null) {
                                values.put(id, value);
                            }
                        })
                        .onFailure(error ->
                                log.warn("Ignoring '" + key + "' in " + file.path() + ": " + error.description()))));
    }
}
