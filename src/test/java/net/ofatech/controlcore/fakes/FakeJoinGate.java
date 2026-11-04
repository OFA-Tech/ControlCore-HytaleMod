package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.JoinGate;

public final class FakeJoinGate implements JoinGate {

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
    }

    public Optional<String> connect(PlayerId player, String name) {
        for (Listener listener : listeners) {
            Optional<String> refused = listener.refuse(player, name);
            if (refused.isPresent()) {
                return refused;
            }
        }
        return Optional.empty();
    }
}
