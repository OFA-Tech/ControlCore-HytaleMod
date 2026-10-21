package net.ofatech.controlcore.domain.ports.platform;

import net.ofatech.controlcore.domain.ports.event.EventPublisher;

public interface Platform {

    String name();

    void bind(PortBinder binder);

    void connect(EventPublisher publisher);
}
