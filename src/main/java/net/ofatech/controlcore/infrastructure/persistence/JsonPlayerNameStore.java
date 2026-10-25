package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.config.PlayerNameStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonPlayerNameStore implements PlayerNameStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final JsonFile file;
    private final Map<PlayerId, String> names = new LinkedHashMap<>();

    public JsonPlayerNameStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "player names");
        this.file
                .<Map<String, String>>read(FILE_TYPE)
                .ifPresent(stored -> stored.forEach((id, name) -> {
                    try {
                        if (name != null && !name.isBlank()) {
                            names.put(PlayerId.of(UUID.fromString(id)), name);
                        }
                    } catch (IllegalArgumentException ignored) {
                        log.warn("Ignoring player name entry '" + id + "' in " + this.file.path());
                    }
                }));
    }

    @Override
    public synchronized Map<PlayerId, String> all() {
        return Map.copyOf(names);
    }

    @Override
    public synchronized void put(PlayerId id, String name) {
        if (name == null || name.isBlank() || name.equals(names.get(id))) {
            return;
        }
        names.put(id, name);
        Map<String, String> snapshot = new TreeMap<>();
        names.forEach((key, value) -> snapshot.put(key.value().toString(), value));
        file.write(snapshot, FILE_TYPE);
    }
}
