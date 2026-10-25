package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class AutomationOnLeave implements ModEventHandler<PlayerLeft> {

    private final SessionService sessions;
    private final AutomationService automation;

    public AutomationOnLeave(SessionService sessions, AutomationService automation) {
        this.sessions = sessions;
        this.automation = automation;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        java.util.Optional<String> world = sessions.world(event.player().id());
        sessions.left(event.player());
        automation.onLeave(event.player(), world);
    }
}
