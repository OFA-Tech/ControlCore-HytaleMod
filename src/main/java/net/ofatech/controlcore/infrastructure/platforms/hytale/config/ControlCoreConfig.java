package net.ofatech.controlcore.infrastructure.platforms.hytale.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import net.ofatech.controlcore.domain.model.ModSettings;

final class ControlCoreConfig {

    static final BuilderCodec<ControlCoreConfig> CODEC = BuilderCodec.builder(
                    ControlCoreConfig.class, ControlCoreConfig::new)
            .append(
                    new KeyedCodec<>("ControlPanelEnabled", Codec.BOOLEAN),
                    (config, value) -> config.controlPanelEnabled = value == null || value,
                    config -> config.controlPanelEnabled)
            .add()
            .build();

    private boolean controlPanelEnabled = ModSettings.DEFAULTS.controlPanelEnabled();

    ModSettings toSettings() {
        return new ModSettings(controlPanelEnabled);
    }
}
