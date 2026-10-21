package net.ofatech.controlcore.application.controlpanel;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.ControlPanelTab;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;
import net.ofatech.controlcore.domain.ports.config.SettingsProvider;

/** The control panel's settings in the shared registry; defaults come from {@code Settings.json}. */
public final class ControlPanelSettings {

    static final String ENABLED_ID = "controlcore:control-panel-enabled";
    static final String START_TAB_ID = "controlcore:panel-start-tab";

    private final Setting<Boolean> enabled;
    private final Setting<String> startTab;

    public ControlPanelSettings(SettingsApi settings, SettingsProvider defaults) {
        this.enabled = settings.register(SettingDefinition.server(
                                "controlcore",
                                "control-panel-enabled",
                                SettingType.bool(),
                                defaults.current().controlPanelEnabled())
                        .named(Text.key("controlcore.settings.control_panel_enabled.name", "Control panel enabled"))
                        .describedAs(Text.key(
                                "controlcore.settings.control_panel_enabled.desc",
                                "Whether admins can open the control panel with /open-admin-config. Players"
                                        + " can always open their preferences with /prefs.")))
                .value();
        this.startTab = settings.register(SettingDefinition.player(
                                "controlcore",
                                "panel-start-tab",
                                SettingType.choice(ControlPanelTab.labels().toArray(String[]::new)),
                                ControlPanelTab.SERVER_CONFIGS.label())
                        .named(Text.key("controlcore.settings.panel_start_tab.name", "Panel start tab"))
                        .describedAs(Text.key(
                                "controlcore.settings.panel_start_tab.desc", "The tab the control panel opens on."))
                        .optionsKey("controlcore.tabs"))
                .value();
    }

    public boolean panelEnabled() {
        return enabled.get();
    }

    public ControlPanelTab startTab(PlayerId player) {
        return ControlPanelTab.fromLabel(startTab.get(player)).orElse(ControlPanelTab.SERVER_CONFIGS);
    }
}
