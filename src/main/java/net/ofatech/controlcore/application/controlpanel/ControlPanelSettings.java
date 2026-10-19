package net.ofatech.controlcore.application.controlpanel;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;

/** ControlCore's own settings in the shared registry; defaults come from {@code Settings.json}. */
public final class ControlPanelSettings {

    static final String ENABLED_ID = "controlcore:control-panel-enabled";

    private final Setting<Boolean> enabled;

    public ControlPanelSettings(SettingsApi settings, SettingsProvider defaults) {
        this.enabled = settings.register(SettingDefinition.server(
                                ENABLED_ID,
                                SettingType.bool(),
                                defaults.current().controlPanelEnabled())
                        .named("Control panel enabled")
                        .describedAs("Whether admins can open the control panel with /open-admin-config.")
                        .inCategory("ControlCore"))
                .value();
    }

    public boolean panelEnabled() {
        return enabled.get();
    }
}
