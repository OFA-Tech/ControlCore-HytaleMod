package net.ofatech.controlcore.domain.ports.game;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;

public interface PlayerInput {

    void listen(Listener listener);

    void stop();

    interface Listener {

        boolean pressed(PlayerId player, KeySignal signal);

        void held(PlayerId player, Set<KeySignal> held);
    }
}
