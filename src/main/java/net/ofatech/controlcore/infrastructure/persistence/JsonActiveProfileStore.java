package net.ofatech.controlcore.infrastructure.persistence;

import java.nio.file.Path;
import java.util.Optional;
import net.ofatech.controlcore.domain.model.settings.SettingId;
import net.ofatech.controlcore.domain.ports.config.ActiveProfileStore;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class JsonActiveProfileStore implements ActiveProfileStore {

    private static final SettingId ACTIVE = new SettingId("controlcore", "active-profile");

    private final JsonSettingStore file;

    public JsonActiveProfileStore(Path file, Log log) {
        this.file = new JsonSettingStore(file, log);
    }

    @Override
    public Optional<String> get() {
        return file.get(ACTIVE).filter(name -> !name.isBlank());
    }

    @Override
    public void set(Optional<String> profile) {
        if (profile.isPresent()) {
            file.put(ACTIVE, profile.get());
        } else {
            file.remove(ACTIVE);
        }
        file.flush();
    }
}
