package net.ofatech.controlcore.application.component;

import net.ofatech.controlcore.domain.event.DomainEvent;

/** Reacts to one event type. Annotate implementations with {@code @AutoService(ModEventHandler.class)}. */
public interface ModEventHandler<E extends DomainEvent> {

    Class<E> eventType();

    void handle(E event);
}
