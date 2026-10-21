package net.ofatech.controlcore.application.component;

import net.ofatech.controlcore.domain.event.DomainEvent;

public interface ModEventHandler<E extends DomainEvent> {

    Class<E> eventType();

    void handle(E event);
}
