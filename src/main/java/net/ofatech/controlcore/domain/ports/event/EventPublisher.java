package net.ofatech.controlcore.domain.ports.event;

import net.ofatech.controlcore.domain.event.DomainEvent;

public interface EventPublisher {

    /** Delivers {@code event} to every listener synchronously and returns it (to read cancellation). */
    <E extends DomainEvent> E publish(E event);
}
