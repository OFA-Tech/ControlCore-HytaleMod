package net.ofatech.controlcore.application.automation;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ForgetNotificationsOnLeave implements ModEventHandler<PlayerLeft> {

    private final NotificationService notifications;

    public ForgetNotificationsOnLeave(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        notifications.forget(event.player().id());
    }
}
