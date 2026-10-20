package net.ofatech.controlcore.domain.ports.game;

import java.util.Set;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.model.keys.KeySignal;

/** Reports the {@link KeySignal}s players' clients send while they play. */
public interface PlayerInput {

    /** Starts reporting to {@code listener}, replacing any earlier listener. */
    void listen(Listener listener);

    /** Stops reporting. */
    void stop();

    /** Receives signals on network threads; must return quickly. */
    interface Listener {

        /**
         * A press arrived.
         *
         * @return whether the game's own action for it should be cancelled
         */
        boolean pressed(PlayerId player, KeySignal signal);

        /** The held states changed; {@code held} contains only states that are not presses. */
        void held(PlayerId player, Set<KeySignal> held);
    }
}
