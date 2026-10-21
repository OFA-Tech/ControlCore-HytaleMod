package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;
import net.ofatech.controlcore.domain.ports.config.CustomKeyBindStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonCustomKeyBindStore implements CustomKeyBindStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private final JsonFile file;
    private final Log log;
    private final Map<String, CustomKeyBind> binds = new TreeMap<>();

    public JsonCustomKeyBindStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "custom key binds");
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
        file.write(snapshot, FILE_TYPE);
    }

    private void load() {
        file.<Map<String, Entry>>read(FILE_TYPE)
                .ifPresent(stored -> stored.forEach((name, entry) -> {
                    if (entry == null || entry.command == null || entry.command.isBlank() || name.isBlank()) {
                        log.warn("Ignoring custom key bind '" + name + "' in " + file.path() + ": it has no command");
                        return;
                    }
                    binds.put(
                            name,
                            new CustomKeyBind(
                                    name, entry.keys == null ? "none" : entry.keys, entry.command, entry.runAsServer));
                }));
    }

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
