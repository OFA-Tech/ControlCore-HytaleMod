package net.ofatech.controlcore.domain.ports.game;

import java.util.Optional;
import net.ofatech.controlcore.domain.model.PlayerId;

public interface JoinGate {

    interface Listener {
        Optional<String> refuse(PlayerId player, String name);
    }

    JoinGate NONE = listener -> {};

    void listen(Listener listener);
}
