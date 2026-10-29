package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ForgetLeavingPlayer implements ModEventHandler<PlayerLeft> {

    private final KeyDispatcher dispatcher;
    private final KeyDetector detector;

    public ForgetLeavingPlayer(KeyDispatcher dispatcher, KeyDetector detector) {
        this.dispatcher = dispatcher;
        this.detector = detector;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        dispatcher.forget(event.player().id());
        detector.forget(event.player().id());
    }
}
