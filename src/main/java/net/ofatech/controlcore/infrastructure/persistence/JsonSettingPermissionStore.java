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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.system.Log;

/**
 * {@link SettingPermissionStore} in one JSON object:
 * {@code "setting:homes:max-homes": {"view": "everyone", "edit": "homes.moderator"}}. Server owners may edit
 * the file while the server is stopped.
 */
public final class JsonSettingPermissionStore implements SettingPermissionStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private final Path file;
    private final Log log;
    private final Gson gson =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, SettingPermission> rules = new TreeMap<>();

    public JsonSettingPermissionStore(Path file, Log log) {
        this.file = file;
        this.log = log;
        load();
    }

    @Override
    public synchronized Map<String, SettingPermission> all() {
        return Map.copyOf(new TreeMap<>(rules));
    }

    @Override
    public synchronized void put(String target, SettingPermission rule) {
        if (rule.isInherit()) {
            if (rules.remove(target) == null) {
                return;
            }
        } else {
            rules.put(target, rule);
        }
        save();
    }

    private void save() {
        Map<String, Entry> snapshot = new TreeMap<>();
        rules.forEach((target, rule) -> snapshot.put(
                target, new Entry(rule.view().orElse(null), rule.edit().orElse(null))));
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(snapshot, FILE_TYPE, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Could not save setting permissions to " + file, e);
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
            stored.forEach((target, entry) -> {
                if (entry == null || target.isBlank()) {
                    return;
                }
                try {
                    SettingPermission rule = SettingPermission.of(entry.view, entry.edit);
                    if (!rule.isInherit()) {
                        rules.put(target.strip().toLowerCase(Locale.ROOT), rule);
                    }
                } catch (RuntimeException e) {
                    log.warn("Ignoring the setting permission '" + target + "' in " + file + ": " + e.getMessage());
                }
            });
        } catch (IOException | JsonParseException e) {
            log.error("Could not read setting permissions from " + file + "; starting with none", e);
        }
    }

    /** One rule as stored; a missing side inherits. */
    private static final class Entry {
        private String view;
        private String edit;

        Entry(String view, String edit) {
            this.view = view;
            this.edit = edit;
        }
    }
}
