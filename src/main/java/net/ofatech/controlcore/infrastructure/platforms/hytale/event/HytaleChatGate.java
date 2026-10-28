package net.ofatech.controlcore.infrastructure.platforms.hytale.event;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
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
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean listening = new AtomicBoolean();
    private final AtomicBoolean observing = new AtomicBoolean();

    public HytaleChatGate(EventRegistry registry, Log log) {
        this.registry = registry;
        this.log = log;
    }

    @Override
    public void listen(Listener listener) {
        listeners.add(listener);
        if (listening.compareAndSet(false, true)) {
            registry.<String, PlayerChatEvent>registerAsyncGlobal(
                    EventPriority.EARLY, PlayerChatEvent.class, future -> future.thenApply(this::filter));
        }
    }

    @Override
    public void observe(Observer observer) {
        observers.add(observer);
        if (observing.compareAndSet(false, true)) {
            registry.<String, PlayerChatEvent>registerAsyncGlobal(
                    EventPriority.LAST, PlayerChatEvent.class, future -> future.thenApply(this::sent));
        }
    }

    private PlayerChatEvent filter(PlayerChatEvent event) {
        if (event == null || event.isCancelled() || event.getSender() == null) {
            return event;
        }
        PlayerRef sender = event.getSender();
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

    private PlayerChatEvent sent(PlayerChatEvent event) {
        if (event == null || event.isCancelled() || event.getSender() == null || !toEveryone(event)) {
            return event;
        }
        PlayerRef sender = event.getSender();
        PlayerId id = PlayerId.of(sender.getUuid());
        for (Observer observer : observers) {
            try {
                observer.sent(id, sender.getUsername(), event.getContent());
            } catch (RuntimeException e) {
                log.error("Could not keep a chat message", e);
            }
        }
        return event;
    }

    private static boolean toEveryone(PlayerChatEvent event) {
        List<PlayerRef> targets = event.getTargets();
        return targets == null || targets.size() >= Universe.get().getPlayerCount();
    }
}
