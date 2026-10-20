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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * {@link CustomKeyBindStore} in one JSON object:
 * {@code "warp-spawn": {"keys": "CTRL + F", "command": "warp spawn", "runAsServer": false}}.
 */
public final class JsonCustomKeyBindStore implements CustomKeyBindStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private final Path file;
    private final Log log;
    private final Gson gson =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, CustomKeyBind> binds = new TreeMap<>();

    public JsonCustomKeyBindStore(Path file, Log log) {
        this.file = file;
        this.log = log;
        load();
    }

    @Override
    public synchronized List<CustomKeyBind> all() {
        return List.copyOf(binds.values());
    }

    @Override
    public synchronized void put(CustomKeyBind bind) {
        binds.put(bind.name(), bind);
        save();
    }

    @Override
    public synchronized void remove(String name) {
        if (binds.remove(name) != null) {
            save();
        }
    }

    private void save() {
        Map<String, Entry> snapshot = new TreeMap<>();
        binds.forEach((name, bind) -> snapshot.put(name, new Entry(bind.keys(), bind.command(), bind.runAsServer())));
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(snapshot, FILE_TYPE, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Could not save custom key binds to " + file, e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Entry> stored = gson.fromJson(reader, FILE_TYPE);
            if (stored == null) {
                return;
            }
            stored.forEach((name, entry) -> {
                if (entry == null || entry.command == null || entry.command.isBlank() || name.isBlank()) {
                    log.warn("Ignoring custom key bind '" + name + "' in " + file + ": it has no command");
                    return;
                }
                binds.put(
                        name,
                        new CustomKeyBind(
                                name, entry.keys == null ? "none" : entry.keys, entry.command, entry.runAsServer));
            });
        } catch (IOException | JsonParseException e) {
            log.error("Could not read custom key binds from " + file + "; starting empty", e);
        }
    }

    /** One bind as stored. */
    private static final class Entry {
        private String keys;
        private String command;
        private boolean runAsServer;

        Entry(String keys, String command, boolean runAsServer) {
            this.keys = keys;
            this.command = command;
            this.runAsServer = runAsServer;
        }
    }
}
