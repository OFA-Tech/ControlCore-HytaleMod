package net.ofatech.controlcore.fakes;

import net.ofatech.controlcore.domain.model.ModSettings;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;

public final class FakeSettings implements SettingsProvider {

    public ModSettings settings = ModSettings.DEFAULTS;

    @Override
    public ModSettings current() {
        return settings;
    }
}
