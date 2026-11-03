package net.ofatech.controlcore.infrastructure.persistence;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.ofatech.controlcore.domain.model.settings.SettingPermission;
import net.ofatech.controlcore.domain.ports.config.SettingPermissionStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonSettingPermissionStore implements SettingPermissionStore {

    private static final Type FILE_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    private final JsonFile file;
    private final Log log;
    private final Map<String, SettingPermission> rules = new TreeMap<>();

    public JsonSettingPermissionStore(Path file, Log log) {
        this.file = new JsonFile(file, log, "setting permissions");
        this.log = log;
        load();
    }

    @Override
    public synchronized Map<String, SettingPermission> all() {
        return Map.copyOf(new TreeMap<>(rules));
    }

    @Override
    public synchronized boolean put(String target, SettingPermission rule) {
        Map<String, SettingPermission> updated = new TreeMap<>(rules);
        if (rule.isInherit()) {
            if (updated.remove(target) == null) {
                return true;
            }
        } else {
            updated.put(target, rule);
        }
        Map<String, Entry> snapshot = new TreeMap<>();
        updated.forEach((key, value) -> snapshot.put(
                key, new Entry(value.view().orElse(null), value.edit().orElse(null))));
        if (!file.write(snapshot, FILE_TYPE)) {
            return false;
        }
        rules.clear();
        rules.putAll(updated);
        return true;
    }

    private void load() {
        file.<Map<String, Entry>>read(FILE_TYPE)
                .ifPresent(stored -> stored.forEach((target, entry) -> {
                    if (entry == null || target.isBlank()) {
                        return;
                    }
                    try {
                        SettingPermission rule = SettingPermission.of(entry.view, entry.edit);
                        if (!rule.isInherit()) {
                            rules.put(target.strip().toLowerCase(Locale.ROOT), rule);
                        }
                    } catch (RuntimeException e) {
                        log.warn("Ignoring the setting permission '" + target + "' in " + file.path() + ": "
                                + e.getMessage());
                    }
                }));
    }

    private static final class Entry {
        private String view;
        private String edit;

        Entry(String view, String edit) {
            this.view = view;
            this.edit = edit;
        }
    }
}
