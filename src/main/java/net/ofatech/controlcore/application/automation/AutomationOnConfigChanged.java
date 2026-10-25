package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.ServerConfigChanged;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class AutomationOnConfigChanged implements ModEventHandler<ServerConfigChanged> {

    private final AutomationService automation;

    public AutomationOnConfigChanged(AutomationService automation) {
        this.automation = automation;
    }

    @Override
    public Class<ServerConfigChanged> eventType() {
        return ServerConfigChanged.class;
    }

    @Override
    public void handle(ServerConfigChanged event) {
        automation.onSetting(event.id(), event.oldValue(), event.newValue());
    }
}
