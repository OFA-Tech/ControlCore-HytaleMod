package net.ofatech.controlcore.application.bans;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class RememberBanNames implements ModEventHandler<PlayerJoined> {

    private final BansService bans;

    public RememberBanNames(BansService bans) {
        this.bans = bans;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        bans.remember(event.player().id(), event.player().name());
    }
}
