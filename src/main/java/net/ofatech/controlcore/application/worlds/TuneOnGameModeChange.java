package net.ofatech.controlcore.application.worlds;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerGameModeChanged;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class TuneOnGameModeChange implements ModEventHandler<PlayerGameModeChanged> {

    private final WorldsService worlds;

    public TuneOnGameModeChange(WorldsService worlds) {
        this.worlds = worlds;
    }

    @Override
    public Class<PlayerGameModeChanged> eventType() {
        return PlayerGameModeChanged.class;
    }

    @Override
    public void handle(PlayerGameModeChanged event) {
        worlds.tuneLater(event.player());
    }
}
