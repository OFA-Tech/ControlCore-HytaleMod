package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface ChatGate {

    interface Listener {
        boolean allow(PlayerId sender, String name, String text);
    }

    ChatGate NONE = listener -> {};

    void listen(Listener listener);
}
