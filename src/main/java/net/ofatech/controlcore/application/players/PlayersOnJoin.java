package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class PlayersOnJoin implements ModEventHandler<PlayerJoined> {

    private final PlayersService players;

    public PlayersOnJoin(PlayersService players) {
        this.players = players;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        players.joined(event.player().id());
    }
}
