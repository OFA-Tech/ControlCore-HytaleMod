package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.ChatGate;

public final class FakeChatGate implements ChatGate {

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
    }

    public boolean say(PlayerId sender, String name, String text) {
        for (Listener listener : listeners) {
            if (!listener.allow(sender, name, text)) {
                return false;
            }
        }
        return true;
    }
}
