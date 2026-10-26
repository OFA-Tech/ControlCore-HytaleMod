package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class AutomationOnJoin implements ModEventHandler<PlayerJoined> {

    private final SessionService sessions;
    private final AutomationService automation;

    public AutomationOnJoin(SessionService sessions, AutomationService automation) {
        this.sessions = sessions;
        this.automation = automation;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        boolean first = sessions.joined(event.player());
        automation.onJoin(event.player(), first);
    }
}
