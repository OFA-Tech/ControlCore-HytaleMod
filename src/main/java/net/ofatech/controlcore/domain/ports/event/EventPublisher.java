package net.ofatech.controlcore.domain.ports.event;

import net.ofatech.controlcore.domain.event.DomainEvent;

public interface EventPublisher {

    <E extends DomainEvent> E publish(E event);
}
