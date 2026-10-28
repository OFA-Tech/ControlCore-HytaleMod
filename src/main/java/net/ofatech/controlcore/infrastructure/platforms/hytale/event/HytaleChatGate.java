package net.ofatech.controlcore.infrastructure.platforms.hytale.event;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.ofatech.controlcore.domain.model.PlayerId;
import net.ofatech.controlcore.domain.ports.game.ChatGate;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class HytaleChatGate implements ChatGate {

    private final EventRegistry registry;
    private final Log log;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean registered = new AtomicBoolean();

    public HytaleChatGate(EventRegistry registry, Log log) {
        this.registry = registry;
        this.log = log;
    }

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
        if (registered.compareAndSet(false, true)) {
            registry.<String, PlayerChatEvent>registerAsyncGlobal(
                    PlayerChatEvent.class, future -> future.thenApply(this::filter));
        }
    }

    private PlayerChatEvent filter(PlayerChatEvent event) {
        if (event == null || event.isCancelled()) {
            return event;
        }
        PlayerRef sender = event.getSender();
        if (sender == null) {
            return event;
        }
        PlayerId id = PlayerId.of(sender.getUuid());
        for (Listener listener : listeners) {
            try {
                if (!listener.allow(id, sender.getUsername(), event.getContent())) {
                    event.setCancelled(true);
                    return event;
                }
            } catch (RuntimeException e) {
                log.error("A chat check failed", e);
            }
        }
        return event;
    }
}
