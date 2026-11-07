package net.ofatech.controlcore.fakes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.domain.ports.game.ServerControl;

public final class FakeServerControl implements ServerControl {

    public final List<String> stops = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    public volatile boolean failStop;

    @Override
    public void stop(String message) {
        if (failStop) {
            throw new IllegalStateException("cannot stop");
        }
        stops.add(message);
        shutdown();
    }

    @Override
    public void onStopping(Runnable listener) {
        listeners.add(listener);
    }

    public void shutdown() {
        listeners.forEach(Runnable::run);
    }
}
