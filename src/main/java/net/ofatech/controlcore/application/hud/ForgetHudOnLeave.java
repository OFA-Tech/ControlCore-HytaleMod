package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ForgetHudOnLeave implements ModEventHandler<PlayerLeft> {

    private final HudService hud;

    public ForgetHudOnLeave(HudService hud) {
        this.hud = hud;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        hud.forget(event.player().id());
    }
}
