package net.ofatech.controlcore.infrastructure.platforms.hytale.config;

import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Path;
import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;
import net.ofatech.controlcore.domain.ports.system.Log;

/** Reads {@code Settings.json} through Hytale's codec config and writes missing defaults back. */
public final class HytaleSettingsProvider implements SettingsProvider {

    private final ModSettings settings;

    public HytaleSettingsProvider(Path dataDirectory, Log log) {
        this.settings = load(new Config<>(dataDirectory, "Settings", ControlCoreConfig.CODEC), log);
    }

    @Override
    public ModSettings current() {
        return settings;
    }

    private static ModSettings load(Config<ControlCoreConfig> config, Log log) {
        try {
            config.load().join();
            config.save().join();
            return config.get().toSettings();
        } catch (RuntimeException e) {
            log.error("Could not load Settings.json; using defaults", e);
            return ModSettings.DEFAULTS;
        }
    }
}
