package net.ofatech.controlcore.domain.ports.game;

import net.ofatech.controlcore.domain.model.PlayerId;

public interface ChatGate {

    interface Listener {
        boolean allow(PlayerId sender, String name, String text);
    }

    interface Observer {
        void sent(PlayerId sender, String name, String text);
    }

    ChatGate NONE = new ChatGate() {
        @Override
        public void listen(Listener listener) {}

        @Override
        public void observe(Observer observer) {}
    };

    void listen(Listener listener);

    void observe(Observer observer);
}
