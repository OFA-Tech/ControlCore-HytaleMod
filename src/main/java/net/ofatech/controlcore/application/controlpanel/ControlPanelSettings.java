package net.ofatech.controlcore.application.controlpanel;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;

/** The control panel's settings in the shared registry; defaults come from {@code Settings.json}. */
public final class ControlPanelSettings {

    static final String ENABLED_ID = "controlcore:control-panel-enabled";
    static final String START_TAB_ID = "controlcore:panel-start-tab";

    private final Setting<Boolean> enabled;
    private final Setting<String> startTab;

    public ControlPanelSettings(SettingsApi settings, SettingsProvider defaults) {
        this.enabled = settings.register(SettingDefinition.server(
                                ENABLED_ID,
                                SettingType.bool(),
                                defaults.current().controlPanelEnabled())
                        .named("Control panel enabled")
                        .describedAs("Whether admins can open the control panel with /open-admin-config.")
                        .inCategory("ControlCore"))
                .value();
        this.startTab = settings.register(SettingDefinition.player(
                                START_TAB_ID,
                                SettingType.choice(ControlPanelTab.labels().toArray(String[]::new)),
                                ControlPanelTab.SERVER_CONFIGS.label())
                        .named("Panel start tab")
                        .describedAs("The tab the control panel opens on.")
                        .inCategory("ControlCore"))
                .value();
    }

    public boolean panelEnabled() {
        return enabled.get();
    }

    public ControlPanelTab startTab(PlayerId player) {
        return ControlPanelTab.fromLabel(startTab.get(player)).orElse(ControlPanelTab.SERVER_CONFIGS);
    }
}
