package net.ofatech.controlcore.application.hud;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ApplyHudOnJoin implements ModEventHandler<PlayerJoined> {

    private final HudService hud;

    public ApplyHudOnJoin(HudService hud) {
        this.hud = hud;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        hud.apply(event.player().id());
    }
}
