package net.ofatech.controlcore.domain.ports.config;

import net.ofatech.controlcore.domain.model.ModSettings;

public interface SettingsProvider {

    ModSettings current();
}
