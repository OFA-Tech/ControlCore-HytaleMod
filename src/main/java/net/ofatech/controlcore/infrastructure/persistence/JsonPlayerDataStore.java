package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private final Path file;
    private final Log log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public JsonPlayerDataStore(Path file, Log log) {
        this.file = file;
        this.log = log;
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
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(snapshot, FILE_TYPE, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty = true;
            log.error("Could not save player data to " + file, e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<UUID, Map<String, String>> stored = gson.fromJson(reader, FILE_TYPE);
            if (stored != null) {
                stored.forEach((id, values) -> data.put(id, new ConcurrentHashMap<>(new HashMap<>(values))));
            }
        } catch (IOException | JsonParseException e) {
            log.error("Could not read player data from " + file + "; starting empty", e);
        }
    }
}
