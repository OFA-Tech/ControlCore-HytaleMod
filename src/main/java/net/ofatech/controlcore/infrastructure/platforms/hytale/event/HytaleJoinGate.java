package net.ofatech.controlcore.infrastructure.platforms.hytale.event;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.JoinGate;
import net.ofatech.controlcore.domain.ports.system.Log;
import net.ofatech.controlcore.infrastructure.platforms.hytale.mapping.Messages;

public final class HytaleJoinGate implements JoinGate {

    private final EventRegistry registry;
    private final Log log;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean listening = new AtomicBoolean();

    public HytaleJoinGate(EventRegistry registry, Log log) {
        this.registry = registry;
        this.log = log;
    }

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
        if (listening.compareAndSet(false, true)) {
            registry.registerGlobal(PlayerSetupConnectEvent.class, (PlayerSetupConnectEvent event) -> check(event));
        }
    }

    private void check(PlayerSetupConnectEvent event) {
        if (event == null || event.isCancelled() || event.getUuid() == null) {
            return;
        }
        PlayerId id = PlayerId.of(event.getUuid());
        for (Listener listener : listeners) {
            try {
                Optional<String> refused = listener.refuse(id, event.getUsername());
                if (refused.isPresent()) {
                    event.setCancelled(true);
                    event.setReason(Messages.text(refused.get()));
                    return;
                }
            } catch (RuntimeException e) {
                log.error("A join check failed", e);
            }
        }
    }
}
