package net.ofatech.controlcore.fakes;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;

public final class FakePlayerInput implements PlayerInput {

    public volatile Listener listener;

    @Override
    public void listen(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void stop() {
        listener = null;
    }

    public boolean press(PlayerId player, KeySignal signal) {
        return listener != null && listener.pressed(player, signal);
    }

    public void hold(PlayerId player, KeySignal... held) {
        if (listener != null) {
            listener.held(player, Set.of(held));
        }
    }
}
