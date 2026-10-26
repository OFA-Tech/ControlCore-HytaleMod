package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerEnteredWorld;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class AutomationOnWorldEnter implements ModEventHandler<PlayerEnteredWorld> {

    private final SessionService sessions;
    private final AutomationService automation;

    public AutomationOnWorldEnter(SessionService sessions, AutomationService automation) {
        this.sessions = sessions;
        this.automation = automation;
    }

    @Override
    public Class<PlayerEnteredWorld> eventType() {
        return PlayerEnteredWorld.class;
    }

    @Override
    public void handle(PlayerEnteredWorld event) {
        sessions.enteredWorld(event.player().id(), event.world());
        automation.onWorld(event.player(), event.world());
    }
}
