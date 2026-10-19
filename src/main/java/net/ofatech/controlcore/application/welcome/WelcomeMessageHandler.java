package net.ofatech.controlcore.application.welcome;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerJoined;
import net.ofatech.controlcore.domain.ports.game.Messenger;

@SuppressWarnings("rawtypes") // AutoService cannot name generic interfaces exactly
@AutoService(ModEventHandler.class)
public final class WelcomeMessageHandler implements ModEventHandler<PlayerJoined> {

    private final WelcomeSettings settings;
    private final Messenger messenger;

    public WelcomeMessageHandler(WelcomeSettings settings, Messenger messenger) {
        this.settings = settings;
        this.messenger = messenger;
    }

    @Override
    public Class<PlayerJoined> eventType() {
        return PlayerJoined.class;
    }

    @Override
    public void handle(PlayerJoined event) {
        String text = settings.messageFor(event.player().id(), event.player().name());
        if (!text.isEmpty()) {
            messenger.send(event.player().id(), text);
        }
    }
}
