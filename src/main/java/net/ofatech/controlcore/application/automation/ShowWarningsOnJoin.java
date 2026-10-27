package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ShowWarningsOnJoin implements ModEventHandler<PlayerJoined> {

    private final NotificationService notifications;

    public ShowWarningsOnJoin(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        notifications.join(event.player().id());
    }
}
