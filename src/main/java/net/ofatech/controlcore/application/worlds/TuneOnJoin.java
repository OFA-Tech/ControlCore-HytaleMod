package net.ofatech.controlcore.application.worlds;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class TuneOnJoin implements ModEventHandler<PlayerJoined> {

    private final WorldsService worlds;

    public TuneOnJoin(WorldsService worlds) {
        this.worlds = worlds;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        worlds.tuneLater(event.player());
    }
}
