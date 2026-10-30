package net.ofatech.controlcore.application.players;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ofatech.controlcore.application.bans.BansService;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class PlayersChanges {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Log log;

    public PlayersChanges(BansService bans, Log log) {
        this.log = log;
        bans.onChange(this::fire);
    }

    public Subscription onChange(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void fire() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failed) {
                log.warn("A Players listener failed and was removed: " + failed);
                listeners.remove(listener);
            }
        }
    }
}
