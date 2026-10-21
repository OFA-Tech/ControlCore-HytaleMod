package net.ofatech.controlcore.application.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.ofatech.controlcore.api.ControlCoreEvents;
import net.ofatech.controlcore.domain.event.DomainEvent;
import net.ofatech.controlcore.domain.event.Subscription;
import net.ofatech.controlcore.domain.ports.event.EventPublisher;
import net.ofatech.controlcore.domain.ports.system.Log;

public final class DomainEventBus implements ControlCoreEvents, EventPublisher {

    private final Log log;
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    public DomainEventBus(Log log) {
        this.log = log;
    }

    @Override
    public <E extends DomainEvent> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        Consumer<Object> delivery = event -> listener.accept(type.cast(event));
        List<Consumer<Object>> forType = listeners.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
        forType.add(delivery);
        return () -> forType.remove(delivery);
    }

    @Override
    public <E extends DomainEvent> E publish(E event) {
        listeners.forEach((type, forType) -> {
            if (type.isInstance(event)) {
                for (Consumer<Object> listener : forType) {
                    deliver(listener, event);
                }
            }
        });
        return event;
    }

    public void clear() {
        listeners.clear();
    }

    private void deliver(Consumer<Object> listener, DomainEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            log.error("A listener of " + event.getClass().getSimpleName() + " failed", e);
        }
    }
}
