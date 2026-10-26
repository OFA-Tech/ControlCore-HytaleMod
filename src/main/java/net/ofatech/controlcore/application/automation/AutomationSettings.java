package net.ofatech.controlcore.application.automation;

import net.ofatech.controlcore.api.Setting;
import net.ofatech.controlcore.api.SettingsApi;
import net.ofatech.controlcore.domain.model.settings.SettingDefinition;
import net.ofatech.controlcore.domain.model.settings.SettingType;
import net.ofatech.controlcore.domain.model.text.Text;

public final class AutomationSettings {

    static final String ENABLED_ID = "controlcore:automation-enabled";

    private final Setting<Boolean> enabled;

    public AutomationSettings(SettingsApi settings) {
        this.enabled = settings.register(
                        SettingDefinition.server("controlcore", "automation-enabled", SettingType.bool(), true)
                                .named(Text.key("controlcore.settings.automation_enabled.name", "Automation rules run"))
                                .describedAs(Text.key(
                                        "controlcore.settings.automation_enabled.desc",
                                        "Turn off to stop every automation rule at once without deleting them."))
                                .inCategory(Text.key("controlcore.settings.category.automation", "Automation")))
                .value();
    }

    public boolean on() {
        return enabled.get();
    }
}
