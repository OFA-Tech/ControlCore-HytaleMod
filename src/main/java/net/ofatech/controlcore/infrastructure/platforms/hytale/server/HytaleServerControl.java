package net.ofatech.controlcore.infrastructure.platforms.hytale.server;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ofatech.controlcore.domain.ports.game.ServerControl;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Messages;

public final class HytaleServerControl implements ServerControl {

    private static final short BEFORE_DISCONNECT = (short) (ShutdownEvent.DISCONNECT_PLAYERS - 1);

    private final EventRegistry registry;
    private final Log log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean listening = new AtomicBoolean();

    public HytaleServerControl(EventRegistry registry, Log log) {
        this.registry = registry;
        this.log = log;
    }

    @Override
    public void stop(String message) {
        HytaleServer.get().shutdownServer(ShutdownReason.SHUTDOWN.withMessage(Messages.text(message)));
    }

    @Override
    public void onStopping(Runnable listener) {
        listeners.add(listener);
        if (listening.compareAndSet(false, true)) {
            registry.register(BEFORE_DISCONNECT, ShutdownEvent.class, (ShutdownEvent event) -> stopping());
        }
    }

    private void stopping() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.error("A server stopping listener failed", e);
            }
        }
    }
}
