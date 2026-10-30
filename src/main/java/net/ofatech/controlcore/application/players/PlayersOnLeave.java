package net.ofatech.controlcore.application.players;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class PlayersOnLeave implements ModEventHandler<PlayerLeft> {

    private final PlayersService players;

    public PlayersOnLeave(PlayersService players) {
        this.players = players;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        players.left(event.player().id());
    }
}
