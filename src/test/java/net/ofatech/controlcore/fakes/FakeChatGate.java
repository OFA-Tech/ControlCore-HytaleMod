package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.ChatGate;

public final class FakeChatGate implements ChatGate {

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Observer> observers = new CopyOnWriteArrayList<>();

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void observe(Observer observer) {
        observers.add(observer);
    }

    public boolean say(PlayerId sender, String name, String text) {
        return say(sender, name, text, false);
    }

    public boolean say(PlayerId sender, String name, String text, boolean cancelledByAnotherMod) {
        for (Listener listener : listeners) {
            if (!listener.allow(sender, name, text)) {
                return false;
            }
        }
        if (cancelledByAnotherMod) {
            return false;
        }
        observers.forEach(observer -> observer.sent(sender, name, text));
        return true;
    }
}
