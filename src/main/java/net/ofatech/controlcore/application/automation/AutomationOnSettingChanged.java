package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.SettingChanged;
import net.ofatech.controlcore.domain.model.settings.SettingScope;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class AutomationOnSettingChanged implements ModEventHandler<SettingChanged> {

    private final AutomationService automation;

    public AutomationOnSettingChanged(AutomationService automation) {
        this.automation = automation;
    }

    @Override
    public Class<SettingChanged> eventType() {
        return SettingChanged.class;
    }

    @Override
    public void handle(SettingChanged event) {
        if (event.scope() == SettingScope.SERVER) {
            automation.onSetting(event.id(), event.oldValue(), event.newValue());
        }
    }
}
