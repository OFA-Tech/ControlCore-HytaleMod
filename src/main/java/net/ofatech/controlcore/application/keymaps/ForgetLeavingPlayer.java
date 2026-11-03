package net.ofatech.controlcore.application.keymaps;

import com.google.auto.service.AutoService;
import net.ofatech.controlcore.application.component.ModEventHandler;
import net.ofatech.controlcore.domain.event.PlayerLeft;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;

@SuppressWarnings("rawtypes")
@AutoService(ModEventHandler.class)
public final class ForgetLeavingPlayer implements ModEventHandler<PlayerLeft> {

    private final KeyDispatcher dispatcher;
    private final KeyDetector detector;
    private final KeyDetectionService detection;
    private final PlayerInput input;

    public ForgetLeavingPlayer(
            KeyDispatcher dispatcher, KeyDetector detector, KeyDetectionService detection, PlayerInput input) {
        this.dispatcher = dispatcher;
        this.detector = detector;
        this.detection = detection;
        this.input = input;
    }

    @Override
    public Class<PlayerLeft> eventType() {
        return PlayerLeft.class;
    }

    @Override
    public void handle(PlayerLeft event) {
        dispatcher.forget(event.player().id());
        detector.forget(event.player().id());
        detection.forget(event.player().id());
        input.forget(event.player().id());
    }
}
