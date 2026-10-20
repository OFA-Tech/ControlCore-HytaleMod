package net.ofatech.controlcore.fakes;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;
import net.ofatech.controlcore.domain.ports.game.PlayerInput;

/** {@link PlayerInput} driven by the test. */
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

    /** @return whether the game action would be cancelled */
    public boolean press(PlayerId player, KeySignal signal) {
        return listener != null && listener.pressed(player, signal);
    }

    public void hold(PlayerId player, KeySignal... held) {
        if (listener != null) {
            listener.held(player, Set.of(held));
        }
    }
}
