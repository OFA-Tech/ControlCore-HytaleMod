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

    private final Path file;
    private final Log log;
    private final Gson gson =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<SettingId, String> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public JsonSettingStore(Path file, Log log) {
        this.file = file;
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
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(snapshot, FILE_TYPE, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty = true;
            log.error("Could not save settings to " + file, e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, String> stored = gson.fromJson(reader, FILE_TYPE);
            if (stored == null) {
                return;
            }
            stored.forEach((key, value) -> SettingId.parse(key)
                    .onSuccess(id -> {
                        if (value != null) {
                            values.put(id, value);
                        }
                    })
                    .onFailure(error -> log.warn("Ignoring '" + key + "' in " + file + ": " + error.description())));
        } catch (IOException | JsonParseException e) {
            log.error("Could not read settings from " + file + "; starting empty", e);
        }
    }
}
