package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.PlayerData;
import net.ofatech.controlcore.domain.ports.system.Log;

/** {@link PlayerData} kept in memory and written to one JSON file on {@link #flush()}. */
public final class JsonPlayerDataStore implements PlayerData {

    private static final Type FILE_TYPE = new TypeToken<Map<UUID, Map<String, String>>>() {}.getType();

    private final JsonFile file;
    private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public JsonPlayerDataStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "player data");
        load();
    }

    @Override
    public Optional<String> get(PlayerId player, String key) {
        return Optional.ofNullable(data.getOrDefault(player.value(), Map.of()).get(key));
    }

    @Override
    public void put(PlayerId player, String key, String value) {
        data.computeIfAbsent(player.value(), id -> new ConcurrentHashMap<>()).put(key, value);
        dirty = true;
    }

    @Override
    public void remove(PlayerId player, String key) {
        Map<String, String> values = data.get(player.value());
        if (values != null && values.remove(key) != null) {
            dirty = true;
        }
    }

    @Override
    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        Map<UUID, Map<String, String>> snapshot = new TreeMap<>();
        data.forEach((id, values) -> snapshot.put(id, new TreeMap<>(values)));
        if (!file.write(snapshot, FILE_TYPE)) {
            dirty = true;
        }
    }

    private void load() {
        file.<Map<UUID, Map<String, String>>>read(FILE_TYPE)
                .ifPresent(stored ->
                        stored.forEach((id, values) -> data.put(id, new ConcurrentHashMap<>(new HashMap<>(values)))));
    }
}
