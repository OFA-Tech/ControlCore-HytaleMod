package net.ofatech.controlcore.application.worlds;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerEnteredWorld;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class TuneOnWorldEnter implements ModEventHandler<PlayerEnteredWorld> {

    private final WorldsService worlds;

    public TuneOnWorldEnter(WorldsService worlds) {
        this.worlds = worlds;
    }

    @Override
    public Class<PlayerEnteredWorld> eventType() {
        return PlayerEnteredWorld.class;
    }

    @Override
    public void handle(PlayerEnteredWorld event) {
        worlds.entered(event.player(), event.world());
    }
}
